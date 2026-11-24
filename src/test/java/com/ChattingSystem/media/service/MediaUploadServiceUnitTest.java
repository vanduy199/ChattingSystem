package com.ChattingSystem.media.service;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.MemberStatus;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.realtime.event.RoomEvent;
import com.ChattingSystem.realtime.event.RoomEventType;
import org.springframework.context.ApplicationEventPublisher;
import com.ChattingSystem.media.dto.request.CompleteUploadRequest;
import com.ChattingSystem.media.dto.request.CompletedPartDto;
import com.ChattingSystem.media.dto.request.InitUploadRequest;
import com.ChattingSystem.media.dto.response.CompleteUploadResponse;
import com.ChattingSystem.media.dto.response.CompletedPartSummaryDto;
import com.ChattingSystem.media.dto.response.InitUploadResponse;
import com.ChattingSystem.media.dto.response.UploadStatusResponse;
import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaQuality;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.repository.MediaAssetRepository;
import com.ChattingSystem.media.service.impl.MediaUploadServiceImpl;
import com.ChattingSystem.media.storage.dto.response.ObjectMetadataResponse;
import com.ChattingSystem.media.storage.dto.response.PartSummaryResponse;
import com.ChattingSystem.media.storage.dto.response.PresignedUrlResponse;
import com.ChattingSystem.media.storage.exception.NoSuchUploadStorageException;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.MediaLimitsProperties;
import com.ChattingSystem.shared.properties.MediaStorageProperties;
import com.ChattingSystem.user.entity.Role;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaUploadServiceUnitTest {

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private RoomAccessService roomAccessService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectStorage objectStorage;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MediaLimitsProperties limitsProperties;
    private MediaStorageProperties storageProperties;

    private MediaUploadServiceImpl service;

    private User regularUser;
    private User guestUser;
    private ChatRoom publicRoom;
    private ChatRoom privateRoom;

    private static final byte[] VALID_JPEG_HEADER = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1
    };

    private static final byte[] RANDOM_BYTES = new byte[]{
            'M', 'Z', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    @BeforeEach
    void setUp() {
        limitsProperties = new MediaLimitsProperties(null, null, null, null, null);
        storageProperties = new MediaStorageProperties(
                "test-bucket", "us-east-1", "http://localhost:9002", null,
                true, "access", "secret", 5242880, Duration.ofMinutes(15), Duration.ofMinutes(30)
        );

        service = new MediaUploadServiceImpl(
                mediaAssetRepository,
                roomAccessService,
                userRepository,
                objectStorage,
                limitsProperties,
                storageProperties,
                roomMemberRepository,
                eventPublisher
        );

        regularUser = User.create("uid-user-1", "user@example.com", "Regular User", null, null);
        regularUser.setId(1L);

        guestUser = User.createGuest("Khách #1234");
        guestUser.setId(2L);

        publicRoom = ChatRoom.create("Phòng Chung", "Mô tả", 1L, 60, 1000, "public");
        publicRoom.setId(10L);

        privateRoom = ChatRoom.create("Phòng Riêng", "Mô tả", 1L, 60, 1000, "private");
        privateRoom.setId(20L);

        lenient().when(mediaAssetRepository.updateUploadIdAndMarkUploading(anyLong(), anyString(), any(Instant.class))).thenReturn(1);
        lenient().when(mediaAssetRepository.updateStatusToProcessing(anyLong(), anyString(), any(Instant.class))).thenReturn(1);
    }

    private PresignedUrlResponse presignedPut(String url) {
        return new PresignedUrlResponse(url, "PUT", Map.of("x-amz-custom", "val"), Instant.now().plusSeconds(900));
    }

    private PartSummaryResponse partSummary(int partNumber, String etag, long sizeBytes) {
        return new PartSummaryResponse(partNumber, etag, sizeBytes, Instant.now());
    }

    private ObjectMetadataResponse metadata(long contentLength, String contentType) {
        return new ObjectMetadataResponse(contentLength, contentType, "etag-xyz");
    }

    private byte[] buildFtypBox(String majorBrand, String... compatibleBrands) {
        int length = 8 + 4 + compatibleBrands.length * 4;
        byte[] box = new byte[length];
        box[0] = (byte) ((length >> 24) & 0xFF);
        box[1] = (byte) ((length >> 16) & 0xFF);
        box[2] = (byte) ((length >> 8) & 0xFF);
        box[3] = (byte) (length & 0xFF);
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, box, 4, 4);
        System.arraycopy(majorBrand.getBytes(StandardCharsets.US_ASCII), 0, box, 8, 4);
        for (int i = 0; i < compatibleBrands.length; i++) {
            System.arraycopy(compatibleBrands[i].getBytes(StandardCharsets.US_ASCII), 0, box, 12 + i * 4, 4);
        }
        return box;
    }

    // =========================================================================
    // 1. NHÓM KIỂM THỬ: initUpload
    // =========================================================================
    @Nested
    @DisplayName("1. Kiểm thử khởi tạo phiên tải lên (initUpload)")
    class InitUploadTests {

        @Test
        @DisplayName("Tệp nhỏ (<= 5MB) khởi tạo thành công ở chế độ SINGLE PUT")
        void singlePut_success() {
            UUID clientUploadId = UUID.randomUUID();
            long sizeBytes = 3 * 1024 * 1024L; // 3 MiB
            InitUploadRequest request = new InitUploadRequest(clientUploadId, 10L, "image/jpeg", sizeBytes, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);
            when(mediaAssetRepository.findByUploaderIdAndClientUploadId(1L, clientUploadId)).thenReturn(Optional.empty());
            when(mediaAssetRepository.countInitSince(eq(1L), any(Instant.class))).thenReturn(0L);
            when(mediaAssetRepository.countOpenUploads(eq(1L), any(Instant.class))).thenReturn(0L);

            when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
                MediaAsset saved = invocation.getArgument(0);
                saved.setId(100L);
                saved.setCreatedAt(Instant.now());
                return saved;
            });

            when(objectStorage.presignSinglePut(anyString(), eq("image/jpeg"), eq(sizeBytes), any(Duration.class)))
                    .thenReturn(presignedPut("http://s3.local/put-url"));

            InitUploadResponse response = service.initUpload(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.mediaAssetId()).isEqualTo(100L);
            assertThat(response.uploadMode()).isEqualTo("SINGLE");
            assertThat(response.totalParts()).isEqualTo(1);
            assertThat(response.uploadTargets()).hasSize(1);
            assertThat(response.uploadTargets().get(0).partNumber()).isEqualTo(1);
            assertThat(response.uploadTargets().get(0).url()).isEqualTo("http://s3.local/put-url");

            verify(mediaAssetRepository).acquireUserAdvisoryLock(1L);
            verify(objectStorage, never()).createMultipartUpload(anyString(), anyString());
        }

        @Test
        @DisplayName("Tệp lớn (> 5MB) khởi tạo thành công ở chế độ MULTIPART và tính chính xác partLength mảnh cuối")
        void multipart_success_exactLastPartLength() {
            UUID clientUploadId = UUID.randomUUID();
            long sizeBytes = 12 * 1024 * 1024L; // 12 MiB -> 3 parts (5 + 5 + 2 MiB)
            InitUploadRequest request = new InitUploadRequest(clientUploadId, 10L, "video/mp4", sizeBytes, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);
            when(mediaAssetRepository.findByUploaderIdAndClientUploadId(1L, clientUploadId)).thenReturn(Optional.empty());
            when(mediaAssetRepository.countInitSince(eq(1L), any(Instant.class))).thenReturn(0L);
            when(mediaAssetRepository.countOpenUploads(eq(1L), any(Instant.class))).thenReturn(0L);

            when(objectStorage.createMultipartUpload(anyString(), eq("video/mp4"))).thenReturn("upload-mp-999");
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
                MediaAsset saved = invocation.getArgument(0);
                saved.setId(101L);
                saved.setCreatedAt(Instant.now());
                return saved;
            });

            when(objectStorage.presignUploadPart(anyString(), eq("upload-mp-999"), anyInt(), anyLong(), any(Duration.class)))
                    .thenAnswer(inv -> presignedPut("http://s3.local/part/" + inv.getArgument(2)));

            InitUploadResponse response = service.initUpload(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.mediaAssetId()).isEqualTo(101L);
            assertThat(response.uploadMode()).isEqualTo("MULTIPART");
            assertThat(response.totalParts()).isEqualTo(3);
            assertThat(response.chunkSizeBytes()).isEqualTo(5 * 1024 * 1024L);
            assertThat(response.uploadTargets()).hasSize(3);

            // Kiểm tra cập nhật uploadId và markUploading
            verify(mediaAssetRepository).updateUploadIdAndMarkUploading(eq(101L), eq("upload-mp-999"), any(Instant.class));

            // Kiểm tra chính xác partLength mảnh cuối: 12MB - 10MB = 2MB (2,097,152 bytes)
            verify(objectStorage).presignUploadPart(anyString(), eq("upload-mp-999"), eq(1), eq(5 * 1024 * 1024L), any(Duration.class));
            verify(objectStorage).presignUploadPart(anyString(), eq("upload-mp-999"), eq(2), eq(5 * 1024 * 1024L), any(Duration.class));
            verify(objectStorage).presignUploadPart(anyString(), eq("upload-mp-999"), eq(3), eq(2 * 1024 * 1024L), any(Duration.class));
        }

        @Test
        @DisplayName("Kiểm tra biên: Đúng 5 MiB dùng SINGLE PUT; 5 MiB + 1 byte dùng MULTIPART")
        void boundary_5MiB_vs_5MiBPlus1() {
            // Đúng 5 MiB
            long exact5MiB = 5 * 1024 * 1024L;
            InitUploadRequest reqExact = new InitUploadRequest(UUID.randomUUID(), 10L, "image/jpeg", exact5MiB, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(inv -> {
                MediaAsset a = inv.getArgument(0);
                a.setId(10L);
                a.setCreatedAt(Instant.now());
                return a;
            });
            when(objectStorage.presignSinglePut(anyString(), anyString(), eq(exact5MiB), any(Duration.class)))
                    .thenReturn(presignedPut("http://s3/single"));

            InitUploadResponse resExact = service.initUpload(1L, reqExact);
            assertThat(resExact.uploadMode()).isEqualTo("SINGLE");
            assertThat(resExact.totalParts()).isEqualTo(1);

            // 5 MiB + 1 byte
            long plusOne = exact5MiB + 1;
            InitUploadRequest reqPlusOne = new InitUploadRequest(UUID.randomUUID(), 10L, "image/jpeg", plusOne, false);
            when(objectStorage.createMultipartUpload(anyString(), anyString())).thenReturn("upload-boundary");
            when(objectStorage.presignUploadPart(anyString(), eq("upload-boundary"), anyInt(), anyLong(), any(Duration.class)))
                    .thenReturn(presignedPut("http://s3/part"));

            InitUploadResponse resPlusOne = service.initUpload(1L, reqPlusOne);
            assertThat(resPlusOne.uploadMode()).isEqualTo("MULTIPART");
            assertThat(resPlusOne.totalParts()).isEqualTo(2);
        }

        @Test
        @DisplayName("Khách (GUEST) tải tệp trong phòng riêng tư bị chặn")
        void guestInPrivateRoom_forbidden() {
            InitUploadRequest request = new InitUploadRequest(UUID.randomUUID(), 20L, "image/jpeg", 1024L, false);

            when(userRepository.findById(2L)).thenReturn(Optional.of(guestUser));
            when(roomAccessService.requireActiveRoom(20L)).thenReturn(privateRoom);

            assertThatThrownBy(() -> service.initUpload(2L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_REQUIRED_TO_CHAT);
        }

        @Test
        @DisplayName("Khách (GUEST) tải video bị chặn")
        void guestUploadVideo_forbidden() {
            InitUploadRequest request = new InitUploadRequest(UUID.randomUUID(), 10L, "video/mp4", 1024L, false);

            when(userRepository.findById(2L)).thenReturn(Optional.of(guestUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);

            assertThatThrownBy(() -> service.initUpload(2L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        @Test
        @DisplayName("Khách (GUEST) bật chế độ HD bị chặn")
        void guestRequestHD_forbidden() {
            InitUploadRequest request = new InitUploadRequest(UUID.randomUUID(), 10L, "image/jpeg", 1024L, true);

            when(userRepository.findById(2L)).thenReturn(Optional.of(guestUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);

            assertThatThrownBy(() -> service.initUpload(2L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("Tải video bật chế độ HD bị chặn vì HD chỉ dành cho ảnh")
        void videoRequestHD_forbidden() {
            InitUploadRequest request = new InitUploadRequest(UUID.randomUUID(), 10L, "video/mp4", 1024L, true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);

            assertThatThrownBy(() -> service.initUpload(1L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("MIME không thuộc Whitelist bị từ chối ngay ở bước khởi tạo")
        void unsupportedDeclaredMime_rejected() {
            InitUploadRequest request = new InitUploadRequest(UUID.randomUUID(), 10L, "image/bmp", 1024L, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);

            assertThatThrownBy(() -> service.initUpload(1L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        @Test
        @DisplayName("Video vượt trần dung lượng 50MB trong phòng công khai bị từ chối")
        void videoExceedsPublicLimit_throwsMediaTooLarge() {
            long oversized = 51 * 1024 * 1024L; // 51 MB > 50 MB
            InitUploadRequest request = new InitUploadRequest(UUID.randomUUID(), 10L, "video/mp4", oversized, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);

            assertThatThrownBy(() -> service.initUpload(1L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_TOO_LARGE);
        }

        @Test
        @DisplayName("Video vượt trần dung lượng 100MB trong phòng riêng tư bị từ chối")
        void videoExceedsPrivateLimit_throwsMediaTooLarge() {
            long oversized = 101 * 1024 * 1024L; // 101 MB > 100 MB
            InitUploadRequest request = new InitUploadRequest(UUID.randomUUID(), 20L, "video/mp4", oversized, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(20L)).thenReturn(privateRoom);

            assertThatThrownBy(() -> service.initUpload(1L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_TOO_LARGE);
        }

        @Test
        @DisplayName("Kiểm tra riêng Rate-limit maxInitPerMinute và maxOpenUploads")
        void rateLimits_distinctChecks() {
            InitUploadRequest request = new InitUploadRequest(UUID.randomUUID(), 10L, "image/jpeg", 1024L, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);

            // 1. Quá maxInitPerMinute (30 req/min)
            when(mediaAssetRepository.countInitSince(eq(1L), any(Instant.class))).thenReturn(30L);
            assertThatThrownBy(() -> service.initUpload(1L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_RATE_LIMITED);

            // 2. Chưa quá maxInitPerMinute nhưng quá maxOpenUploads (10 open uploads)
            when(mediaAssetRepository.countInitSince(eq(1L), any(Instant.class))).thenReturn(5L);
            when(mediaAssetRepository.countOpenUploads(eq(1L), any(Instant.class))).thenReturn(10L);
            assertThatThrownBy(() -> service.initUpload(1L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_RATE_LIMITED);
        }

        @Test
        @DisplayName("Hết hạn mức HD trong ngày tại phòng công khai bị từ chối")
        void hdQuotaExceeded_publicRoom() {
            InitUploadRequest request = new InitUploadRequest(UUID.randomUUID(), 10L, "image/jpeg", 1024L, true);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);
            when(mediaAssetRepository.countInitSince(eq(1L), any(Instant.class))).thenReturn(0L);
            when(mediaAssetRepository.countOpenUploads(eq(1L), any(Instant.class))).thenReturn(0L);
            when(mediaAssetRepository.countHDInDay(eq(1L), any(Instant.class))).thenReturn(5L); // Max 5

            assertThatThrownBy(() -> service.initUpload(1L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HD_QUOTA_EXCEEDED);
        }

        @Test
        @DisplayName("Idempotency: Khi đã chạm trần rate-limit nhưng gửi lại cùng clientUploadId thì vẫn thành công")
        void idempotency_whenQuotaExceeded_stillReturnsExistingAsset() {
            UUID clientUploadId = UUID.randomUUID();
            InitUploadRequest request = new InitUploadRequest(clientUploadId, 10L, "image/jpeg", 2048L, false);

            MediaAsset existingAsset = MediaAsset.singleUpload(
                    1L, 10L, clientUploadId, MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 2048L, "raw/2026/11/test.jpg"
            );
            existingAsset.setId(555L);
            existingAsset.setCreatedAt(Instant.now());

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);
            when(mediaAssetRepository.findByUploaderIdAndClientUploadId(1L, clientUploadId)).thenReturn(Optional.of(existingAsset));
            when(objectStorage.presignSinglePut(anyString(), anyString(), anyLong(), any(Duration.class)))
                    .thenReturn(presignedPut("http://s3.local/re-signed"));

            // Dù không mock countInitSince / countOpenUploads (nếu gọi sẽ NPE), test vẫn pass vì Idempotency được check trước!
            InitUploadResponse response = service.initUpload(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.mediaAssetId()).isEqualTo(555L);
            assertThat(response.uploadTargets().get(0).url()).isEqualTo("http://s3.local/re-signed");
            verify(mediaAssetRepository, never()).save(any(MediaAsset.class));
        }

        @Test
        @DisplayName("Idempotency theo trạng thái: AVAILABLE trả không kèm URL; BLOCKED và CREATING ném lỗi phù hợp")
        void idempotency_statusBranches() {
            UUID clientUploadId = UUID.randomUUID();
            InitUploadRequest request = new InitUploadRequest(clientUploadId, 10L, "image/jpeg", 2048L, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);

            // 1. AVAILABLE: trả response với uploadTargets rỗng
            MediaAsset availAsset = MediaAsset.singleUpload(1L, 10L, clientUploadId, MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 2048L, "raw/test.jpg");
            availAsset.setId(1L);
            availAsset.setCreatedAt(Instant.now());
            availAsset.markProcessing("image/jpeg");
            availAsset.markAvailable("disp", "thumb", 100, 100, null);

            when(mediaAssetRepository.findByUploaderIdAndClientUploadId(1L, clientUploadId)).thenReturn(Optional.of(availAsset));
            InitUploadResponse resAvail = service.initUpload(1L, request);
            assertThat(resAvail.status()).isEqualTo(MediaStatus.AVAILABLE);
            assertThat(resAvail.uploadTargets()).isEmpty();

            // 2. BLOCKED: ném INVALID_MEDIA_SIGNATURE
            availAsset.markBlocked("Signature mismatch");
            assertThatThrownBy(() -> service.initUpload(1L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_MEDIA_SIGNATURE);

            // 3. CREATING: ném UPLOAD_IN_PROGRESS
            MediaAsset creatingAsset = MediaAsset.creating(1L, 10L, clientUploadId, MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 2048L, "raw/test.jpg");
            creatingAsset.setId(2L);
            creatingAsset.setCreatedAt(Instant.now());
            when(mediaAssetRepository.findByUploaderIdAndClientUploadId(1L, clientUploadId)).thenReturn(Optional.of(creatingAsset));
            assertThatThrownBy(() -> service.initUpload(1L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_IN_PROGRESS);
        }

        @Test
        @DisplayName("initUpload khi updateUploadIdAndMarkUploading trả về 0: gọi abortMultipartUpload và ném MEDIA_DELETED")
        void initUpload_concurrentAbortDuringCreating_abortsMultipartAndThrowsMediaDeleted() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);
            when(mediaAssetRepository.acquireUserAdvisoryLock(1L)).thenReturn(1);
            when(mediaAssetRepository.findByUploaderIdAndClientUploadId(eq(1L), any(UUID.class))).thenReturn(Optional.empty());
            when(mediaAssetRepository.countInitSince(eq(1L), any(Instant.class))).thenReturn(0L);
            when(mediaAssetRepository.countOpenUploads(eq(1L), any(Instant.class))).thenReturn(0L);

            MediaAsset saved = MediaAsset.creating(1L, 10L, UUID.randomUUID(), MediaKind.VIDEO, MediaQuality.STANDARD, "video/mp4", 10 * 1024 * 1024L, "raw/test.mp4");
            saved.setId(122L);
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenReturn(saved);

            when(objectStorage.createMultipartUpload(anyString(), anyString())).thenReturn("upload-leak-check");
            // Mô phỏng tranh chấp: updateUploadIdAndMarkUploading trả về 0 (đã bị abort trước đó)
            when(mediaAssetRepository.updateUploadIdAndMarkUploading(eq(122L), eq("upload-leak-check"), any(Instant.class))).thenReturn(0);

            InitUploadRequest req = new InitUploadRequest(UUID.randomUUID(), 10L, "video/mp4", 10 * 1024 * 1024L, false);

            assertThatThrownBy(() -> service.initUpload(1L, req))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_DELETED);

            verify(objectStorage).abortMultipartUpload(anyString(), eq("upload-leak-check"));
        }

        @Test
        @DisplayName("initUpload gọi lại clientUploadId cũ nhưng sai roomId/sizeBytes/kind: ném VALIDATION_ERROR")
        void initUpload_idempotencyParameterMismatch_throwsValidationError() {
            UUID clientId = UUID.randomUUID();
            MediaAsset existing = MediaAsset.singleUpload(1L, 10L, clientId, MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/old.jpg");
            existing.setId(125L);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);
            when(mediaAssetRepository.acquireUserAdvisoryLock(1L)).thenReturn(1);
            when(mediaAssetRepository.findByUploaderIdAndClientUploadId(1L, clientId)).thenReturn(Optional.of(existing));

            // Request với dung lượng khác: 2048 thay vì 1024
            InitUploadRequest reqMismatch = new InitUploadRequest(clientId, 10L, "image/jpeg", 2048L, false);

            assertThatThrownBy(() -> service.initUpload(1L, reqMismatch))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

            // Request với quality khác: hd = true thay vì false
            InitUploadRequest reqQualityMismatch = new InitUploadRequest(clientId, 10L, "image/jpeg", 1024L, true);

            assertThatThrownBy(() -> service.initUpload(1L, reqQualityMismatch))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("Người dùng không phải thành viên phòng bị chặn khởi tạo (initUpload) trước khi đụng DB hay S3")
        void notActiveMember_initUpload_throwsForbidden() {
            InitUploadRequest request = new InitUploadRequest(UUID.randomUUID(), 10L, "image/jpeg", 1024L, false);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);
            doThrow(new AppException(ErrorCode.NOT_ROOM_MEMBER, "Không phải thành viên")).when(roomAccessService).requireActiveMember(1L, 10L);

            assertThatThrownBy(() -> service.initUpload(1L, request))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ROOM_MEMBER);

            verify(mediaAssetRepository, never()).acquireUserAdvisoryLock(anyLong());
            verify(mediaAssetRepository, never()).save(any(MediaAsset.class));
            verify(objectStorage, never()).presignSinglePut(anyString(), anyString(), anyLong(), any(Duration.class));
        }
    }

    // =========================================================================
    // 2. NHÓM KIỂM THỬ: getUploadStatus (Xử lý mất mạng / Resume)
    // =========================================================================
    @Nested
    @DisplayName("2. Kiểm thử tra cứu trạng thái phiên tải lên & Hỗ trợ Resume (getUploadStatus)")
    class GetUploadStatusTests {

        @Test
        @DisplayName("Không tìm thấy tệp hoặc không phải chủ sở hữu ném MEDIA_NOT_FOUND")
        void notFoundOrNotOwner_throwsMediaNotFound() {
            when(mediaAssetRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getUploadStatus(1L, 999L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND);

            MediaAsset otherUserAsset = MediaAsset.singleUpload(
                    2L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/test.jpg"
            );
            otherUserAsset.setId(888L);
            when(mediaAssetRepository.findById(888L)).thenReturn(Optional.of(otherUserAsset));

            assertThatThrownBy(() -> service.getUploadStatus(1L, 888L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND);
        }

        @Test
        @DisplayName("Phiên tải lên quá hạn 24 giờ ném HTTP 410 UPLOAD_EXPIRED")
        void expiredSession_throwsUploadExpired() {
            MediaAsset asset = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/test.jpg"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now().minus(25, ChronoUnit.HOURS)); // 25 hours ago

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.getUploadStatus(1L, 100L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_EXPIRED);
        }

        @Test
        @DisplayName("Tệp đã bị hủy hoặc xóa ném HTTP 410 MEDIA_DELETED")
        void failedOrDeleted_throwsMediaDeleted() {
            MediaAsset asset = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/test.jpg"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());
            asset.markFailed("User cancelled");

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.getUploadStatus(1L, 100L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_DELETED);
        }

        @Test
        @DisplayName("Tệp bị chặn do vi phạm an toàn ném HTTP 422 INVALID_MEDIA_SIGNATURE")
        void blocked_throwsInvalidMediaSignature() {
            MediaAsset asset = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/test.jpg"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());
            asset.markBlocked("Invalid header");

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.getUploadStatus(1L, 100L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_MEDIA_SIGNATURE);
        }

        @Test
        @DisplayName("Tệp đang trong quá trình khởi tạo (CREATING) hoặc ghép (COMPLETING) ném HTTP 409 UPLOAD_IN_PROGRESS")
        void creatingOrCompleting_throwsUploadInProgress() {
            MediaAsset asset = MediaAsset.creating(
                    1L, 10L, UUID.randomUUID(), MediaKind.VIDEO, MediaQuality.STANDARD,
                    "video/mp4", 10000000L, "raw/test.mp4"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.getUploadStatus(1L, 100L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_IN_PROGRESS);

            asset.markCompleting();
            assertThatThrownBy(() -> service.getUploadStatus(1L, 100L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_IN_PROGRESS);
        }

        @Test
        @DisplayName("Tệp đã sẵn sàng (AVAILABLE) trả trạng thái thành công, không còn URL cần tải")
        void available_returnsSuccessWithoutTargets() {
            MediaAsset asset = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/test.jpg"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());
            asset.markProcessing("image/jpeg");
            asset.markAvailable("display/test.webp", "thumb/test.webp", 1920, 1080, null);

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));

            UploadStatusResponse response = service.getUploadStatus(1L, 100L);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(MediaStatus.AVAILABLE);
            assertThat(response.remainingUploadTargets()).isEmpty();
        }

        @Test
        @DisplayName("Trường hợp Single PUT còn hạn: Ký lại 1 URL PUT để tải lại")
        void uploading_singlePut_reSignsUrl() {
            MediaAsset asset = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 2048L, "raw/test.jpg"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));
            when(objectStorage.presignSinglePut(anyString(), anyString(), anyLong(), any(Duration.class)))
                    .thenReturn(presignedPut("http://s3.local/put-resume"));

            UploadStatusResponse response = service.getUploadStatus(1L, 100L);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(MediaStatus.UPLOADING);
            assertThat(response.uploadMode()).isEqualTo("SINGLE");
            assertThat(response.remainingUploadTargets()).hasSize(1);
            assertThat(response.remainingUploadTargets().get(0).url()).isEqualTo("http://s3.local/put-resume");
        }

        @Test
        @DisplayName("Trường hợp Multipart rớt mạng: Đối chiếu listParts và chỉ ký URL các mảnh còn thiếu (Resume)")
        void uploading_multipart_resumeMissingParts() {
            long sizeBytes = 15 * 1024 * 1024L; // 15 MiB -> 3 parts
            MediaAsset asset = MediaAsset.multipartUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.VIDEO, MediaQuality.STANDARD,
                    "video/mp4", sizeBytes, "raw/video.mp4", "upload-resume-123"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));

            // S3 đã nhận part 1 và part 3, thiếu part 2
            List<PartSummaryResponse> s3Parts = List.of(
                    partSummary(1, "etag-1", 5 * 1024 * 1024L),
                    partSummary(3, "etag-3", 5 * 1024 * 1024L)
            );
            when(objectStorage.listParts("raw/video.mp4", "upload-resume-123")).thenReturn(s3Parts);

            when(objectStorage.presignUploadPart(eq("raw/video.mp4"), eq("upload-resume-123"), eq(2), anyLong(), any(Duration.class)))
                    .thenReturn(presignedPut("http://s3.local/part-2-resume"));

            UploadStatusResponse response = service.getUploadStatus(1L, 100L);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(MediaStatus.UPLOADING);
            assertThat(response.uploadMode()).isEqualTo("MULTIPART");
            assertThat(response.totalParts()).isEqualTo(3);
            assertThat(response.completedParts()).hasSize(2);
            assertThat(response.completedParts().stream().map(CompletedPartSummaryDto::partNumber).toList()).containsExactly(1, 3);
            // Chỉ còn part 2 cần tải bù!
            assertThat(response.remainingUploadTargets()).hasSize(1);
            assertThat(response.remainingUploadTargets().get(0).partNumber()).isEqualTo(2);
            assertThat(response.remainingUploadTargets().get(0).url()).isEqualTo("http://s3.local/part-2-resume");
        }

        @Test
        @DisplayName("Khi listParts ném NoSuchUploadStorageException nhưng headObject tồn tại file và DB vẫn UPLOADING: trả về UPLOADING với remainingTargets rỗng")
        void getUploadStatus_noSuchUpload_fileInHead_returnsUploadingWithEmptyRemainingTargets() {
            MediaAsset asset = MediaAsset.multipartUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.VIDEO, MediaQuality.STANDARD,
                    "video/mp4", 10 * 1024 * 1024L, "raw/test-head.mp4", "upload-ns-1"
            );
            asset.setId(121L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(121L)).thenReturn(Optional.of(asset));
            when(objectStorage.listParts("raw/test-head.mp4", "upload-ns-1"))
                    .thenThrow(new NoSuchUploadStorageException("Upload merged"));
            when(objectStorage.head("raw/test-head.mp4"))
                    .thenReturn(Optional.of(metadata(10 * 1024 * 1024L, "video/mp4")));

            UploadStatusResponse response = service.getUploadStatus(1L, 121L);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(MediaStatus.UPLOADING);
            assertThat(response.remainingUploadTargets()).isEmpty();
        }

        @Test
        @DisplayName("getUploadStatus với tệp AVAILABLE tạo cách đây 48 giờ: trả về AVAILABLE bình thường, không ném UPLOAD_EXPIRED")
        void getUploadStatus_availableFileCreated48HoursAgo_returnsAvailable() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/old.jpg");
            asset.setId(123L);
            asset.setCreatedAt(Instant.now().minus(Duration.ofHours(48)));
            asset.markAvailable("display/old.jpg", "thumb/old.jpg", 800, 600);

            when(mediaAssetRepository.findById(123L)).thenReturn(Optional.of(asset));

            UploadStatusResponse response = service.getUploadStatus(1L, 123L);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(MediaStatus.AVAILABLE);
        }
    }

    // =========================================================================
    // 3. NHÓM KIỂM THỬ: completeUpload
    // =========================================================================
    @Nested
    @DisplayName("3. Kiểm thử hoàn tất ghép mảnh và kiểm chứng chữ ký tệp (completeUpload)")
    class CompleteUploadTests {

        @Test
        @DisplayName("Người dùng không phải thành viên phòng chat bị chặn hoàn tất tệp")
        void notActiveMember_throwsForbidden() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));
            doThrow(new AppException(ErrorCode.NOT_ROOM_MEMBER, "Không phải thành viên")).when(roomAccessService).requireActiveMember(1L, 10L);

            assertThatThrownBy(() -> service.completeUpload(1L, 100L, new CompleteUploadRequest(List.of())))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ROOM_MEMBER);
        }

        @Test
        @DisplayName("Tệp đã ở trạng thái PROCESSING hoặc AVAILABLE trả kết quả thành công ngay (Idempotent)")
        void alreadyCompleted_returnsSuccessIdempotently() {
            MediaAsset asset = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/test.jpg"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());
            asset.markProcessing("image/jpeg");

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));

            CompleteUploadResponse response = service.completeUpload(1L, 100L, new CompleteUploadRequest(List.of()));

            assertThat(response).isNotNull();
            assertThat(response.mediaAssetId()).isEqualTo(100L);
            assertThat(response.status()).isEqualTo(MediaStatus.PROCESSING);
            verify(objectStorage, never()).head(anyString());
        }

        @Test
        @DisplayName("Tranh chấp hoàn tất đồng thời: Gặp request đang ghép dở ném HTTP 409 UPLOAD_IN_PROGRESS (không ghi đè mock)")
        void concurrentClaimFails_throwsUploadInProgress() {
            MediaAsset asset = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/test.jpg"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            MediaAsset refreshedCompleting = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/test.jpg"
            );
            refreshedCompleting.setId(100L);
            refreshedCompleting.setCreatedAt(Instant.now());
            refreshedCompleting.markCompleting();

            // Sửa lỗi ghi đè mock: chuỗi thenReturn(first, second)
            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset), Optional.of(refreshedCompleting));
            when(mediaAssetRepository.claimForCompleting(eq(100L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(0);

            assertThatThrownBy(() -> service.completeUpload(1L, 100L, new CompleteUploadRequest(List.of())))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_IN_PROGRESS);
        }

        @Test
        @DisplayName("Multipart thiếu mảnh hoặc rỗng: Hoàn lại UPLOADING và ném UPLOAD_INCOMPLETE")
        void multipart_missingParts_revertsToUploading() {
            long sizeBytes = 10 * 1024 * 1024L; // 10 MiB -> 2 parts
            MediaAsset asset = MediaAsset.multipartUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.VIDEO, MediaQuality.STANDARD,
                    "video/mp4", sizeBytes, "raw/video.mp4", "upload-inc-123"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForCompleting(eq(100L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);

            // Client gửi thiếu: chỉ gửi 1/2 mảnh
            CompleteUploadRequest req = new CompleteUploadRequest(List.of(new CompletedPartDto(1, "etag-1")));

            assertThatThrownBy(() -> service.completeUpload(1L, 100L, req))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_INCOMPLETE);

            verify(mediaAssetRepository).revertStatusToUploading(eq(100L), any(Instant.class));
        }

        @Test
        @DisplayName("Multipart thừa mảnh: Ném UPLOAD_INCOMPLETE")
        void multipart_extraParts_throwsUploadIncomplete() {
            long sizeBytes = 10 * 1024 * 1024L; // 10 MiB -> 2 parts
            MediaAsset asset = MediaAsset.multipartUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.VIDEO, MediaQuality.STANDARD,
                    "video/mp4", sizeBytes, "raw/video.mp4", "upload-extra-123"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForCompleting(eq(100L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);

            // Có 3 mảnh thay vì 2
            when(objectStorage.listParts("raw/video.mp4", "upload-extra-123"))
                    .thenReturn(List.of(
                            partSummary(1, "etag-1", 5 * 1024 * 1024L),
                            partSummary(2, "etag-2", 5 * 1024 * 1024L),
                            partSummary(3, "etag-3", 1024L)
                    ));

            CompleteUploadRequest req = new CompleteUploadRequest(List.of(
                    new CompletedPartDto(1, "etag-1"),
                    new CompletedPartDto(2, "etag-2")
            ));

            assertThatThrownBy(() -> service.completeUpload(1L, 100L, req))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_INCOMPLETE);

            verify(mediaAssetRepository).revertStatusToUploading(eq(100L), any(Instant.class));
        }

        @Test
        @DisplayName("Multipart ETag client gửi lệch với S3: Chặn BLOCKED và ném UPLOAD_SIZE_MISMATCH")
        void multipart_etagMismatch_blocksAsset() {
            long sizeBytes = 10 * 1024 * 1024L; // 10 MiB -> 2 parts
            MediaAsset asset = MediaAsset.multipartUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.VIDEO, MediaQuality.STANDARD,
                    "video/mp4", sizeBytes, "raw/video.mp4", "upload-etag-mismatch"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForCompleting(eq(100L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);

            when(objectStorage.listParts("raw/video.mp4", "upload-etag-mismatch"))
                    .thenReturn(List.of(
                            partSummary(1, "s3-etag-1", 5 * 1024 * 1024L),
                            partSummary(2, "s3-etag-2", 5 * 1024 * 1024L)
                    ));

            // Client gửi lệch ETag part 1
            CompleteUploadRequest req = new CompleteUploadRequest(List.of(
                    new CompletedPartDto(1, "fake-client-etag"),
                    new CompletedPartDto(2, "s3-etag-2")
            ));

            assertThatThrownBy(() -> service.completeUpload(1L, 100L, req))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_SIZE_MISMATCH);

            verify(mediaAssetRepository).updateStatusToBlocked(eq(100L), anyString(), any(Instant.class));
            verify(objectStorage).abortMultipartUpload("raw/video.mp4", "upload-etag-mismatch");
        }

        @Test
        @DisplayName("Multipart khôi phục an toàn khi S3 báo NoSuchUpload nhưng file đã tồn tại trên storage")
        void completeUpload_recoverWhenNoSuchUpload_fileAlreadyExists() {
            byte[] mp4Header = buildFtypBox("isom", "mp41");
            long sizeBytes = 10 * 1024 * 1024L;

            MediaAsset asset = MediaAsset.multipartUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.VIDEO, MediaQuality.STANDARD,
                    "video/mp4", sizeBytes, "raw/already-merged.mp4", "upload-merged-123"
            );
            asset.setId(105L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(105L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForCompleting(eq(105L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);

            // S3 quăng NoSuchUploadStorageException
            when(objectStorage.listParts("raw/already-merged.mp4", "upload-merged-123"))
                    .thenThrow(new NoSuchUploadStorageException("Upload already completed"));

            // Nhưng Head kiểm tra file đã tồn tại đúng size!
            when(objectStorage.head("raw/already-merged.mp4"))
                    .thenReturn(Optional.of(metadata(sizeBytes, "video/mp4")));
            when(objectStorage.readRange("raw/already-merged.mp4", 0, 8191)).thenReturn(mp4Header);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);

            CompleteUploadRequest req = new CompleteUploadRequest(List.of(
                    new CompletedPartDto(1, "etag1"),
                    new CompletedPartDto(2, "etag2")
            ));

            CompleteUploadResponse response = service.completeUpload(1L, 105L, req);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(MediaStatus.PROCESSING);
            verify(mediaAssetRepository).updateStatusToProcessing(eq(105L), eq("video/mp4"), any(Instant.class));
        }

        @Test
        @DisplayName("Lỗi storage không lọt chi tiết exception nội bộ ra client")
        void completeUpload_storageError_doesNotLeakInternalDetails() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForCompleting(eq(100L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);

            // Storage quăng RuntimeException chứa thông tin nhạy cảm
            when(objectStorage.head("raw/test.jpg")).thenThrow(new RuntimeException("SECRET_AWS_KEY_LEAK_IN_LOG"));

            assertThatThrownBy(() -> service.completeUpload(1L, 100L, new CompleteUploadRequest(List.of())))
                    .isInstanceOf(AppException.class)
                    .hasMessage("Lỗi dịch vụ lưu trữ khi hoàn tất tệp") // Message chuẩn, không chứa SECRET_AWS_KEY
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORAGE_ERROR);

            verify(mediaAssetRepository).revertStatusToUploading(eq(100L), any(Instant.class));
        }

        @Test
        @DisplayName("Dung lượng thực tế trên storage lệch so với khai báo: Chặn và ném UPLOAD_SIZE_MISMATCH")
        void headSizeMismatch_blocksAsset() {
            MediaAsset asset = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 2048L, "raw/test.jpg"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForCompleting(eq(100L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);

            when(objectStorage.head("raw/test.jpg"))
                    .thenReturn(Optional.of(metadata(5000L, "image/jpeg")));

            assertThatThrownBy(() -> service.completeUpload(1L, 100L, new CompleteUploadRequest(List.of())))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_SIZE_MISMATCH);

            verify(mediaAssetRepository).updateStatusToBlocked(eq(100L), anyString(), any(Instant.class));
            verify(objectStorage).delete("raw/test.jpg");
        }

        @Test
        @DisplayName("File giả mạo định dạng (Magic Bytes không hợp lệ): Chặn và ném INVALID_MEDIA_SIGNATURE")
        void invalidMagicBytes_blocksAsset() {
            MediaAsset asset = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/test.jpg"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForCompleting(eq(100L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);

            when(objectStorage.head("raw/test.jpg"))
                    .thenReturn(Optional.of(metadata(1024L, "image/jpeg")));
            when(objectStorage.readRange("raw/test.jpg", 0, 8191)).thenReturn(RANDOM_BYTES);

            assertThatThrownBy(() -> service.completeUpload(1L, 100L, new CompleteUploadRequest(List.of())))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_MEDIA_SIGNATURE);

            verify(mediaAssetRepository).updateStatusToBlocked(eq(100L), anyString(), any(Instant.class));
            verify(objectStorage).delete("raw/test.jpg");
        }

        @Test
        @DisplayName("Tải lên ảnh JPEG hợp lệ: Xác thực thành công và chuyển trạng thái sang PROCESSING")
        void validJpeg_transitionsToProcessing() {
            long size = VALID_JPEG_HEADER.length;
            MediaAsset asset = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", size, "raw/valid.jpg"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForCompleting(eq(100L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);

            when(objectStorage.head("raw/valid.jpg"))
                    .thenReturn(Optional.of(metadata(size, "image/jpeg")));
            when(objectStorage.readRange("raw/valid.jpg", 0, 8191)).thenReturn(VALID_JPEG_HEADER);

            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);

            CompleteUploadResponse response = service.completeUpload(1L, 100L, new CompleteUploadRequest(List.of()));

            assertThat(response).isNotNull();
            assertThat(response.mediaAssetId()).isEqualTo(100L);
            assertThat(response.status()).isEqualTo(MediaStatus.PROCESSING);
            assertThat(response.kind()).isEqualTo(MediaKind.IMAGE);

            verify(mediaAssetRepository).updateStatusToProcessing(eq(100L), eq("image/jpeg"), any(Instant.class));
        }

        @Test
        @DisplayName("Xác nhận updateStatusToProcessing được gọi đúng tham số khi hoàn tất tệp")
        void completeUpload_invokesUpdateStatusToProcessing_withCorrectArguments() {
            long size = VALID_JPEG_HEADER.length;
            MediaAsset asset = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", size, "raw/valid-test.jpg"
            );
            asset.setId(101L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(101L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForCompleting(eq(101L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);
            when(objectStorage.head("raw/valid-test.jpg")).thenReturn(Optional.of(metadata(size, "image/jpeg")));
            when(objectStorage.readRange("raw/valid-test.jpg", 0, 8191)).thenReturn(VALID_JPEG_HEADER);
            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);

            CompleteUploadResponse response = service.completeUpload(1L, 101L, new CompleteUploadRequest(List.of()));

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(MediaStatus.PROCESSING);

            ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(mediaAssetRepository).updateStatusToProcessing(eq(101L), eq("image/jpeg"), nowCaptor.capture());
            assertThat(nowCaptor.getValue()).isNotNull();
            assertThat(nowCaptor.getValue()).isAfter(Instant.now().minusSeconds(10));
        }

        @Test
        @DisplayName("Lý do chặn (reason) khi blockAndCleanup dài hơn 500 ký tự được cắt ngắn an toàn <= 500 ký tự")
        void blockAndCleanup_longReason_truncatedTo500() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 2048L, "raw/test.jpg");
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForCompleting(eq(100L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);
            when(objectStorage.head("raw/test.jpg")).thenReturn(Optional.of(metadata(5000L, "image/jpeg")));

            assertThatThrownBy(() -> service.completeUpload(1L, 100L, new CompleteUploadRequest(List.of())))
                    .isInstanceOf(AppException.class);

            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            verify(mediaAssetRepository).updateStatusToBlocked(eq(100L), reasonCaptor.capture(), any(Instant.class));
            assertThat(reasonCaptor.getValue()).hasSizeLessThanOrEqualTo(500);
        }

        @Test
        @DisplayName("Lỗi tạo multipart trên S3 có message siêu dài (> 500 chars): markCreatingFailed tự động cắt ngắn <= 500 chars")
        void initUpload_s3ThrowsHugeErrorMessage_truncatedTo500() {
            String hugeErrorMessage = "X".repeat(1000);
            when(userRepository.findById(1L)).thenReturn(Optional.of(regularUser));
            when(roomAccessService.requireActiveRoom(10L)).thenReturn(publicRoom);
            when(mediaAssetRepository.acquireUserAdvisoryLock(1L)).thenReturn(1);
            when(mediaAssetRepository.findByUploaderIdAndClientUploadId(eq(1L), any(UUID.class))).thenReturn(Optional.empty());
            when(mediaAssetRepository.countInitSince(eq(1L), any(Instant.class))).thenReturn(0L);
            when(mediaAssetRepository.countOpenUploads(eq(1L), any(Instant.class))).thenReturn(0L);

            MediaAsset savedMock = MediaAsset.creating(1L, 10L, UUID.randomUUID(), MediaKind.VIDEO, MediaQuality.STANDARD, "video/mp4", 10 * 1024 * 1024L, "raw/test.mp4");
            savedMock.setId(999L);
            when(mediaAssetRepository.save(any(MediaAsset.class))).thenReturn(savedMock);

            when(objectStorage.createMultipartUpload(anyString(), anyString()))
                    .thenThrow(new RuntimeException(hugeErrorMessage));

            InitUploadRequest req = new InitUploadRequest(UUID.randomUUID(), 10L, "video/mp4", 10 * 1024 * 1024L, false);

            assertThatThrownBy(() -> service.initUpload(1L, req))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORAGE_ERROR);

            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            verify(mediaAssetRepository).markCreatingFailed(eq(999L), reasonCaptor.capture(), any(Instant.class));
            assertThat(reasonCaptor.getValue()).hasSizeLessThanOrEqualTo(500);
        }

        @Test
        @DisplayName("Khi storage ném AppException(STORAGE_ERROR) trong completeUpload: revertStatusToUploading bắt buộc được gọi")
        void completeUpload_storageAppException_triggersRevertToUploading() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test-app-err.jpg");
            asset.setId(120L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(120L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForCompleting(eq(120L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);
            when(objectStorage.head("raw/test-app-err.jpg")).thenThrow(new AppException(ErrorCode.STORAGE_ERROR, "S3 connection timeout"));

            assertThatThrownBy(() -> service.completeUpload(1L, 120L, new CompleteUploadRequest(List.of())))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORAGE_ERROR);

            verify(mediaAssetRepository).revertStatusToUploading(eq(120L), any(Instant.class));
        }

        @Test
        @DisplayName("completeUpload với tệp AVAILABLE tạo cách đây 48 giờ: trả về AVAILABLE idempotent, không ném UPLOAD_EXPIRED")
        void completeUpload_availableFileCreated48HoursAgo_returnsAvailable() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/old.jpg");
            asset.setId(124L);
            asset.setCreatedAt(Instant.now().minus(Duration.ofHours(48)));
            asset.markAvailable("display/old.jpg", "thumb/old.jpg", 800, 600);

            when(mediaAssetRepository.findById(124L)).thenReturn(Optional.of(asset));

            CompleteUploadResponse response = service.completeUpload(1L, 124L, new CompleteUploadRequest(List.of()));

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(MediaStatus.AVAILABLE);
        }

        @Test
        @DisplayName("completeUpload khi parts gửi lên không khớp expectedTotalParts hoặc có partNumber trùng: ném UPLOAD_INCOMPLETE và revertToUploading")
        void completeUpload_partsSizeOrDuplicateMismatch_revertsToUploading() {
            long sizeBytes = 10 * 1024 * 1024L; // 2 parts
            MediaAsset asset = MediaAsset.multipartUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.VIDEO, MediaQuality.STANDARD,
                    "video/mp4", sizeBytes, "raw/video.mp4", "upload-dup-1"
            );
            asset.setId(126L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(126L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForCompleting(eq(126L), eq(1L), any(Instant.class), any(Instant.class))).thenReturn(1);

            // Gửi 2 parts nhưng trùng partNumber = 1
            CompleteUploadRequest reqWithDup = new CompleteUploadRequest(List.of(
                    new CompletedPartDto(1, "etag-1"),
                    new CompletedPartDto(1, "etag-2")
            ));

            assertThatThrownBy(() -> service.completeUpload(1L, 126L, reqWithDup))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_INCOMPLETE);

            verify(mediaAssetRepository).revertStatusToUploading(eq(126L), any(Instant.class));
        }
    }

    // =========================================================================
    // 4. NHÓM KIỂM THỬ: abortUpload
    // =========================================================================
    @Nested
    @DisplayName("4. Kiểm thử hủy phiên tải lên (abortUpload)")
    class AbortUploadTests {

        @Test
        @DisplayName("Hủy phiên tải lên thành công: Dọn dẹp storage và cập nhật trạng thái FAILED bằng UPDATE")
        void abortUpload_success() {
            MediaAsset asset = MediaAsset.multipartUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.VIDEO, MediaQuality.STANDARD,
                    "video/mp4", 1024L, "raw/abort.mp4", "upload-abort-123"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForAbort(eq(100L), eq(1L), anyString(), any(Instant.class))).thenReturn(1);

            service.abortUpload(1L, 100L);

            verify(mediaAssetRepository).claimForAbort(eq(100L), eq(1L), anyString(), any(Instant.class));
            verify(objectStorage).abortMultipartUpload("raw/abort.mp4", "upload-abort-123");
            verify(objectStorage).delete("raw/abort.mp4");
        }

        @Test
        @DisplayName("Đang trong quá trình hoàn tất (COMPLETING) thì không thể hủy, ném UPLOAD_IN_PROGRESS")
        void abortUpload_completing_throwsUploadInProgress() {
            MediaAsset asset = MediaAsset.singleUpload(
                    1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/abort.jpg"
            );
            asset.setId(100L);
            asset.setCreatedAt(Instant.now());
            asset.markCompleting();

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.abortUpload(1L, 100L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_IN_PROGRESS);

            verify(objectStorage, never()).delete(anyString());
        }

        @Test
        @DisplayName("Không phải chủ sở hữu tệp không được hủy, ném MEDIA_NOT_FOUND")
        void abortUpload_notOwner_throwsMediaNotFound() {
            MediaAsset asset = MediaAsset.singleUpload(
                    2L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/other.jpg"
            );
            asset.setId(100L);

            when(mediaAssetRepository.findById(100L)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.abortUpload(1L, 100L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND);
        }

        @Test
        @DisplayName("Hủy phiên khi tệp đã là PURGED trả về thành công (idempotent)")
        void abortUpload_alreadyPurged_returnsIdempotently() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/purged.jpg");
            asset.setId(127L);
            asset.markPurged();

            when(mediaAssetRepository.findById(127L)).thenReturn(Optional.of(asset));

            service.abortUpload(1L, 127L);

            verify(mediaAssetRepository, never()).claimForAbort(anyLong(), anyLong(), anyString(), any(Instant.class));
            verify(objectStorage, never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("7. deleteAsset(userId, mediaAssetId) - Xóa tệp phương tiện")
    class DeleteAssetTests {

        @Test
        @DisplayName("Tệp không tồn tại -> ném MEDIA_NOT_FOUND (404)")
        void deleteAsset_notFound_throwsMediaNotFound() {
            when(mediaAssetRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteAsset(1L, 999L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND);
        }

        @Test
        @DisplayName("Tệp ở trạng thái UPLOADING: chủ sở hữu gọi -> tái sử dụng abortUpload thành công")
        void deleteAsset_uploading_uploader_callsAbortUpload() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(101L);

            when(mediaAssetRepository.findById(101L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.claimForAbort(eq(101L), eq(1L), anyString(), any(Instant.class))).thenReturn(1);

            service.deleteAsset(1L, 101L);

            verify(mediaAssetRepository).claimForAbort(eq(101L), eq(1L), eq("USER_ABORTED"), any(Instant.class));
            verify(objectStorage).delete("raw/test.jpg");
        }

        @Test
        @DisplayName("Tệp ở trạng thái UPLOADING: người khác gọi -> ném MEDIA_NOT_FOUND")
        void deleteAsset_uploading_notUploader_throwsMediaNotFound() {
            MediaAsset asset = MediaAsset.singleUpload(2L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(101L);

            when(mediaAssetRepository.findById(101L)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.deleteAsset(1L, 101L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND);
        }

        @Test
        @DisplayName("Tệp ở trạng thái COMPLETING: chủ sở hữu gọi -> ném UPLOAD_IN_PROGRESS (409)")
        void deleteAsset_completing_uploader_throwsUploadInProgress() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(102L);
            asset.markCompleting();

            when(mediaAssetRepository.findById(102L)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.deleteAsset(1L, 102L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_IN_PROGRESS);
        }

        @Test
        @DisplayName("Tệp ở trạng thái COMPLETING: thành viên hoạt động không phải uploader -> ném MEDIA_NOT_FOUND (404 chống lộ ID)")
        void deleteAsset_completing_activeMemberNotUploader_throwsMediaNotFound() {
            MediaAsset asset = MediaAsset.singleUpload(2L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(102L);
            asset.markCompleting();

            RoomMember member = RoomMember.create(10L, 1L, MemberRole.MEMBER, MemberStatus.ACTIVE);

            when(mediaAssetRepository.findById(102L)).thenReturn(Optional.of(asset));

            assertThatThrownBy(() -> service.deleteAsset(1L, 102L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND);
        }

        @Test
        @DisplayName("Tệp ở trạng thái COMPLETING: người ngoài phòng gọi -> ném MEDIA_NOT_FOUND (404)")
        void deleteAsset_completing_outsider_throwsMediaNotFound() {
            MediaAsset asset = MediaAsset.singleUpload(2L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(102L);
            asset.markCompleting();

            when(mediaAssetRepository.findById(102L)).thenReturn(Optional.of(asset));
            when(roomMemberRepository.findByRoomIdAndUserIdAndStatus(10L, 1L, MemberStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteAsset(1L, 102L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND);
        }

        @Test
        @DisplayName("Tệp đã DELETED: chủ sở hữu gọi -> return idempotent thành công")
        void deleteAsset_alreadyDeleted_uploader_returnsIdempotent() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(103L);
            asset.softDelete();

            when(mediaAssetRepository.findById(103L)).thenReturn(Optional.of(asset));

            service.deleteAsset(1L, 103L);

            verify(mediaAssetRepository, never()).markStatusToDeleted(anyLong(), any(Instant.class));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Tệp đã PURGED và đã gắn message: OWNER phòng gọi -> return idempotent thành công")
        void deleteAsset_alreadyPurged_roomOwnerWhenMessageAttached_returnsIdempotent() {
            MediaAsset asset = MediaAsset.singleUpload(2L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(104L);
            asset.attachToMessage(50L, 0);
            asset.markPurged();

            RoomMember owner = RoomMember.create(10L, 1L, MemberRole.OWNER, MemberStatus.ACTIVE);

            when(mediaAssetRepository.findById(104L)).thenReturn(Optional.of(asset));
            when(roomMemberRepository.findByRoomIdAndUserIdAndStatus(10L, 1L, MemberStatus.ACTIVE))
                    .thenReturn(Optional.of(owner));

            service.deleteAsset(1L, 104L);

            verify(mediaAssetRepository, never()).markStatusToDeleted(anyLong(), any(Instant.class));
        }

        @Test
        @DisplayName("Tệp đã DELETED: người ngoài phòng gọi -> ném MEDIA_NOT_FOUND (404)")
        void deleteAsset_alreadyDeleted_outsider_throwsMediaNotFound() {
            MediaAsset asset = MediaAsset.singleUpload(2L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(105L);
            asset.softDelete();

            when(mediaAssetRepository.findById(105L)).thenReturn(Optional.of(asset));
            when(roomMemberRepository.findByRoomIdAndUserIdAndStatus(10L, 1L, MemberStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteAsset(1L, 105L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND);
        }

        @Test
        @DisplayName("Tệp nháp (messageId == null): uploader xóa -> cập nhật DELETED thành công, không phát event")
        void deleteAsset_draft_uploader_success() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(106L);
            asset.markAvailable("display/test.webp", "thumb/test.webp", 800, 600);

            when(mediaAssetRepository.findById(106L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.markStatusToDeleted(eq(106L), any(Instant.class))).thenReturn(1);

            service.deleteAsset(1L, 106L);

            verify(mediaAssetRepository).markStatusToDeleted(eq(106L), any(Instant.class));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Tệp ban đầu messageId == null nhưng DB vừa gắn tin nhắn trước lúc xóa -> đọc latestMessageId và phát MEDIA_STATUS")
        void deleteAsset_draft_attachedJustBeforeDelete_publishesEventWithLatestMessageId() {
            MediaAsset assetInMemory = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            assetInMemory.setId(115L);
            assetInMemory.markAvailable("display/test.webp", "thumb/test.webp", 800, 600);

            MediaAsset assetInDb = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            assetInDb.setId(115L);
            assetInDb.markAvailable("display/test.webp", "thumb/test.webp", 800, 600);
            assetInDb.attachToMessage(99L, 0);

            when(mediaAssetRepository.findById(115L))
                    .thenReturn(Optional.of(assetInMemory)) // lần 1: đọc kiểm tra đầu hàm
                    .thenReturn(Optional.of(assetInDb));    // lần 2: đọc latestMessageId sau khi count == 1
            when(mediaAssetRepository.markStatusToDeleted(eq(115L), any(Instant.class))).thenReturn(1);

            service.deleteAsset(1L, 115L);

            verify(mediaAssetRepository).markStatusToDeleted(eq(115L), any(Instant.class));
            verify(eventPublisher).publishEvent(any(RoomEvent.class));
        }

        @Test
        @DisplayName("Tệp nháp (messageId == null): OWNER phòng không phải uploader cố xóa -> ném MEDIA_NOT_FOUND (404 chống dò ID)")
        void deleteAsset_draft_roomOwnerNotUploader_throwsMediaNotFound() {
            MediaAsset asset = MediaAsset.singleUpload(2L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(107L);
            asset.markAvailable("display/test.webp", "thumb/test.webp", 800, 600);

            RoomMember owner = RoomMember.create(10L, 1L, MemberRole.OWNER, MemberStatus.ACTIVE);

            when(mediaAssetRepository.findById(107L)).thenReturn(Optional.of(asset));
            when(roomMemberRepository.findByRoomIdAndUserIdAndStatus(10L, 1L, MemberStatus.ACTIVE))
                    .thenReturn(Optional.of(owner));

            assertThatThrownBy(() -> service.deleteAsset(1L, 107L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND);

            verify(mediaAssetRepository, never()).markStatusToDeleted(anyLong(), any(Instant.class));
        }

        @Test
        @DisplayName("Tệp đã gắn tin nhắn: uploader xóa -> cập nhật DELETED và phát MEDIA_STATUS event")
        void deleteAsset_attachedMessage_uploader_success_publishesEvent() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(108L);
            asset.attachToMessage(55L, 0);
            asset.markAvailable("display/test.webp", "thumb/test.webp", 800, 600);

            when(mediaAssetRepository.findById(108L)).thenReturn(Optional.of(asset));
            when(mediaAssetRepository.markStatusToDeleted(eq(108L), any(Instant.class))).thenReturn(1);

            service.deleteAsset(1L, 108L);

            verify(mediaAssetRepository).markStatusToDeleted(eq(108L), any(Instant.class));
            verify(eventPublisher).publishEvent(any(RoomEvent.class));
        }

        @Test
        @DisplayName("Tệp đã gắn tin nhắn: OWNER phòng xóa -> cập nhật DELETED và phát MEDIA_STATUS event")
        void deleteAsset_attachedMessage_roomOwner_success_publishesEvent() {
            MediaAsset asset = MediaAsset.singleUpload(2L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(109L);
            asset.attachToMessage(55L, 0);
            asset.markAvailable("display/test.webp", "thumb/test.webp", 800, 600);

            RoomMember owner = RoomMember.create(10L, 1L, MemberRole.OWNER, MemberStatus.ACTIVE);

            when(mediaAssetRepository.findById(109L)).thenReturn(Optional.of(asset));
            when(roomMemberRepository.findByRoomIdAndUserIdAndStatus(10L, 1L, MemberStatus.ACTIVE))
                    .thenReturn(Optional.of(owner));
            when(mediaAssetRepository.markStatusToDeleted(eq(109L), any(Instant.class))).thenReturn(1);

            service.deleteAsset(1L, 109L);

            verify(mediaAssetRepository).markStatusToDeleted(eq(109L), any(Instant.class));
            verify(eventPublisher).publishEvent(any(RoomEvent.class));
        }

        @Test
        @DisplayName("Tệp đã gắn tin nhắn: thành viên thường không phải uploader cố xóa -> ném ACCESS_DENIED (403)")
        void deleteAsset_attachedMessage_regularMemberNotUploader_throwsAccessDenied() {
            MediaAsset asset = MediaAsset.singleUpload(2L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(110L);
            asset.attachToMessage(55L, 0);
            asset.markAvailable("display/test.webp", "thumb/test.webp", 800, 600);

            RoomMember regularMember = RoomMember.create(10L, 1L, MemberRole.MEMBER, MemberStatus.ACTIVE);

            when(mediaAssetRepository.findById(110L)).thenReturn(Optional.of(asset));
            when(roomMemberRepository.findByRoomIdAndUserIdAndStatus(10L, 1L, MemberStatus.ACTIVE))
                    .thenReturn(Optional.of(regularMember));

            assertThatThrownBy(() -> service.deleteAsset(1L, 110L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

            verify(mediaAssetRepository, never()).markStatusToDeleted(anyLong(), any(Instant.class));
        }

        @Test
        @DisplayName("Tệp đã gắn tin nhắn: người ngoài phòng cố xóa -> ném MEDIA_NOT_FOUND (404)")
        void deleteAsset_attachedMessage_outsider_throwsMediaNotFound() {
            MediaAsset asset = MediaAsset.singleUpload(2L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(111L);
            asset.attachToMessage(55L, 0);
            asset.markAvailable("display/test.webp", "thumb/test.webp", 800, 600);

            when(mediaAssetRepository.findById(111L)).thenReturn(Optional.of(asset));
            when(roomMemberRepository.findByRoomIdAndUserIdAndStatus(10L, 1L, MemberStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteAsset(1L, 111L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_NOT_FOUND);

            verify(mediaAssetRepository, never()).markStatusToDeleted(anyLong(), any(Instant.class));
        }

        @Test
        @DisplayName("Tranh chấp cập nhật (updated == 0), DB đã là DELETED -> return idempotent thành công")
        void deleteAsset_raceCondition_updatedZero_refreshedDeleted_returnsIdempotent() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(112L);
            asset.markAvailable("display/test.webp", "thumb/test.webp", 800, 600);

            MediaAsset refreshed = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            refreshed.setId(112L);
            refreshed.softDelete();

            when(mediaAssetRepository.findById(112L))
                    .thenReturn(Optional.of(asset))
                    .thenReturn(Optional.of(refreshed));
            when(mediaAssetRepository.markStatusToDeleted(eq(112L), any(Instant.class))).thenReturn(0);

            service.deleteAsset(1L, 112L);

            verify(mediaAssetRepository).markStatusToDeleted(eq(112L), any(Instant.class));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("Tranh chấp cập nhật (updated == 0), DB đang là COMPLETING -> ném UPLOAD_IN_PROGRESS (409)")
        void deleteAsset_raceCondition_updatedZero_refreshedCompleting_throwsUploadInProgress() {
            MediaAsset asset = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            asset.setId(113L);
            asset.markAvailable("display/test.webp", "thumb/test.webp", 800, 600);

            MediaAsset refreshed = MediaAsset.singleUpload(1L, 10L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD, "image/jpeg", 1024L, "raw/test.jpg");
            refreshed.setId(113L);
            refreshed.markCompleting();

            when(mediaAssetRepository.findById(113L))
                    .thenReturn(Optional.of(asset))
                    .thenReturn(Optional.of(refreshed));
            when(mediaAssetRepository.markStatusToDeleted(eq(113L), any(Instant.class))).thenReturn(0);

            assertThatThrownBy(() -> service.deleteAsset(1L, 113L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UPLOAD_IN_PROGRESS);
        }
    }
}
