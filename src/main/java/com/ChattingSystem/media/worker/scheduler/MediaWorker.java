package com.ChattingSystem.media.worker.scheduler;

import com.ChattingSystem.media.dto.response.MediaStatusEventDto;
import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.repository.MediaAssetClaimDao;
import com.ChattingSystem.media.repository.MediaAssetRepository;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.media.worker.service.MediaProcessor;
import com.ChattingSystem.realtime.event.RoomEvent;
import com.ChattingSystem.realtime.event.RoomEventType;
import com.ChattingSystem.shared.properties.MediaWorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduler định kỳ quét, claim và điều phối các job xử lý media nền qua ThreadPoolTaskExecutor.
 */
@Component
@ConditionalOnProperty(name = "media.worker.enabled", havingValue = "true", matchIfMissing = true)
public class MediaWorker {

    private static final Logger log = LoggerFactory.getLogger(MediaWorker.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final MediaAssetClaimDao claimDao;
    private final MediaAssetRepository mediaAssetRepo;
    private final MediaProcessor mediaProcessor;
    private final ThreadPoolTaskExecutor mediaTaskExecutor;
    private final MediaWorkerProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate txTemplate;
    private final ObjectStorage objectStorage;

    private final AtomicInteger inFlight = new AtomicInteger(0);

    public MediaWorker(MediaAssetClaimDao claimDao,
                       MediaAssetRepository mediaAssetRepo,
                       MediaProcessor mediaProcessor,
                       @Qualifier("mediaTaskExecutor") ThreadPoolTaskExecutor mediaTaskExecutor,
                       MediaWorkerProperties properties,
                       ApplicationEventPublisher events,
                       TransactionTemplate txTemplate,
                       ObjectStorage objectStorage) {
        this.claimDao = claimDao;
        this.mediaAssetRepo = mediaAssetRepo;
        this.mediaProcessor = mediaProcessor;
        this.mediaTaskExecutor = mediaTaskExecutor;
        this.properties = properties;
        this.events = events;
        this.txTemplate = txTemplate;
        this.objectStorage = objectStorage;
    }

    public int getInFlightCount() {
        return inFlight.get();
    }

    @Scheduled(fixedDelayString = "${media.worker.poll-interval:PT2S}")
    public void pollAndProcess() {
        int concurrency = properties.concurrency();
        int currentInFlight = inFlight.get();
        int availableSlots = concurrency - currentInFlight;
        if (availableSlots <= 0) {
            log.debug("No available slots for media worker (inFlight={}, concurrency={})", currentInFlight, concurrency);
            return;
        }

        Instant now = Instant.now();

        // 1. Quét và xử lý các job poison-pill trước khi claim
        cleanPoisonPillJobs(now);

        // 2. Claim tối đa availableSlots jobs (FOR UPDATE SKIP LOCKED)
        Instant lockedUntil = now.plus(properties.jobTimeout()).plus(Duration.ofMinutes(2));
        List<Long> claimedIds = claimDao.claimJobs(availableSlots, now, lockedUntil, properties.maxAttempts());
        if (claimedIds.isEmpty()) {
            return;
        }

        log.info("Claimed {} media processing jobs: {}", claimedIds.size(), claimedIds);

        // 3. Dispatch từng job vào mediaTaskExecutor
        for (Long mediaAssetId : claimedIds) {
            inFlight.incrementAndGet();
            try {
                mediaTaskExecutor.execute(() -> {
                    try {
                        mediaProcessor.process(mediaAssetId);
                    } catch (Exception e) {
                        handleProcessFailure(mediaAssetId, e);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });
            } catch (RejectedExecutionException e) {
                // Thread pool đã bão hòa (queueCapacity = 0, AbortPolicy)
                inFlight.decrementAndGet();
                log.warn("Task executor rejected job {}, releasing claim", mediaAssetId);
                try {
                    txTemplate.executeWithoutResult(status -> {
                        mediaAssetRepo.releaseClaimOnReject(mediaAssetId, Instant.now());
                    });
                } catch (Exception ex) {
                    log.warn("Failed to release claim on reject for asset {}: {}", mediaAssetId, ex.getMessage());
                }
            }
        }
    }

    void handleProcessFailure(Long mediaAssetId, Exception e) {
        log.error("Failed to process media asset {}: {}", mediaAssetId, e.getMessage(), e);

        Optional<MediaAsset> assetOpt = mediaAssetRepo.findById(mediaAssetId);
        if (assetOpt.isEmpty()) {
            return;
        }
        MediaAsset asset = assetOpt.get();
        if (asset.getStatus() != MediaStatus.PROCESSING) {
            return;
        }

        Instant now = Instant.now();
        int attempts = asset.getAttempts();
        String errorMsg = truncateError(e.getMessage());

        if (attempts >= properties.maxAttempts()) {
            markJobFailed(asset, "EXCEEDED_MAX_ATTEMPTS: " + errorMsg, now);
        } else {
            Instant nextRunAt = calculateNextRunAt(attempts, now);
            log.info("Scheduling retry #{} for asset {} at {}", attempts + 1, mediaAssetId, nextRunAt);
            txTemplate.executeWithoutResult(status -> {
                mediaAssetRepo.scheduleRetry(mediaAssetId, nextRunAt, errorMsg, now);
            });
        }
    }

    void markJobFailed(MediaAsset asset, String reason, Instant now) {
        log.warn("Marking media asset {} as FAILED: reason={}", asset.getId(), reason);
        final String truncatedReason = truncateError(reason);

        Boolean marked = txTemplate.execute(status -> {
            int updated = mediaAssetRepo.markProcessingFailed(asset.getId(), truncatedReason, now);
            if (updated == 1) {
                Long latestMessageId = mediaAssetRepo.findById(asset.getId())
                        .map(MediaAsset::getMessageId)
                        .orElse(asset.getMessageId());

                MediaStatusEventDto eventDto = MediaStatusEventDto.failed(
                        asset.getId(),
                        asset.getRoomId(),
                        latestMessageId,
                        asset.getKind(),
                        "MEDIA_FAILED"
                );
                events.publishEvent(new RoomEvent(asset.getRoomId(), RoomEventType.MEDIA_STATUS, eventDto));
                return true;
            }
            return false;
        });

        // Chỉ dọn dẹp rawKey ngoài transaction khi cập nhật FAILED thành công
        if (Boolean.TRUE.equals(marked) && asset.getRawKey() != null) {
            try {
                objectStorage.delete(asset.getRawKey());
            } catch (Exception ex) {
                log.warn("Failed to delete rawKey {} for failed asset {}: {}", asset.getRawKey(), asset.getId(), ex.getMessage());
            }
        }
    }

    void cleanPoisonPillJobs(Instant now) {
        try {
            List<MediaAsset> poisonPills = mediaAssetRepo.findPoisonPillJobs(properties.maxAttempts(), now);
            for (MediaAsset asset : poisonPills) {
                log.warn("Poison-pill detected for asset {} (attempts={}), marking FAILED",
                        asset.getId(), asset.getAttempts());
                markJobFailed(asset, "EXCEEDED_MAX_ATTEMPTS", now);
            }
        } catch (Exception e) {
            log.warn("Error cleaning poison-pill jobs: {}", e.getMessage(), e);
        }
    }

    Instant calculateNextRunAt(int attempts, Instant now) {
        long delaySeconds = switch (attempts) {
            case 1 -> 60L;       // Lần 1: retry sau 1 phút
            case 2 -> 300L;      // Lần 2: retry sau 5 phút
            case 3 -> 900L;      // Lần 3: retry sau 15 phút
            default -> 1800L;    // Còn lại: retry sau 30 phút
        };
        return now.plusSeconds(delaySeconds);
    }

    private String truncateError(String msg) {
        if (msg == null) {
            return "UNKNOWN_ERROR";
        }
        return msg.length() > MAX_ERROR_LENGTH ? msg.substring(0, MAX_ERROR_LENGTH) : msg;
    }
}
