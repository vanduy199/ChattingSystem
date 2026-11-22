package com.ChattingSystem.media.worker;

import com.ChattingSystem.media.dto.response.MediaStatusEventDto;
import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaQuality;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.repository.MediaAssetClaimDao;
import com.ChattingSystem.media.repository.MediaAssetRepository;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.media.worker.runner.FfmpegRunner;
import com.ChattingSystem.media.worker.runner.dto.MediaProbeResult;
import com.ChattingSystem.media.worker.scheduler.MediaWorker;
import com.ChattingSystem.media.worker.service.impl.MediaProcessorImpl;
import com.ChattingSystem.realtime.event.RoomEvent;
import com.ChattingSystem.realtime.event.RoomEventType;
import com.ChattingSystem.shared.properties.MediaWorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử tích hợp luồng hoàn chỉnh của Media Worker:
 * Claim Job qua DAO -> Điều phối luồng Worker -> Nghiệp vụ Hậu kỳ Processor -> Phát sự kiện STOMP MEDIA_STATUS.
 */
class MediaEndToEndFlowUnitTest {

    private MediaAssetClaimDao claimDao;
    private MediaAssetRepository mediaAssetRepo;
    private ObjectStorage objectStorage;
    private FfmpegRunner ffmpegRunner;
    private ApplicationEventPublisher events;
    private TransactionTemplate txTemplate;
    private ThreadPoolTaskExecutor mediaTaskExecutor;
    private MediaWorkerProperties properties;

    private MediaProcessorImpl processor;
    private MediaWorker worker;

    @TempDir
    Path tempFolder;

    @BeforeEach
    void setUp() {
        claimDao = mock(MediaAssetClaimDao.class);
        mediaAssetRepo = mock(MediaAssetRepository.class);
        objectStorage = mock(ObjectStorage.class);
        ffmpegRunner = mock(FfmpegRunner.class);
        events = mock(ApplicationEventPublisher.class);
        txTemplate = mock(TransactionTemplate.class);
        mediaTaskExecutor = mock(ThreadPoolTaskExecutor.class);

        // Giả lập TransactionTemplate thực thi ngay lập tức callback
        when(txTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });

        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(txTemplate).executeWithoutResult(any());

        // Giả lập tải file về đĩa tạm
        when(objectStorage.downloadToTemp(anyString(), any(Path.class), anyLong())).thenAnswer(invocation -> {
            Path targetPath = invocation.getArgument(1);
            if (!Files.exists(targetPath)) {
                Files.createFile(targetPath);
            }
            return targetPath;
        });

        properties = new MediaWorkerProperties(
                true,
                1,
                Duration.ofSeconds(2),
                Duration.ofMinutes(10),
                4,
                tempFolder.toString(),
                256 * 1024 * 1024L,
                50_000_000L,
                300
        );

        processor = new MediaProcessorImpl(
                mediaAssetRepo,
                objectStorage,
                ffmpegRunner,
                properties,
                events,
                txTemplate
        );

        worker = new MediaWorker(
                claimDao,
                mediaAssetRepo,
                processor,
                mediaTaskExecutor,
                properties,
                events,
                txTemplate,
                objectStorage
        );
    }

    @Test
    @DisplayName("Chu trình đầu cuối thành công: Claim job -> Xử lý video Remux -> AVAILABLE -> Phát RoomEvent MEDIA_STATUS")
    void fullFlow_videoRemuxSuccess_shouldPublishMediaStatusAvailable() {
        Long assetId = 500L;
        Long roomId = 12L;
        String rawKey = "raw/2026/11/21/clip.mp4";

        MediaAsset asset = MediaAsset.singleUpload(
                10L,
                roomId,
                UUID.randomUUID(),
                MediaKind.VIDEO,
                MediaQuality.STANDARD,
                "video/mp4",
                5000000L,
                rawKey
        );
        asset.setId(assetId);
        asset.markProcessing("video/mp4");

        // 1. Giả lập Claim DAO trả về assetId
        when(claimDao.claimJobs(eq(1), any(), any(), eq(4))).thenReturn(List.of(assetId));

        // 2. Giả lập mediaTaskExecutor chạy ngay lập tức tác vụ
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(mediaTaskExecutor).execute(any(Runnable.class));

        // 3. Giả lập Repository và FFmpeg Runner
        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(ffmpegRunner.probe(any(Path.class))).thenReturn(new MediaProbeResult(
                1280, 720, 12000, "h264", "aac", "yuv420p", 0, true, true, "mp4"
        ));
        when(mediaAssetRepo.markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), any(), any())).thenReturn(1);

        // Kích hoạt chu trình xử lý của Worker
        worker.pollAndProcess();

        // Kiểm chứng các bước:
        // - Đã remux video
        verify(ffmpegRunner).remuxVideo(any(Path.class), any(Path.class));
        // - Đã trích thumbnail từ displayPath
        verify(ffmpegRunner).extractThumbnail(any(Path.class), any(Path.class), eq(true), eq(12000));
        // - Đã upload display và thumbnail lên Storage
        verify(objectStorage).putFile(anyString(), any(Path.class), eq("video/mp4"));
        verify(objectStorage).putFile(anyString(), any(Path.class), eq("image/webp"));
        // - Đã cập nhật AVAILABLE trong DB
        verify(mediaAssetRepo).markProcessingAvailable(eq(assetId), anyString(), anyString(), eq("video/mp4"),
                eq(1280), eq(720), eq(12000), any());

        // - Đã phát sự kiện STOMP MEDIA_STATUS với trạng thái AVAILABLE
        ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        RoomEvent published = eventCaptor.getValue();
        assertThat(published.roomId()).isEqualTo(roomId);
        assertThat(published.type()).isEqualTo(RoomEventType.MEDIA_STATUS);

        MediaStatusEventDto dto = (MediaStatusEventDto) published.data();
        assertThat(dto.status()).isEqualTo(MediaStatus.AVAILABLE);
        assertThat(dto.mediaAssetId()).isEqualTo(assetId);
        assertThat(dto.width()).isEqualTo(1280);
        assertThat(dto.height()).isEqualTo(720);
        assertThat(dto.durationMs()).isEqualTo(12000);

        // - Đã dọn rawKey trên Storage
        verify(objectStorage).delete(rawKey);

        // - InFlight trở lại 0
        assertThat(worker.getInFlightCount()).isEqualTo(0);
    }
}
