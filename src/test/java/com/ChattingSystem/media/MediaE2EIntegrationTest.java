package com.ChattingSystem.media;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.MemberStatus;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.media.dto.request.BatchAccessUrlRequest;
import com.ChattingSystem.media.dto.request.CompleteUploadRequest;
import com.ChattingSystem.media.dto.request.InitUploadRequest;
import com.ChattingSystem.media.dto.response.BatchAccessUrlResponse;
import com.ChattingSystem.media.dto.response.InitUploadResponse;
import com.ChattingSystem.media.dto.response.MediaAccessItemResponse;
import com.ChattingSystem.media.dto.response.MediaStatusEventDto;
import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaQuality;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.repository.MediaAssetCleanupDao;
import com.ChattingSystem.media.repository.MediaAssetRepository;
import com.ChattingSystem.media.repository.dto.DeletedPurgeTarget;
import com.ChattingSystem.media.service.MediaAccessService;
import com.ChattingSystem.media.service.MediaUploadService;
import com.ChattingSystem.media.service.impl.MediaAccessServiceImpl;
import com.ChattingSystem.media.service.impl.MediaUploadServiceImpl;
import com.ChattingSystem.media.storage.dto.response.ObjectMetadataResponse;
import com.ChattingSystem.media.storage.dto.response.PresignedUrlResponse;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.media.worker.runner.FfmpegRunner;
import com.ChattingSystem.media.worker.runner.dto.MediaProbeResult;
import com.ChattingSystem.media.worker.scheduler.MediaCleanupJob;
import com.ChattingSystem.media.worker.service.impl.MediaProcessorImpl;
import com.ChattingSystem.realtime.event.RoomEvent;
import com.ChattingSystem.realtime.event.RoomEventType;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.MediaGcProperties;
import com.ChattingSystem.shared.properties.MediaLimitsProperties;
import com.ChattingSystem.shared.properties.MediaStorageProperties;
import com.ChattingSystem.shared.properties.MediaWorkerProperties;
import com.ChattingSystem.user.entity.Role;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Media E2E Integration Test: Kiểm thử tích hợp toàn bộ vòng đời tệp phương tiện (A -> Z)")
class MediaE2EIntegrationTest {

    // Services
    private MediaUploadService mediaUploadService;
    private MediaAccessService mediaAccessService;
    private MediaProcessorImpl mediaProcessor;
    private MediaCleanupJob mediaCleanupJob;

    // Mocks & Repos
    private MediaAssetRepository mediaAssetRepo;
    private RoomAccessService roomAccessService;
    private UserRepository userRepo;
    private RoomMemberRepository roomMemberRepo;
    private ObjectStorage objectStorage;
    private FfmpegRunner ffmpegRunner;
    private ApplicationEventPublisher events;
    private TransactionTemplate txTemplate;
    private MediaAssetCleanupDao cleanupDao;

    // Properties
    private MediaLimitsProperties limitsProperties;
    private MediaStorageProperties storageProperties;
    private MediaWorkerProperties workerProperties;
    private MediaGcProperties gcProperties;

    @TempDir
    Path tempFolder;

    private final AtomicLong idGenerator = new AtomicLong(1000L);

