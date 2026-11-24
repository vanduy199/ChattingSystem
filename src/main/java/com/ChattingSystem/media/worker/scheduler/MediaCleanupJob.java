package com.ChattingSystem.media.worker.scheduler;

import com.ChattingSystem.media.repository.MediaAssetCleanupDao;
import com.ChattingSystem.media.repository.dto.DeletedPurgeTarget;
import com.ChattingSystem.media.repository.dto.UploadingCleanupTarget;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.shared.properties.MediaGcProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Bộ lập lịch định kỳ dọn dẹp rác media (Garbage Collection & Lifecycle) theo 6 quy tắc:
 * 1. CREATING timeout > 10m -> FAILED (CREATE_TIMEOUT)
 * 2. UPLOADING quá hạn > 24h -> dọn storage ngoài Tx -> FAILED (EXPIRED)
 * 3. COMPLETING timeout > 15m -> UPLOADING (để client có thể hoàn tất lại)
 * 4. FAILED / BLOCKED > 1 ngày -> DELETED (chuẩn bị xóa vật lý)
 * 5. Tệp mồ côi (PROCESSING/AVAILABLE không có tin nhắn sống) > 7 ngày -> DELETED
 * 6. DELETED quá thời gian ân hạn purgeGrace (24h) -> xóa sạch storage ngoài Tx -> PURGED
 */
