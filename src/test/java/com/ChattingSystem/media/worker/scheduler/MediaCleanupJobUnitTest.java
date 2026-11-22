package com.ChattingSystem.media.worker.scheduler;

import com.ChattingSystem.media.repository.MediaAssetCleanupDao;
import com.ChattingSystem.media.repository.dto.DeletedPurgeTarget;
import com.ChattingSystem.media.repository.dto.UploadingCleanupTarget;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.shared.properties.MediaGcProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Kiểm thử MediaCleanupJob (Bộ lập lịch dọn rác 6 quy tắc)")
class MediaCleanupJobUnitTest {

    private MediaAssetCleanupDao cleanupDao;
    private ObjectStorage objectStorage;
    private TransactionTemplate txTemplate;
    private MediaGcProperties properties;
    private MediaCleanupJob cleanupJob;

    @BeforeEach
    void setUp() {
        cleanupDao = mock(MediaAssetCleanupDao.class);
        objectStorage = mock(ObjectStorage.class);
        txTemplate = mock(TransactionTemplate.class);

        // TransactionTemplate chạy trực tiếp callback
        when(txTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });

        doAnswer(invocation -> {
            java.util.function.Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(txTemplate).executeWithoutResult(any());

        // Cấu hình properties mẫu với batchSize = 10, maxBatchesPerRun = 2
        properties = new MediaGcProperties(
                true,
                "0 0 * * * *",
                10,
                2,
                Duration.ofDays(7),
                Duration.ofHours(24),
                Duration.ofMinutes(10),
                Duration.ofMinutes(15),
                Duration.ofDays(1),
                Duration.ofHours(24),
                Duration.ofMinutes(10)
        );

        cleanupJob = new MediaCleanupJob(cleanupDao, objectStorage, properties, txTemplate);
    }

    @Test
    @DisplayName("runCleanup: Chạy thành công toàn bộ 3 nhóm quy tắc khi DB lock lấy được")
    void runCleanup_happyPath_runsAllThreePhases() {
        when(cleanupDao.tryAcquireDbCleanupLock()).thenReturn(true);
        when(cleanupDao.cleanupCreatingTimeout(any(), any(), anyInt())).thenReturn(0);
        when(cleanupDao.revertCompletingTimeout(any(), any(), anyInt())).thenReturn(0);
        when(cleanupDao.cleanupFailedAndBlocked(any(), any(), anyInt())).thenReturn(0);
        when(cleanupDao.cleanupOrphanAssets(any(), any(), anyInt())).thenReturn(0);

        // Quy tắc 2: claim 1 target
        UploadingCleanupTarget uploadingTarget = new UploadingCleanupTarget(101L, "raw/uploading.mp4", "upload-123");
        when(cleanupDao.claimExpiredUploading(any(), any(), any(), eq(10)))
                .thenReturn(List.of(uploadingTarget))
                .thenReturn(List.of());

        // Quy tắc 6: claim 1 target
        DeletedPurgeTarget purgeTarget = new DeletedPurgeTarget(201L, "raw/del.mp4", "display/del.mp4", "thumb/del.webp", "upload-456");
        when(cleanupDao.claimPurgeTargets(any(), any(), any(), eq(10)))
                .thenReturn(List.of(purgeTarget))
                .thenReturn(List.of());

        cleanupJob.runCleanup();

        // 1. Nhóm DB-only
        verify(cleanupDao).cleanupCreatingTimeout(any(), any(), eq(10));
        verify(cleanupDao).revertCompletingTimeout(any(), any(), eq(10));
        verify(cleanupDao).cleanupFailedAndBlocked(any(), any(), eq(10));
        verify(cleanupDao).cleanupOrphanAssets(any(), any(), eq(10));

        // 2. Quy tắc 2 (Storage abort & delete)
        verify(objectStorage).abortMultipartUpload("raw/uploading.mp4", "upload-123");
        verify(objectStorage).delete("raw/uploading.mp4");
        verify(cleanupDao).markUploadingExpiredFailed(eq(List.of(101L)), any(Instant.class));

        // 3. Quy tắc 6 (Storage purge sạch các keys)
        verify(objectStorage).abortMultipartUpload("raw/del.mp4", "upload-456");
        verify(objectStorage).delete("raw/del.mp4");
        verify(objectStorage).delete("display/del.mp4");
        verify(objectStorage).delete("thumb/del.webp");
        verify(cleanupDao).markDeletedPurged(eq(List.of(201L)), any(Instant.class));

        // Không bao giờ giải phóng lock khi thành công
        verify(cleanupDao, never()).releaseCleanupLocks(anyCollection(), any());
    }

    @Test
    @DisplayName("runDbOnlyCleanup: Không lấy được advisory lock -> bỏ qua toàn bộ 4 quy tắc DB-only")
    void runDbOnlyCleanup_lockNotAcquired_skipsDbRules() {
        when(cleanupDao.tryAcquireDbCleanupLock()).thenReturn(false);

        cleanupJob.runDbOnlyCleanup(Instant.now());

        verify(cleanupDao, never()).cleanupCreatingTimeout(any(), any(), anyInt());
        verify(cleanupDao, never()).revertCompletingTimeout(any(), any(), anyInt());
        verify(cleanupDao, never()).cleanupFailedAndBlocked(any(), any(), anyInt());
        verify(cleanupDao, never()).cleanupOrphanAssets(any(), any(), anyInt());
    }

    @Test
    @DisplayName("runDbOnlyCleanup: Dừng vòng lặp sớm khi số dòng cập nhật < batchSize")
    void runDbOnlyCleanup_batchLoop_stopsWhenUpdatedLessThanBatchSize() {
        when(cleanupDao.tryAcquireDbCleanupLock()).thenReturn(true);
        // Batch đầu tiên trả về 3 dòng (< batchSize 10) -> dừng luôn, không chạy batch 2
        when(cleanupDao.cleanupCreatingTimeout(any(), any(), eq(10))).thenReturn(3);
        when(cleanupDao.revertCompletingTimeout(any(), any(), eq(10))).thenReturn(0);
        when(cleanupDao.cleanupFailedAndBlocked(any(), any(), eq(10))).thenReturn(0);
        when(cleanupDao.cleanupOrphanAssets(any(), any(), eq(10))).thenReturn(0);

        cleanupJob.runDbOnlyCleanup(Instant.now());

        verify(cleanupDao, times(1)).cleanupCreatingTimeout(any(), any(), eq(10));
    }

    @Test
    @DisplayName("runDbOnlyCleanup: Dừng vòng lặp khi đạt maxBatchesPerRun")
    void runDbOnlyCleanup_batchLoop_stopsAtMaxBatches() {
        when(cleanupDao.tryAcquireDbCleanupLock()).thenReturn(true);
        // Mỗi batch đều trả về 10 dòng (= batchSize) -> chạy tối đa 2 batches (maxBatchesPerRun = 2) rồi dừng
        when(cleanupDao.cleanupCreatingTimeout(any(), any(), eq(10))).thenReturn(10);
        when(cleanupDao.revertCompletingTimeout(any(), any(), eq(10))).thenReturn(0);
        when(cleanupDao.cleanupFailedAndBlocked(any(), any(), eq(10))).thenReturn(0);
        when(cleanupDao.cleanupOrphanAssets(any(), any(), eq(10))).thenReturn(0);

        cleanupJob.runDbOnlyCleanup(Instant.now());

        verify(cleanupDao, times(2)).cleanupCreatingTimeout(any(), any(), eq(10));
    }

    @Test
    @DisplayName("cleanupExpiredUploading: Storage lỗi ở 1 target -> giữ lease cho target lỗi, đánh FAILED target thành công")
    void cleanupExpiredUploading_storageError_retainsLockForFailed_marksSuccessForRest() {
        UploadingCleanupTarget targetSuccess = new UploadingCleanupTarget(101L, "raw/ok.mp4", null);
        UploadingCleanupTarget targetFail = new UploadingCleanupTarget(102L, "raw/fail.mp4", null);

        when(cleanupDao.claimExpiredUploading(any(), any(), any(), eq(10)))
                .thenReturn(List.of(targetSuccess, targetFail))
                .thenReturn(List.of());

        // targetSuccess thành công, targetFail ném ngoại lệ
        doNothing().when(objectStorage).delete("raw/ok.mp4");
        doThrow(new RuntimeException("S3 network error")).when(objectStorage).delete("raw/fail.mp4");

        cleanupJob.cleanupExpiredUploading(Instant.now());

        // Target thành công: chuyển sang FAILED
        verify(cleanupDao).markUploadingExpiredFailed(eq(List.of(101L)), any(Instant.class));
        // Target thất bại: giữ nguyên locked_until (lease 10 phút), KHÔNG gọi releaseCleanupLocks và KHÔNG đánh FAILED
        verify(cleanupDao, never()).releaseCleanupLocks(anyCollection(), any());
    }

    @Test
    @DisplayName("cleanupExpiredUploading: Toàn bộ lô Storage lỗi -> dừng vòng lặp tránh retry vô hạn trong cùng một lần chạy")
    void cleanupExpiredUploading_allStorageError_breaksLoopToAvoidInfiniteRetry() {
        UploadingCleanupTarget targetFail = new UploadingCleanupTarget(102L, "raw/fail.mp4", null);

        // Giả sử có bản ghi nhưng thất bại
        when(cleanupDao.claimExpiredUploading(any(), any(), any(), eq(10)))
                .thenReturn(List.of(targetFail));

        doThrow(new RuntimeException("S3 503 Service Unavailable")).when(objectStorage).delete("raw/fail.mp4");

        cleanupJob.cleanupExpiredUploading(Instant.now());

        // Chỉ claim 1 lần duy nhất rồi dừng do succeededIds.isEmpty()
        verify(cleanupDao, times(1)).claimExpiredUploading(any(), any(), any(), eq(10));
        verify(cleanupDao, never()).markUploadingExpiredFailed(anyCollection(), any());
        verify(cleanupDao, never()).releaseCleanupLocks(anyCollection(), any());
    }

    @Test
    @DisplayName("purgeDeletedAssets: Storage lỗi ở 1 target -> giữ lease cho target lỗi, đánh PURGED target thành công")
    void purgeDeletedAssets_storageError_retainsLockForFailed_marksPurgedForRest() {
        DeletedPurgeTarget targetSuccess = new DeletedPurgeTarget(201L, "raw/ok.mp4", "display/ok.mp4", "thumb/ok.webp", null);
        DeletedPurgeTarget targetFail = new DeletedPurgeTarget(202L, "raw/fail.mp4", "display/fail.mp4", "thumb/fail.webp", null);

        when(cleanupDao.claimPurgeTargets(any(), any(), any(), eq(10)))
                .thenReturn(List.of(targetSuccess, targetFail))
                .thenReturn(List.of());

        doNothing().when(objectStorage).delete("raw/ok.mp4");
        doNothing().when(objectStorage).delete("display/ok.mp4");
        doNothing().when(objectStorage).delete("thumb/ok.webp");

        // Fail khi xóa display
        doNothing().when(objectStorage).delete("raw/fail.mp4");
        doThrow(new RuntimeException("S3 403 Forbidden")).when(objectStorage).delete("display/fail.mp4");

        cleanupJob.purgeDeletedAssets(Instant.now());

        // Target thành công: chuyển sang PURGED
        verify(cleanupDao).markDeletedPurged(eq(List.of(201L)), any(Instant.class));
        // Target thất bại: giữ nguyên locked_until (lease 10 phút), KHÔNG gọi releaseCleanupLocks và KHÔNG đánh PURGED
        verify(cleanupDao, never()).releaseCleanupLocks(anyCollection(), any());
    }

    @Test
    @DisplayName("purgeDeletedAssets: Toàn bộ lô Storage lỗi -> dừng vòng lặp tránh retry vô hạn trong cùng một lần chạy")
    void purgeDeletedAssets_allStorageError_breaksLoopToAvoidInfiniteRetry() {
        DeletedPurgeTarget targetFail = new DeletedPurgeTarget(202L, "raw/fail.mp4", null, null, null);

        when(cleanupDao.claimPurgeTargets(any(), any(), any(), eq(10)))
                .thenReturn(List.of(targetFail));

        doThrow(new RuntimeException("S3 connection reset")).when(objectStorage).delete("raw/fail.mp4");

        cleanupJob.purgeDeletedAssets(Instant.now());

        verify(cleanupDao, times(1)).claimPurgeTargets(any(), any(), any(), eq(10));
        verify(cleanupDao, never()).markDeletedPurged(anyCollection(), any());
        verify(cleanupDao, never()).releaseCleanupLocks(anyCollection(), any());
    }

    @Test
    @DisplayName("runCleanup: Lỗi ở nhóm DB-only không làm dừng Quy tắc 2 và Quy tắc 6")
    void runCleanup_exceptionInOnePhase_doesNotAbortOtherPhases() {
        // Gây lỗi ở runDbOnlyCleanup
        when(cleanupDao.tryAcquireDbCleanupLock()).thenThrow(new RuntimeException("PostgreSQL Connection Terminated"));

        when(cleanupDao.claimExpiredUploading(any(), any(), any(), eq(10))).thenReturn(List.of());
        when(cleanupDao.claimPurgeTargets(any(), any(), any(), eq(10))).thenReturn(List.of());

        // Không ném exception ra ngoài scheduler
        cleanupJob.runCleanup();

        // Vẫn tiếp tục thực thi Quy tắc 2 và Quy tắc 6
        verify(cleanupDao).claimExpiredUploading(any(), any(), any(), eq(10));
        verify(cleanupDao).claimPurgeTargets(any(), any(), any(), eq(10));
    }

    @Test
    @DisplayName("runCleanup: Chạy 2 lần liên tiếp idempotent và an toàn khi lần 2 không còn bản ghi nào")
    void runCleanup_twoConsecutiveRuns_idempotentAndSafe() {
        when(cleanupDao.tryAcquireDbCleanupLock()).thenReturn(true);
        when(cleanupDao.cleanupCreatingTimeout(any(), any(), anyInt())).thenReturn(0);
        when(cleanupDao.revertCompletingTimeout(any(), any(), anyInt())).thenReturn(0);
        when(cleanupDao.cleanupFailedAndBlocked(any(), any(), anyInt())).thenReturn(0);
        when(cleanupDao.cleanupOrphanAssets(any(), any(), anyInt())).thenReturn(0);

        when(cleanupDao.claimExpiredUploading(any(), any(), any(), eq(10))).thenReturn(List.of());
        when(cleanupDao.claimPurgeTargets(any(), any(), any(), eq(10))).thenReturn(List.of());

        // Lần 1
        cleanupJob.runCleanup();
        // Lần 2
        cleanupJob.runCleanup();

        verify(cleanupDao, times(2)).tryAcquireDbCleanupLock();
        verify(cleanupDao, times(2)).claimExpiredUploading(any(), any(), any(), eq(10));
        verify(cleanupDao, times(2)).claimPurgeTargets(any(), any(), any(), eq(10));
    }

    @Test
    @DisplayName("cleanupExpiredUploading: Trong lô 3 bản ghi, 1 bản ghi lỗi S3 -> 2 bản ghi còn lại vẫn FAILED, bản ghi lỗi S3 giữ lease")
    void cleanupExpiredUploading_oneOfThreeFails_twoSucceed() {
        UploadingCleanupTarget t1 = new UploadingCleanupTarget(301L, "raw/ok1.mp4", null);
        UploadingCleanupTarget t2 = new UploadingCleanupTarget(302L, "raw/fail.mp4", null);
        UploadingCleanupTarget t3 = new UploadingCleanupTarget(303L, "raw/ok2.mp4", null);

        when(cleanupDao.claimExpiredUploading(any(), any(), any(), eq(10)))
                .thenReturn(List.of(t1, t2, t3))
                .thenReturn(List.of());

        doNothing().when(objectStorage).delete("raw/ok1.mp4");
        doThrow(new RuntimeException("S3 500 Internal Error")).when(objectStorage).delete("raw/fail.mp4");
        doNothing().when(objectStorage).delete("raw/ok2.mp4");

        cleanupJob.cleanupExpiredUploading(Instant.now());

        // 2 bản ghi thành công chuyển sang FAILED
        verify(cleanupDao).markUploadingExpiredFailed(eq(List.of(301L, 303L)), any(Instant.class));
        // 1 bản ghi lỗi S3 giữ nguyên lease, releaseCleanupLocks KHÔNG bao giờ được gọi
        verify(cleanupDao, never()).releaseCleanupLocks(anyCollection(), any());
    }

    @Test
    @DisplayName("purgeDeletedAssets: Trong lô 3 bản ghi, 1 bản ghi lỗi S3 -> 2 bản ghi còn lại vẫn PURGED, bản ghi lỗi S3 giữ lease")
    void purgeDeletedAssets_oneOfThreeFails_twoSucceed() {
        DeletedPurgeTarget t1 = new DeletedPurgeTarget(401L, "raw/p1.mp4", "display/p1.mp4", null, null);
        DeletedPurgeTarget t2 = new DeletedPurgeTarget(402L, "raw/p2.mp4", "display/p2.mp4", null, null);
        DeletedPurgeTarget t3 = new DeletedPurgeTarget(403L, "raw/p3.mp4", "display/p3.mp4", null, null);

        when(cleanupDao.claimPurgeTargets(any(), any(), any(), eq(10)))
                .thenReturn(List.of(t1, t2, t3))
                .thenReturn(List.of());

        doNothing().when(objectStorage).delete("raw/p1.mp4");
        doNothing().when(objectStorage).delete("display/p1.mp4");

        doNothing().when(objectStorage).delete("raw/p2.mp4");
        doThrow(new RuntimeException("S3 Storage Error")).when(objectStorage).delete("display/p2.mp4");

        doNothing().when(objectStorage).delete("raw/p3.mp4");
        doNothing().when(objectStorage).delete("display/p3.mp4");

        cleanupJob.purgeDeletedAssets(Instant.now());

        // 2 bản ghi thành công chuyển sang PURGED
        verify(cleanupDao).markDeletedPurged(eq(List.of(401L, 403L)), any(Instant.class));
        // 1 bản ghi lỗi S3 giữ nguyên lease, releaseCleanupLocks KHÔNG bao giờ được gọi
        verify(cleanupDao, never()).releaseCleanupLocks(anyCollection(), any());
    }
}