    @BeforeEach
    void setUp() {
        mediaAssetRepo = mock(MediaAssetRepository.class);
        roomAccessService = mock(RoomAccessService.class);
        userRepo = mock(UserRepository.class);
        roomMemberRepo = mock(RoomMemberRepository.class);
        objectStorage = mock(ObjectStorage.class);
        ffmpegRunner = mock(FfmpegRunner.class);
        events = mock(ApplicationEventPublisher.class);
        txTemplate = mock(TransactionTemplate.class);
        cleanupDao = mock(MediaAssetCleanupDao.class);

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

        // Cấu hình Properties với default constructor
        limitsProperties = new MediaLimitsProperties(null, null, null, null, null);

        storageProperties = new MediaStorageProperties(
                "test-media-bucket",
                "ap-southeast-1",
                "http://localhost:9000",
                "http://localhost:9000",
                true,
                "accessKey",
                "secretKey",
                5242880,
                Duration.ofMinutes(15),
                Duration.ofMinutes(30)
        );

        workerProperties = new MediaWorkerProperties(
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

        gcProperties = new MediaGcProperties(
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

        // Khởi tạo các Service
        mediaUploadService = new MediaUploadServiceImpl(
                mediaAssetRepo,
                roomAccessService,
                userRepo,
                objectStorage,
                limitsProperties,
                storageProperties,
                roomMemberRepo,
                events
        );

        mediaAccessService = new MediaAccessServiceImpl(
                roomAccessService,
                mediaAssetRepo,
                objectStorage,
                storageProperties
        );

        mediaProcessor = new MediaProcessorImpl(
                mediaAssetRepo,
                objectStorage,
                ffmpegRunner,
                workerProperties,
                events,
                txTemplate
        );

        mediaCleanupJob = new MediaCleanupJob(
                cleanupDao,
                objectStorage,
                gcProperties,
                txTemplate
        );

        // Giả lập lưu repo gán ID
        when(mediaAssetRepo.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset a = invocation.getArgument(0);
            if (a.getId() == null) {
                a.setId(idGenerator.incrementAndGet());
            }
            return a;
        });

        // Giả lập tải file về đĩa tạm
        when(objectStorage.downloadToTemp(anyString(), any(Path.class), anyLong())).thenAnswer(invocation -> {
            Path targetPath = invocation.getArgument(1);
            if (!Files.exists(targetPath)) {
                Files.createFile(targetPath);
            }
            return targetPath;
        });
    }

    @Test
    @DisplayName("E2E Full Flow: Init -> Complete -> Message Attach -> Worker Process -> Access URLs -> Delete -> Purge")
    void fullMediaLifecycle_happyPath() throws Exception {
        Long uploaderId = 10L;
        Long viewerId = 20L;
        Long roomId = 1L;
        UUID clientUploadId = UUID.randomUUID();

        // 1. Giả lập quyền truy cập phòng & vai trò
        ChatRoom publicRoom = ChatRoom.create("Phòng Chung", "Mô tả", 1L, 60, 1000, "public");
        publicRoom.setId(roomId);
        RoomMember activeMember = RoomMember.create(roomId, uploaderId, MemberRole.MEMBER, MemberStatus.ACTIVE);

        when(roomAccessService.requireActiveRoom(roomId)).thenReturn(publicRoom);
        when(roomAccessService.requireActiveMember(anyLong(), eq(roomId))).thenReturn(activeMember);

        User uploader = User.create("uid-10", "uploader@example.com", "Uploader", null, null);
        uploader.setId(uploaderId);
        when(userRepo.findById(uploaderId)).thenReturn(Optional.of(uploader));

        User viewer = User.create("uid-20", "viewer@example.com", "Viewer", null, null);
        viewer.setId(viewerId);
        when(userRepo.findById(viewerId)).thenReturn(Optional.of(viewer));

        // Khởi tạo thông số S3 presign
        PresignedUrlResponse putResponse = new PresignedUrlResponse(
                "http://s3/presigned-put", "PUT", Map.of(), Instant.now().plusSeconds(900)
        );
        when(objectStorage.presignSinglePut(anyString(), anyString(), anyLong(), any()))
                .thenReturn(putResponse);

        when(objectStorage.presignGet(anyString(), any()))
                .thenAnswer(inv -> new PresignedUrlResponse(
                        "http://s3/presigned-get/" + inv.getArgument(0), "GET", Map.of(), Instant.now().plusSeconds(1800)
                ));

        // -------------------------------------------------------------
        // Bước 1: Khởi tạo tải lên (POST /media/uploads)
        // -------------------------------------------------------------
        InitUploadRequest initReq = new InitUploadRequest(
                clientUploadId,
                roomId,
                "image/jpeg",
                1024 * 1024L, // 1MB -> SINGLE mode
                false
        );

        InitUploadResponse uploadResp = mediaUploadService.initUpload(uploaderId, initReq);
        assertThat(uploadResp).isNotNull();
        assertThat(uploadResp.mediaAssetId()).isNotNull();
        assertThat(uploadResp.uploadMode()).isEqualTo("SINGLE");
        assertThat(uploadResp.uploadTargets()).hasSize(1);
        assertThat(uploadResp.uploadTargets().get(0).url()).isEqualTo("http://s3/presigned-put");

        Long assetId = uploadResp.mediaAssetId();

        // Chuẩn bị asset sau init
        MediaAsset asset = MediaAsset.singleUpload(
                uploaderId,
                roomId,
                clientUploadId,
                MediaKind.IMAGE,
                MediaQuality.STANDARD,
                "image/jpeg",
                1024 * 1024L,
                "raw/2026/11/21/test.jpg"
        );
        asset.setId(assetId);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(mediaAssetRepo.claimForCompleting(eq(assetId), eq(uploaderId), any(), any())).thenReturn(1);

        // -------------------------------------------------------------
        // Bước 2: Hoàn tất tải lên (POST /media/uploads/{id}/complete)
        // -------------------------------------------------------------
        // Giả lập S3 kiểm tra ContentLength và magic bytes JPEG
        when(objectStorage.head(anyString())).thenReturn(Optional.of(new ObjectMetadataResponse(1024 * 1024L, "image/jpeg", "etag-1")));
        byte[] jpegHeader = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1
        };
        when(objectStorage.readRange(anyString(), anyLong(), anyLong())).thenReturn(jpegHeader);
        when(mediaAssetRepo.updateStatusToProcessing(eq(assetId), anyString(), any())).thenReturn(1);

        CompleteUploadRequest completeReq = new CompleteUploadRequest(null);
        var completeResp = mediaUploadService.completeUpload(uploaderId, assetId, completeReq);

        assertThat(completeResp.status()).isEqualTo(MediaStatus.PROCESSING);
        asset.markProcessing("image/jpeg");

        // -------------------------------------------------------------
        // Bước 3: Gắn tệp vào tin nhắn chat (Message Service)
        // -------------------------------------------------------------
        Long messageId = 777L;
        asset.attachToMessage(messageId, 0);
        assertThat(asset.getMessageId()).isEqualTo(messageId);

        // -------------------------------------------------------------
        // Bước 4: MediaWorker / MediaProcessor xử lý hậu kỳ nền
        // -------------------------------------------------------------
        MediaProbeResult probeResult = new MediaProbeResult(
                1920, 1080, null, "mjpeg", null, "yuvj420p", 0, false, false, "image2"
        );
        when(ffmpegRunner.probe(any(Path.class))).thenReturn(probeResult);
        when(mediaAssetRepo.markProcessingAvailable(eq(assetId), anyString(), anyString(), anyString(), eq(1920), eq(1080), isNull(), any()))
                .thenReturn(1);

        mediaProcessor.process(assetId);

        // Xác nhận đã phát RoomEvent MEDIA_STATUS AVAILABLE
        ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(events, atLeastOnce()).publishEvent(eventCaptor.capture());
        RoomEvent publishedEvent = eventCaptor.getValue();
        assertThat(publishedEvent.type()).isEqualTo(RoomEventType.MEDIA_STATUS);
        MediaStatusEventDto statusDto = (MediaStatusEventDto) publishedEvent.data();
        assertThat(statusDto.status()).isEqualTo(MediaStatus.AVAILABLE);

        asset.markAvailable("display/2026/11/21/test.webp", "thumb/2026/11/21/test.webp", 1920, 1080);

        // -------------------------------------------------------------
        // Bước 5: Cấp URL xem theo lô (POST /media/access-urls)
        // -------------------------------------------------------------
        BatchAccessUrlRequest accessReq = new BatchAccessUrlRequest(roomId, List.of(assetId));
        when(mediaAssetRepo.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                .thenReturn(List.of(assetId));
        when(mediaAssetRepo.findAllById(anyCollection()))
                .thenReturn(List.of(asset));

        BatchAccessUrlResponse accessResp = mediaAccessService.getAccessUrls(viewerId, accessReq);
        assertThat(accessResp.items()).hasSize(1);
        MediaAccessItemResponse item = accessResp.items().get(0);
        assertThat(item.mediaAssetId()).isEqualTo(assetId);
        assertThat(item.status()).isEqualTo(MediaStatus.AVAILABLE);
        assertThat(item.urls().display()).startsWith("http://s3/presigned-get");
        assertThat(item.urls().thumbnail()).startsWith("http://s3/presigned-get");

        // -------------------------------------------------------------
        // Bước 6: Xóa tệp phương tiện (DELETE /media/{id})
        // -------------------------------------------------------------
        when(roomMemberRepo.findByRoomIdAndUserId(roomId, uploaderId)).thenReturn(Optional.of(activeMember));
        when(mediaAssetRepo.markStatusToDeleted(eq(assetId), any())).thenReturn(1);

        mediaUploadService.deleteAsset(uploaderId, assetId);

        // Xác nhận phát sự kiện DELETED qua WebSocket STOMP
        verify(events, atLeast(2)).publishEvent(eventCaptor.capture());
        RoomEvent deletedEvent = eventCaptor.getValue();
        assertThat(deletedEvent.type()).isEqualTo(RoomEventType.MEDIA_STATUS);
        MediaStatusEventDto deletedDto = (MediaStatusEventDto) deletedEvent.data();
        assertThat(deletedDto.status()).isEqualTo(MediaStatus.DELETED);

        // -------------------------------------------------------------
        // Bước 7: MediaCleanupJob dọn rác vật lý (Quy tắc 6 PURGE)
        // -------------------------------------------------------------
        DeletedPurgeTarget purgeTarget = new DeletedPurgeTarget(
                assetId,
                asset.getRawKey(),
                asset.getDisplayKey(),
                asset.getThumbKey(),
                null
        );

        when(cleanupDao.tryAcquireDbCleanupLock()).thenReturn(true);
        when(cleanupDao.claimExpiredUploading(any(), any(), any(), anyInt())).thenReturn(Collections.emptyList());
        when(cleanupDao.claimPurgeTargets(any(), any(), any(), anyInt()))
                .thenReturn(List.of(purgeTarget))
                .thenReturn(Collections.emptyList());

        mediaCleanupJob.runCleanup();

        // Xác nhận đã xóa sạch các object trên Cloud Storage và cập nhật PURGED trong DB
        // rawKey bị xóa 2 lần: lần 1 sau khi MediaProcessor xử lý xong, lần 2 khi CleanupJob purge
        verify(objectStorage, times(2)).delete(asset.getRawKey());
        verify(objectStorage).delete(asset.getDisplayKey());
        verify(objectStorage).delete(asset.getThumbKey());
        verify(cleanupDao).markDeletedPurged(eq(List.of(assetId)), any(Instant.class));
    }

    @Test
    @DisplayName("Bảo vệ phân quyền: Guest bị chặn ở phòng PRIVATE, Guest không được bật HD")
    void guardrails_guestPermissions() {
        Long guestId = 99L;
        Long privateRoomId = 2L;
        Long publicRoomId = 1L;

        // Giả lập phòng PRIVATE ném lỗi LOGIN_REQUIRED_TO_CHAT đối với Guest
        ChatRoom privateRoom = ChatRoom.create("Phòng Riêng", "Mô tả", 1L, 60, 1000, "private");
        privateRoom.setId(privateRoomId);
        when(roomAccessService.requireActiveRoom(privateRoomId)).thenReturn(privateRoom);

        User guestUser = User.createGuest("Khách #1234");
        guestUser.setId(guestId);
        when(userRepo.findById(guestId)).thenReturn(Optional.of(guestUser));

        InitUploadRequest reqPrivate = new InitUploadRequest(
                UUID.randomUUID(), privateRoomId, "image/jpeg", 1000L, false
        );

        assertThatThrownBy(() -> mediaUploadService.initUpload(guestId, reqPrivate))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.LOGIN_REQUIRED_TO_CHAT));

