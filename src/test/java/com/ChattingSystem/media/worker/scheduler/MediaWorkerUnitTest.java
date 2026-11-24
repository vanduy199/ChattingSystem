package com.ChattingSystem.media.worker.scheduler;

import com.ChattingSystem.media.dto.response.MediaStatusEventDto;
import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaQuality;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.repository.MediaAssetClaimDao;
import com.ChattingSystem.media.repository.MediaAssetRepository;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.media.worker.service.MediaProcessor;
import com.ChattingSystem.realtime.event.RoomEvent;
import com.ChattingSystem.realtime.event.RoomEventType;
import com.ChattingSystem.shared.properties.MediaWorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaWorkerUnitTest {

    private MediaAssetClaimDao claimDao;
    private MediaAssetRepository mediaAssetRepo;
    private MediaProcessor mediaProcessor;
    private ThreadPoolTaskExecutor mediaTaskExecutor;
    private MediaWorkerProperties properties;
    private ApplicationEventPublisher events;
    private TransactionTemplate txTemplate;
    private ObjectStorage objectStorage;

    private MediaWorker worker;

    @BeforeEach
    void setUp() {
        claimDao = mock(MediaAssetClaimDao.class);
        mediaAssetRepo = mock(MediaAssetRepository.class);
        mediaProcessor = mock(MediaProcessor.class);
        mediaTaskExecutor = mock(ThreadPoolTaskExecutor.class);
        events = mock(ApplicationEventPublisher.class);
        txTemplate = mock(TransactionTemplate.class);
        objectStorage = mock(ObjectStorage.class);

        // TransactionTemplate chạy trực tiếp callback
        when(txTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });

        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(txTemplate).executeWithoutResult(any());

        properties = new MediaWorkerProperties(
                true,
                1,
                Duration.ofSeconds(2),
                Duration.ofMinutes(10),
                4,
                Path.of(System.getProperty("java.io.tmpdir"), "media-worker-test").toString(),
                256 * 1024 * 1024L,
                50_000_000L,
                300
        );

        worker = new MediaWorker(
                claimDao,
                mediaAssetRepo,
                mediaProcessor,
                mediaTaskExecutor,
                properties,
                events,
                txTemplate,
                objectStorage
        );
    }

    private MediaAsset createAssetWithAttempts(Long id, int attempts, String rawKey) {
        MediaAsset asset = MediaAsset.singleUpload(
                100L,
                1L,
                UUID.randomUUID(),
                MediaKind.IMAGE,
                MediaQuality.STANDARD,
                "image/jpeg",
                1024L,
                rawKey
        );
        asset.setId(id);
        asset.markProcessing("image/jpeg");
        for (int i = 0; i < attempts; i++) {
            // Giả lập số lần attempts
            ReflectionTestUtils.setField(asset, "attempts", attempts);
        }
        return asset;
    }

    @Test
    @DisplayName("pollAndProcess: khi không còn slot trống (inFlight >= concurrency), bỏ qua claim")
    void pollAndProcess_whenNoAvailableSlots_shouldSkipClaiming() {
        AtomicInteger inFlight = (AtomicInteger) ReflectionTestUtils.getField(worker, "inFlight");
        assertThat(inFlight).isNotNull();
        inFlight.set(1); // Concurrency = 1, inFlight = 1 -> availableSlots = 0

        worker.pollAndProcess();

        verify(claimDao, never()).claimJobs(anyInt(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("pollAndProcess: dọn dẹp poison-pill jobs, cập nhật FAILED, phát RoomEvent và xóa rawKey")
    void pollAndProcess_shouldCleanPoisonPillJobs() {
        Long poisonId = 99L;
        String rawKey = "raw/2026/11/21/poison.jpg";
        MediaAsset poisonAsset = createAssetWithAttempts(poisonId, 4, rawKey);

        when(mediaAssetRepo.findPoisonPillJobs(eq(4), any())).thenReturn(List.of(poisonAsset));
        when(mediaAssetRepo.markProcessingFailed(eq(poisonId), eq("EXCEEDED_MAX_ATTEMPTS"), any())).thenReturn(1);
        when(mediaAssetRepo.findById(poisonId)).thenReturn(Optional.of(poisonAsset));

        worker.pollAndProcess();

        verify(mediaAssetRepo).markProcessingFailed(eq(poisonId), eq("EXCEEDED_MAX_ATTEMPTS"), any());

        ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        RoomEvent published = eventCaptor.getValue();
        assertThat(published.type()).isEqualTo(RoomEventType.MEDIA_STATUS);
        MediaStatusEventDto dto = (MediaStatusEventDto) published.data();
        assertThat(dto.status()).isEqualTo(MediaStatus.FAILED);
        assertThat(dto.failureReason()).isEqualTo("MEDIA_FAILED");

        verify(objectStorage).delete(rawKey);
    }

    @Test
    @DisplayName("pollAndProcess: claim và dispatch job thành công vào mediaTaskExecutor")
    void pollAndProcess_normalClaimAndExecution_shouldProcessAndDecrementInFlight() {
        Long assetId = 101L;
        when(claimDao.claimJobs(eq(1), any(), any(), eq(4))).thenReturn(List.of(assetId));

        // mediaTaskExecutor chạy command ngay lập tức
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(mediaTaskExecutor).execute(any(Runnable.class));

        worker.pollAndProcess();

        verify(mediaProcessor).process(assetId);
        assertThat(worker.getInFlightCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("pollAndProcess: khi TaskExecutor từ chối (bão hòa), gọi releaseClaimOnReject và giảm inFlight")
    void pollAndProcess_whenExecutorThrowsRejectedExecutionException_shouldReleaseClaim() {
        Long assetId = 102L;
        when(claimDao.claimJobs(eq(1), any(), any(), eq(4))).thenReturn(List.of(assetId));

        doThrow(new RejectedExecutionException("Pool full")).when(mediaTaskExecutor).execute(any(Runnable.class));

        worker.pollAndProcess();

        verify(txTemplate, times(1)).executeWithoutResult(any());
        verify(mediaAssetRepo).releaseClaimOnReject(eq(assetId), any());
        assertThat(worker.getInFlightCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("handleProcessFailure: attempts < maxAttempts -> lập lịch thử lại lũy tiến (scheduleRetry)")
    void handleProcessFailure_whenAttemptsLessThanMax_shouldScheduleRetryWithBackoff() {
        Long assetId = 103L;
        MediaAsset asset = createAssetWithAttempts(assetId, 1, "raw/retry.jpg");
        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));

        worker.handleProcessFailure(assetId, new RuntimeException("S3 connection timeout"));

        verify(txTemplate, times(1)).executeWithoutResult(any());
        verify(mediaAssetRepo).scheduleRetry(eq(assetId), any(Instant.class), eq("S3 connection timeout"), any(Instant.class));
        verify(mediaAssetRepo, never()).markProcessingFailed(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("handleProcessFailure: attempts >= maxAttempts -> đánh dấu FAILED và bắn MEDIA_STATUS(FAILED)")
    void handleProcessFailure_whenAttemptsReachMax_shouldMarkFailedAndPublishEvent() {
        Long assetId = 104L;
        MediaAsset asset = createAssetWithAttempts(assetId, 4, "raw/fail.jpg");
        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(mediaAssetRepo.markProcessingFailed(eq(assetId), anyString(), any())).thenReturn(1);

        worker.handleProcessFailure(assetId, new RuntimeException("Final attempt failed"));

        verify(mediaAssetRepo).markProcessingFailed(eq(assetId), eq("EXCEEDED_MAX_ATTEMPTS: Final attempt failed"), any());
        verify(events).publishEvent(any(RoomEvent.class));
        verify(objectStorage).delete("raw/fail.jpg");
    }

    @Test
    @DisplayName("markJobFailed: khi cập nhật DB trả về 0 (đã bị luồng khác sửa), không xóa rawKey")
    void markJobFailed_whenUpdateFails_shouldNotDeleteRawKey() {
        Long assetId = 105L;
        MediaAsset asset = createAssetWithAttempts(assetId, 4, "raw/concur.jpg");
        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(mediaAssetRepo.markProcessingFailed(eq(assetId), anyString(), any())).thenReturn(0);

        worker.markJobFailed(asset, "FAILED_CONCURRENT", Instant.now());

        verify(mediaAssetRepo).markProcessingFailed(eq(assetId), eq("FAILED_CONCURRENT"), any());
        verify(events, never()).publishEvent(any());
        verify(objectStorage, never()).delete(anyString());
    }

    @Test
    @DisplayName("Kiểm tra thời gian retry backoff lũy tiến: 1m -> 5m -> 15m")
    void calculateNextRunAt_shouldMatchExponentialDelays() {
        Instant now = Instant.parse("2026-11-21T10:00:00Z");

        Instant retry1 = worker.calculateNextRunAt(1, now);
        assertThat(retry1).isEqualTo(now.plusSeconds(60)); // 1 phút

        Instant retry2 = worker.calculateNextRunAt(2, now);
        assertThat(retry2).isEqualTo(now.plusSeconds(300)); // 5 phút

        Instant retry3 = worker.calculateNextRunAt(3, now);
        assertThat(retry3).isEqualTo(now.plusSeconds(900)); // 15 phút

        Instant retryDefault = worker.calculateNextRunAt(4, now);
        assertThat(retryDefault).isEqualTo(now.plusSeconds(1800)); // 30 phút
    }
}