@Component
@ConditionalOnProperty(name = "media.gc.enabled", havingValue = "true", matchIfMissing = true)
public class MediaCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);

    private final MediaAssetCleanupDao cleanupDao;
    private final ObjectStorage objectStorage;
    private final MediaGcProperties properties;
    private final TransactionTemplate txTemplate;

    public MediaCleanupJob(MediaAssetCleanupDao cleanupDao,
                           ObjectStorage objectStorage,
                           MediaGcProperties properties,
                           TransactionTemplate txTemplate) {
        this.cleanupDao = cleanupDao;
        this.objectStorage = objectStorage;
        this.properties = properties;
        this.txTemplate = txTemplate;
    }

    @Scheduled(cron = "${media.gc.cron:0 0 * * * *}")
    public void runCleanup() {
        log.info("Bắt đầu tiến trình dọn dẹp rác media (MediaCleanupJob)...");
        Instant now = Instant.now();

        // 1. Nhóm quy tắc DB-only (Quy tắc 1, 3, 4, 5) dùng Postgres advisory transaction lock
        try {
            runDbOnlyCleanup(now);
        } catch (Exception e) {
            log.error("Lỗi khi thực thi nhóm quy tắc dọn dẹp DB-only: {}", e.getMessage(), e);
        }

        // 2. Quy tắc 2: Dọn dẹp UPLOADING quá hạn trên Storage
        try {
            cleanupExpiredUploading(now);
        } catch (Exception e) {
            log.error("Lỗi khi thực thi Quy tắc 2 (dọn dẹp UPLOADING quá hạn): {}", e.getMessage(), e);
        }

        // 3. Quy tắc 6: Xóa vật lý các tệp DELETED quá thời gian ân hạn (PURGE)
        try {
            purgeDeletedAssets(now);
        } catch (Exception e) {
            log.error("Lỗi khi thực thi Quy tắc 6 (purge vật lý Storage): {}", e.getMessage(), e);
        }

        log.info("Hoàn tất tiến trình dọn dẹp rác media.");
    }

    void runDbOnlyCleanup(Instant now) {
        Boolean lockAcquired = txTemplate.execute(status -> {
            boolean acquired = cleanupDao.tryAcquireDbCleanupLock();
            if (!acquired) {
                log.info("Không thể lấy DB cleanup advisory lock (instance khác đang chạy). Bỏ qua cụm DB-only.");
                return false;
            }

            int batchSize = properties.batchSize();
            int maxBatches = properties.maxBatchesPerRun();

            int totalCreating = 0;
            int totalCompleting = 0;
            int totalFailedBlocked = 0;
            int totalOrphan = 0;

            // Quy tắc 1: CREATING timeout > creatingTimeout
            Instant creatingCutoff = now.minus(properties.creatingTimeout());
            for (int batch = 0; batch < maxBatches; batch++) {
                int updated = cleanupDao.cleanupCreatingTimeout(creatingCutoff, now, batchSize);
                totalCreating += updated;
                if (updated < batchSize) {
                    break;
                }
            }

            // Quy tắc 3: COMPLETING timeout > completingTimeout (dùng updated_at)
            Instant completingCutoff = now.minus(properties.completingTimeout());
            for (int batch = 0; batch < maxBatches; batch++) {
                int updated = cleanupDao.revertCompletingTimeout(completingCutoff, now, batchSize);
                totalCompleting += updated;
                if (updated < batchSize) {
                    break;
                }
            }

            // Quy tắc 4: FAILED hoặc BLOCKED > failedTtl
            Instant failedCutoff = now.minus(properties.failedTtl());
            for (int batch = 0; batch < maxBatches; batch++) {
                int updated = cleanupDao.cleanupFailedAndBlocked(failedCutoff, now, batchSize);
                totalFailedBlocked += updated;
                if (updated < batchSize) {
                    break;
                }
            }

            // Quy tắc 5: Tệp mồ côi (PROCESSING hoặc AVAILABLE) > orphanTtl
            Instant orphanCutoff = now.minus(properties.orphanTtl());
            for (int batch = 0; batch < maxBatches; batch++) {
                int updated = cleanupDao.cleanupOrphanAssets(orphanCutoff, now, batchSize);
                totalOrphan += updated;
                if (updated < batchSize) {
                    break;
                }
            }

            log.info("Đã hoàn tất dọn dẹp DB-only: creatingTimeout={}, completingTimeout={}, failedAndBlocked={}, orphanAssets={}",
                    totalCreating, totalCompleting, totalFailedBlocked, totalOrphan);
            return true;
        });

        if (Boolean.TRUE.equals(lockAcquired)) {
            log.debug("Đã hoàn tất dọn dẹp các quy tắc DB-only.");
        }
    }

    void cleanupExpiredUploading(Instant now) {
        int batchSize = properties.batchSize();
        int maxBatches = properties.maxBatchesPerRun();

        int totalSucceeded = 0;
        int totalFailedS3 = 0;

        for (int batch = 0; batch < maxBatches; batch++) {
            Instant batchNow = Instant.now();
            Instant cutoff = batchNow.minus(properties.uploadingTtl());
            Instant lockedUntil = batchNow.plus(properties.storageClaimLease());

            // Tx1 ngắn: Claim batch với FOR UPDATE SKIP LOCKED
            List<UploadingCleanupTarget> targets = txTemplate.execute(status ->
                    cleanupDao.claimExpiredUploading(cutoff, batchNow, lockedUntil, batchSize)
            );

            if (targets == null || targets.isEmpty()) {
                break;
            }

            List<Long> succeededIds = new ArrayList<>();
            List<Long> failedIds = new ArrayList<>();

            // Ngoài Transaction: gọi Storage delete / abort
            for (UploadingCleanupTarget target : targets) {
                try {
                    if (target.uploadId() != null && target.rawKey() != null) {
                        objectStorage.abortMultipartUpload(target.rawKey(), target.uploadId());
                    }
                    if (target.rawKey() != null) {
                        objectStorage.delete(target.rawKey());
                    }
                    succeededIds.add(target.id());
                } catch (Exception e) {
                    log.warn("Lỗi dọn dẹp storage cho UPLOADING quá hạn [id={}]: {}", target.id(), e.getMessage());
                    failedIds.add(target.id());
                }
            }

            totalSucceeded += succeededIds.size();
            totalFailedS3 += failedIds.size();

            // Tx2 ngắn: Cập nhật kết quả thành công vào DB
            // Lưu ý: KHÔNG giải phóng locked_until cho failedIds để giữ lease (10m), tránh đợt tiếp theo lặp lại
            if (!succeededIds.isEmpty()) {
                Instant writeNow = Instant.now();
                txTemplate.executeWithoutResult(status ->
                        cleanupDao.markUploadingExpiredFailed(succeededIds, writeNow)
                );
            }

            // Chống lặp vô hạn nếu toàn bộ lô thất bại do lỗi mạng Storage, hoặc đã hết bản ghi
            if (succeededIds.isEmpty() || targets.size() < batchSize) {
                break;
            }
        }

        log.info("Hoàn tất Quy tắc 2 (UPLOADING quá hạn): thành công={}, lỗi S3 (giữ lease)={}",
                totalSucceeded, totalFailedS3);
    }

    void purgeDeletedAssets(Instant now) {
        int batchSize = properties.batchSize();
        int maxBatches = properties.maxBatchesPerRun();

        int totalSucceeded = 0;
        int totalFailedS3 = 0;

        for (int batch = 0; batch < maxBatches; batch++) {
            Instant batchNow = Instant.now();
            Instant cutoff = batchNow.minus(properties.purgeGrace());
            Instant lockedUntil = batchNow.plus(properties.storageClaimLease());

            // Tx1 ngắn: Claim batch với FOR UPDATE SKIP LOCKED
            List<DeletedPurgeTarget> targets = txTemplate.execute(status ->
                    cleanupDao.claimPurgeTargets(cutoff, batchNow, lockedUntil, batchSize)
            );

            if (targets == null || targets.isEmpty()) {
                break;
            }

            List<Long> succeededIds = new ArrayList<>();
            List<Long> failedIds = new ArrayList<>();

            // Ngoài Transaction: xóa toàn bộ object liên quan trên Storage
            for (DeletedPurgeTarget target : targets) {
                try {
                    if (target.uploadId() != null && target.rawKey() != null) {
                        objectStorage.abortMultipartUpload(target.rawKey(), target.uploadId());
                    }
                    if (target.rawKey() != null) {
                        objectStorage.delete(target.rawKey());
                    }
                    if (target.displayKey() != null) {
                        objectStorage.delete(target.displayKey());
                    }
                    if (target.thumbKey() != null) {
                        objectStorage.delete(target.thumbKey());
                    }
                    succeededIds.add(target.id());
                } catch (Exception e) {
                    log.warn("Lỗi xóa storage khi purge [id={}]: {}", target.id(), e.getMessage());
                    failedIds.add(target.id());
                }
            }

            totalSucceeded += succeededIds.size();
            totalFailedS3 += failedIds.size();

            // Tx2 ngắn: Cập nhật kết quả thành công vào DB
            // Lưu ý: KHÔNG giải phóng locked_until cho failedIds để giữ lease (10m), tránh đợt tiếp theo lặp lại
            if (!succeededIds.isEmpty()) {
                Instant writeNow = Instant.now();
                txTemplate.executeWithoutResult(status ->
                        cleanupDao.markDeletedPurged(succeededIds, writeNow)
                );
            }

            // Chống lặp vô hạn nếu toàn bộ lô thất bại do lỗi mạng Storage, hoặc đã hết bản ghi
            if (succeededIds.isEmpty() || targets.size() < batchSize) {
                break;
            }
        }

        log.info("Hoàn tất Quy tắc 6 (Purge DELETED): thành công={}, lỗi S3 (giữ lease)={}",
                totalSucceeded, totalFailedS3);
    }
}
