package com.ChattingSystem.media.worker.service;

import com.ChattingSystem.media.dto.response.MediaStatusEventDto;
import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaQuality;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.repository.MediaAssetRepository;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.media.worker.exception.MediaValidationException;
import com.ChattingSystem.media.worker.runner.FfmpegRunner;
import com.ChattingSystem.media.worker.runner.dto.MediaProbeResult;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaProcessorUnitTest {

    private MediaAssetRepository mediaAssetRepo;
    private ObjectStorage objectStorage;
    private FfmpegRunner ffmpegRunner;
    private ApplicationEventPublisher events;
    private TransactionTemplate txTemplate;
    private MediaWorkerProperties properties;
    private MediaProcessorImpl processor;

    @TempDir
    Path tempFolder;

    @BeforeEach
    void setUp() {
        mediaAssetRepo = mock(MediaAssetRepository.class);
        objectStorage = mock(ObjectStorage.class);
        ffmpegRunner = mock(FfmpegRunner.class);
        events = mock(ApplicationEventPublisher.class);
        txTemplate = mock(TransactionTemplate.class);

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

        // Giả lập downloadToTemp tạo file rỗng nếu chưa tồn tại
        when(objectStorage.downloadToTemp(anyString(), any(Path.class), anyLong())).thenAnswer(invocation -> {
            Path targetPath = invocation.getArgument(1);
            if (!java.nio.file.Files.exists(targetPath)) {
                java.nio.file.Files.createFile(targetPath);
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
    }

    private MediaAsset createProcessingAsset(Long id, MediaKind kind, MediaQuality quality, String contentType, String rawKey) {
        MediaAsset asset = MediaAsset.singleUpload(
                100L,
                1L,
                UUID.randomUUID(),
                kind,
                quality,
                contentType,
                1024L,
                rawKey
        );
        asset.setId(id);
        asset.markProcessing(contentType);
        return asset;
    }

    @Test
    @DisplayName("Ảnh tĩnh STANDARD: convert sang webp với max 2048px Q82, tạo thumb, cập nhật AVAILABLE, bắn event và xóa raw")
    void process_standardImage_success() {
        Long assetId = 10L;
        String rawKey = "raw/2026/11/21/test.jpg";
        MediaAsset asset = createProcessingAsset(assetId, MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", rawKey);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(ffmpegRunner.probe(any(Path.class))).thenReturn(new MediaProbeResult(
                1920, 1080, null, null, null, "yuv420p", 0, false, false, "image2"
        ));
        when(mediaAssetRepo.markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), any(), any())).thenReturn(1);

        processor.process(assetId);

        verify(objectStorage).downloadToTemp(eq(rawKey), any(Path.class), eq(properties.maxDownloadBytes()));
        verify(ffmpegRunner).convertImage(any(Path.class), any(Path.class), eq(2048), eq(82));
        verify(ffmpegRunner).extractThumbnail(any(Path.class), any(Path.class), eq(false), any());
        verify(objectStorage, org.mockito.Mockito.times(2)).putFile(anyString(), any(Path.class), eq("image/webp"));
        verify(mediaAssetRepo).markProcessingAvailable(eq(assetId), anyString(), anyString(), eq("image/webp"),
                eq(1920), eq(1080), any(), any());

        ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        RoomEvent published = eventCaptor.getValue();
        assertThat(published.roomId()).isEqualTo(1L);
        assertThat(published.type()).isEqualTo(RoomEventType.MEDIA_STATUS);
        MediaStatusEventDto dto = (MediaStatusEventDto) published.data();
        assertThat(dto.status()).isEqualTo(MediaStatus.AVAILABLE);
        assertThat(dto.mediaAssetId()).isEqualTo(assetId);
        assertThat(dto.width()).isEqualTo(1920);
        assertThat(dto.height()).isEqualTo(1080);

        verify(objectStorage).delete(rawKey);
    }

    @Test
    @DisplayName("Ảnh tĩnh HD: convert sang webp với max 4096px Q92")
    void process_hdImage_shouldUseHdQuality() {
        Long assetId = 11L;
        String rawKey = "raw/2026/11/21/hd.png";
        MediaAsset asset = createProcessingAsset(assetId, MediaKind.IMAGE, MediaQuality.HD, "image/png", rawKey);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(ffmpegRunner.probe(any(Path.class))).thenReturn(new MediaProbeResult(
                3840, 2160, null, null, null, "rgba", 0, false, false, "image2"
        ));
        when(mediaAssetRepo.markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), any(), any())).thenReturn(1);

        processor.process(assetId);

        verify(ffmpegRunner).convertImage(any(Path.class), any(Path.class), eq(4096), eq(92));
        verify(mediaAssetRepo).markProcessingAvailable(eq(assetId), anyString(), anyString(), eq("image/webp"),
                eq(3840), eq(2160), any(), any());
    }

    @Test
    @DisplayName("Ảnh GIF: copy raw giữ animation, trích thumb, lưu MIME image/gif")
    void process_gifImage_shouldPreserveGifAnimation() {
        Long assetId = 12L;
        String rawKey = "raw/2026/11/21/animated.gif";
        MediaAsset asset = createProcessingAsset(assetId, MediaKind.IMAGE, MediaQuality.STANDARD, "image/gif", rawKey);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(ffmpegRunner.probe(any(Path.class))).thenReturn(new MediaProbeResult(
                500, 500, null, null, null, "pal8", 0, false, false, "gif"
        ));
        when(mediaAssetRepo.markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), any(), any())).thenReturn(1);

        processor.process(assetId);

        verify(ffmpegRunner, never()).convertImage(any(Path.class), any(Path.class), anyInt(), anyInt());
        verify(ffmpegRunner).extractThumbnail(any(Path.class), any(Path.class), eq(false), any());
        verify(mediaAssetRepo).markProcessingAvailable(eq(assetId), anyString(), anyString(), eq("image/gif"),
                eq(500), eq(500), any(), any());
    }

    @Test
    @DisplayName("Video REMUX: đủ điều kiện (h264, yuv420p, rotation 0, aac, <=1920x1920) gọi remuxVideo")
    void process_video_eligibleForRemux_shouldCallRemux() {
        Long assetId = 13L;
        String rawKey = "raw/2026/11/21/video.mp4";
        MediaAsset asset = createProcessingAsset(assetId, MediaKind.VIDEO, MediaQuality.STANDARD, "video/mp4", rawKey);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(ffmpegRunner.probe(any(Path.class))).thenReturn(new MediaProbeResult(
                1280, 720, 15000, "h264", "aac", "yuv420p", 0, true, true, "mp4"
        ));
        when(mediaAssetRepo.markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), any(), any())).thenReturn(1);

        processor.process(assetId);

        verify(ffmpegRunner).remuxVideo(any(Path.class), any(Path.class));
        verify(ffmpegRunner, never()).transcodeVideo(any(Path.class), any(Path.class));
        verify(ffmpegRunner).extractThumbnail(any(Path.class), any(Path.class), eq(true), eq(15000));
        verify(mediaAssetRepo).markProcessingAvailable(eq(assetId), anyString(), anyString(), eq("video/mp4"),
                eq(1280), eq(720), eq(15000), any());
    }

    @Test
    @DisplayName("Video TRANSCODE: không đủ điều kiện remux (codec hevc) gọi transcodeVideo")
    void process_video_notEligibleForRemux_shouldCallTranscode() {
        Long assetId = 14L;
        String rawKey = "raw/2026/11/21/video.mov";
        MediaAsset asset = createProcessingAsset(assetId, MediaKind.VIDEO, MediaQuality.STANDARD, "video/quicktime", rawKey);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(ffmpegRunner.probe(any(Path.class))).thenReturn(new MediaProbeResult(
                1920, 1080, 20000, "hevc", "aac", "yuv420p", 0, true, true, "mov"
        ));
        when(mediaAssetRepo.markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), any(), any())).thenReturn(1);

        processor.process(assetId);

        verify(ffmpegRunner).transcodeVideo(any(Path.class), any(Path.class));
        verify(ffmpegRunner, never()).remuxVideo(any(Path.class), any(Path.class));
    }

    @Test
    @DisplayName("Vi phạm giới hạn maxPixels (bom nén) -> chuyển sang BLOCKED, bắn MEDIA_STATUS(BLOCKED) và xóa rawKey")
    void process_exceedsMaxPixels_shouldMarkBlocked() {
        Long assetId = 15L;
        String rawKey = "raw/2026/11/21/bomb.jpg";
        MediaAsset asset = createProcessingAsset(assetId, MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", rawKey);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(ffmpegRunner.probe(any(Path.class))).thenReturn(new MediaProbeResult(
                10000, 6000, null, null, null, "yuv420p", 0, false, false, "image2"
        ));
        when(mediaAssetRepo.markProcessingBlocked(eq(assetId), anyString(), any())).thenReturn(1);

        processor.process(assetId);

        verify(mediaAssetRepo).markProcessingBlocked(eq(assetId), anyString(), any());
        verify(ffmpegRunner, never()).convertImage(any(), any(), anyInt(), anyInt());

        ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        MediaStatusEventDto dto = (MediaStatusEventDto) eventCaptor.getValue().data();
        assertThat(dto.status()).isEqualTo(MediaStatus.BLOCKED);
        assertThat(dto.failureReason()).isEqualTo("MEDIA_BLOCKED");

        verify(objectStorage).delete(rawKey);
    }

    @Test
    @DisplayName("Video quá thời lượng (>300s) -> chuyển sang BLOCKED")
    void process_videoTooLong_shouldMarkBlocked() {
        Long assetId = 16L;
        String rawKey = "raw/2026/11/21/long.mp4";
        MediaAsset asset = createProcessingAsset(assetId, MediaKind.VIDEO, MediaQuality.STANDARD, "video/mp4", rawKey);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(ffmpegRunner.probe(any(Path.class))).thenReturn(new MediaProbeResult(
                1280, 720, 350000, "h264", "aac", "yuv420p", 0, true, true, "mp4"
        ));
        when(mediaAssetRepo.markProcessingBlocked(eq(assetId), anyString(), any())).thenReturn(1);

        processor.process(assetId);

        verify(mediaAssetRepo).markProcessingBlocked(eq(assetId), anyString(), any());
        verify(ffmpegRunner, never()).remuxVideo(any(), any());
        verify(ffmpegRunner, never()).transcodeVideo(any(), any());
    }

    @Test
    @DisplayName("Tệp hỏng khi probe ném MediaValidationException -> chuyển sang BLOCKED")
    void process_corruptFile_shouldCatchValidationExceptionAndMarkBlocked() {
        Long assetId = 17L;
        String rawKey = "raw/2026/11/21/corrupt.mp4";
        MediaAsset asset = createProcessingAsset(assetId, MediaKind.VIDEO, MediaQuality.STANDARD, "video/mp4", rawKey);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(ffmpegRunner.probe(any(Path.class))).thenThrow(new MediaValidationException("Tệp hỏng"));
        when(mediaAssetRepo.markProcessingBlocked(eq(assetId), anyString(), any())).thenReturn(1);

        processor.process(assetId);

        verify(mediaAssetRepo).markProcessingBlocked(eq(assetId), anyString(), any());
        verify(objectStorage).delete(rawKey);
    }

    @Test
    @DisplayName("Khi cập nhật AVAILABLE thất bại (updated=0 do tệp đã bị xóa) -> dọn display/thumb đã upload")
    void process_concurrentDelete_shouldCleanUpUploadedArtifacts() {
        Long assetId = 18L;
        String rawKey = "raw/2026/11/21/del.jpg";
        MediaAsset asset = createProcessingAsset(assetId, MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", rawKey);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(ffmpegRunner.probe(any(Path.class))).thenReturn(new MediaProbeResult(
                800, 600, null, null, null, "yuv420p", 0, false, false, "image2"
        ));
        // Giả lập tệp đã bị xóa/hủy trong lúc worker đang convert -> DB trả về 0 dòng cập nhật
        when(mediaAssetRepo.markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), any(), any())).thenReturn(0);

        processor.process(assetId);

        // Phải dọn displayKey và thumbKey
        verify(objectStorage, never()).delete(rawKey);
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Bỏ qua khi MediaAsset không tồn tại hoặc status không phải PROCESSING")
    void process_nonProcessingOrNotFound_shouldSkip() {
        when(mediaAssetRepo.findById(999L)).thenReturn(Optional.empty());
        processor.process(999L);
        verify(objectStorage, never()).downloadToTemp(any(), any(), anyLong());

        MediaAsset availableAsset = createProcessingAsset(100L, MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", "raw/test.jpg");
        availableAsset.markAvailable("display/key", "thumb/key", 100, 100);
        when(mediaAssetRepo.findById(100L)).thenReturn(Optional.of(availableAsset));

        processor.process(100L);
        verify(objectStorage, never()).downloadToTemp(any(), any(), anyLong());
    }

    @Test
    @DisplayName("Ảnh tĩnh: dù displayProbe có durationMs = 40, finalDurationMs vẫn ép về null")
    void process_image_shouldForceDurationMsNullEvenIfProbeReturnsDuration() {
        Long assetId = 19L;
        String rawKey = "raw/2026/11/21/photo.jpg";
        MediaAsset asset = createProcessingAsset(assetId, MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", rawKey);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(ffmpegRunner.probe(any(Path.class))).thenReturn(new MediaProbeResult(
                1920, 1080, 40, null, null, "yuv420p", 0, false, false, "image2"
        ));
        when(mediaAssetRepo.markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), any(), any())).thenReturn(1);

        processor.process(assetId);

        // durationMs được truyền vào markProcessingAvailable bắt buộc phải là null
        verify(mediaAssetRepo).markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(),
                eq(1920), eq(1080), eq(null), any());

        ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        MediaStatusEventDto dto = (MediaStatusEventDto) eventCaptor.getValue().data();
        assertThat(dto.durationMs()).isNull();
    }

    @Test
    @DisplayName("Video REMUX ném AppException -> tự fallback sang transcodeVideo và hoàn tất AVAILABLE")
    void process_video_remuxThrowsAppException_shouldFallbackToTranscode() {
        Long assetId = 20L;
        String rawKey = "raw/2026/11/21/video_corrupt_remux.mp4";
        MediaAsset asset = createProcessingAsset(assetId, MediaKind.VIDEO, MediaQuality.STANDARD, "video/mp4", rawKey);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(ffmpegRunner.probe(any(Path.class))).thenReturn(new MediaProbeResult(
                1280, 720, 10000, "h264", "aac", "yuv420p", 0, true, true, "mp4"
        ));
        // Remux thất bại
        org.mockito.Mockito.doThrow(new com.ChattingSystem.shared.exeption.AppException(
                com.ChattingSystem.shared.exeption.ErrorCode.STORAGE_ERROR, "Remux failed"
        )).when(ffmpegRunner).remuxVideo(any(Path.class), any(Path.class));

        when(mediaAssetRepo.markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), any(), any())).thenReturn(1);

        processor.process(assetId);

        // Phải gọi transcodeVideo fallback
        verify(ffmpegRunner).transcodeVideo(any(Path.class), any(Path.class));
        verify(mediaAssetRepo).markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("Lấy messageId mới nhất trước khi bắn RoomEvent nếu tin nhắn được gửi trong lúc worker đang xử lý")
    void process_assetInitiallyNoMessageId_shouldFetchLatestMessageIdBeforePublishing() {
        Long assetId = 21L;
        String rawKey = "raw/2026/11/21/chat_attach.jpg";
        MediaAsset assetWithoutMessage = createProcessingAsset(assetId, MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", rawKey);
        // asset ban đầu có messageId == null
        assertThat(assetWithoutMessage.getMessageId()).isNull();

        MediaAsset assetWithMessage = createProcessingAsset(assetId, MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", rawKey);
        assetWithMessage.attachToMessage(999L, 0);

        // Lần 1 gọi findById (lúc bắt đầu process) trả về assetWithoutMessage
        // Lần 2 gọi findById (trong transaction sau khi markProcessingAvailable) trả về assetWithMessage
        when(mediaAssetRepo.findById(assetId))
                .thenReturn(Optional.of(assetWithoutMessage))
                .thenReturn(Optional.of(assetWithMessage));

        when(ffmpegRunner.probe(any(Path.class))).thenReturn(new MediaProbeResult(
                1000, 1000, null, null, null, "yuv420p", 0, false, false, "image2"
        ));
        when(mediaAssetRepo.markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), any(), any())).thenReturn(1);

        processor.process(assetId);

        ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        MediaStatusEventDto dto = (MediaStatusEventDto) eventCaptor.getValue().data();
        assertThat(dto.messageId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("downloadToTemp ném MEDIA_TOO_LARGE -> bắt lỗi và chuyển sang BLOCKED mà không rethrow")
    void process_downloadThrowsMediaTooLarge_shouldCatchAndMarkBlockedWithoutRethrow() {
        Long assetId = 22L;
        String rawKey = "raw/2026/11/21/oversized.mp4";
        MediaAsset asset = createProcessingAsset(assetId, MediaKind.VIDEO, MediaQuality.STANDARD, "video/mp4", rawKey);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(objectStorage.downloadToTemp(eq(rawKey), any(Path.class), anyLong()))
                .thenThrow(new com.ChattingSystem.shared.exeption.AppException(
                        com.ChattingSystem.shared.exeption.ErrorCode.MEDIA_TOO_LARGE, "Dung lượng tệp vượt ngưỡng tối đa cho phép xử lý"
                ));
        when(mediaAssetRepo.markProcessingBlocked(eq(assetId), anyString(), any())).thenReturn(1);

        // Không ném ngoại lệ ra ngoài
        processor.process(assetId);

        verify(mediaAssetRepo).markProcessingBlocked(eq(assetId), anyString(), any());

        ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        MediaStatusEventDto dto = (MediaStatusEventDto) eventCaptor.getValue().data();
        assertThat(dto.status()).isEqualTo(MediaStatus.BLOCKED);
        assertThat(dto.failureReason()).isEqualTo("MEDIA_BLOCKED");
    }
}