        // Guest ở phòng PUBLIC nhưng bật hd: true -> ACCESS_DENIED
        ChatRoom publicRoom = ChatRoom.create("Phòng Chung", "Mô tả", 1L, 60, 1000, "public");
        publicRoom.setId(publicRoomId);
        when(roomAccessService.requireActiveRoom(publicRoomId)).thenReturn(publicRoom);

        InitUploadRequest reqHd = new InitUploadRequest(
                UUID.randomUUID(), publicRoomId, "image/jpeg", 1000L, true
        );

        assertThatThrownBy(() -> mediaUploadService.initUpload(guestId, reqHd))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("Bảo vệ IDOR: Người ngoài hoặc phòng khác không thể lấy access-url của tệp")
    void idorProtection_cannotAccessUrlsFromOtherRooms() {
        Long outsiderId = 888L;
        Long roomId = 1L;
        Long targetAssetId = 500L;

        // Người ngoài bị chặn bởi requireActiveMember
        doThrow(new AppException(ErrorCode.NOT_ROOM_MEMBER))
                .when(roomAccessService).requireActiveMember(outsiderId, roomId);

        BatchAccessUrlRequest req = new BatchAccessUrlRequest(roomId, List.of(targetAssetId));

        assertThatThrownBy(() -> mediaAccessService.getAccessUrls(outsiderId, req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_ROOM_MEMBER));
    }

    @Test
    @DisplayName("Hủy phiên tải (Abort Upload): Xóa multipart upload trên Storage và cập nhật FAILED")
    void abortUpload_success() {
        Long uploaderId = 10L;
        Long assetId = 701L;

        MediaAsset asset = MediaAsset.multipartUpload(
                uploaderId,
                1L,
                UUID.randomUUID(),
                MediaKind.VIDEO,
                MediaQuality.STANDARD,
                "video/mp4",
                10_000_000L,
                "raw/clip.mp4",
                "upload-id-999"
        );
        asset.setId(assetId);

        when(mediaAssetRepo.findById(assetId)).thenReturn(Optional.of(asset));
        when(mediaAssetRepo.claimForAbort(eq(assetId), eq(uploaderId), eq("USER_ABORTED"), any())).thenReturn(1);

        mediaUploadService.abortUpload(uploaderId, assetId);

        // Xác nhận đã hủy multipart upload trên Cloud Storage
        verify(objectStorage).abortMultipartUpload("raw/clip.mp4", "upload-id-999");
        verify(mediaAssetRepo).claimForAbort(eq(assetId), eq(uploaderId), eq("USER_ABORTED"), any());
    }
}
