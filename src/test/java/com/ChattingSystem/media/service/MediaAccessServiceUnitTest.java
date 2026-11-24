package com.ChattingSystem.media.service;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.media.dto.request.BatchAccessUrlRequest;
import com.ChattingSystem.media.dto.response.BatchAccessUrlResponse;
import com.ChattingSystem.media.dto.response.MediaAccessItemResponse;
import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaQuality;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.repository.MediaAssetRepository;
import com.ChattingSystem.media.service.impl.MediaAccessServiceImpl;
import com.ChattingSystem.media.storage.dto.request.PresignGetRequest;
import com.ChattingSystem.media.storage.dto.response.PresignedUrlResponse;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.MediaStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Kiểm thử MediaAccessService (Cấp URL xem tệp theo lô & Chống IDOR)")
class MediaAccessServiceUnitTest {

    @Mock
    private RoomAccessService roomAccessService;

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private ObjectStorage objectStorage;

    private MediaStorageProperties storageProperties;
    private MediaAccessService mediaAccessService;

    private final Long userId = 1L;
    private final Long roomId = 10L;

    @BeforeEach
    void setUp() {
        storageProperties = new MediaStorageProperties(
                "test-media-bucket",
                "us-east-1",
                "http://localhost:9000",
                "http://localhost:9000",
                true,
                "accessKey",
                "secretKey",
                5242880,
                Duration.ofMinutes(15),
                Duration.ofMinutes(30)
        );

        mediaAccessService = new MediaAccessServiceImpl(
                roomAccessService,
                mediaAssetRepository,
                objectStorage,
                storageProperties
        );
    }

    @Nested
    @DisplayName("1. Kiểm tra xác thực phòng và thành viên phòng")
    class RoomAccessTests {

        @Test
        @DisplayName("Phòng không hoạt động hoặc hết hạn -> ném ROOM_EXPIRED và không kiểm tra thành viên")
        void requireActiveRoom_expired_throwsException() {
            when(roomAccessService.requireActiveRoom(roomId))
                    .thenThrow(new AppException(ErrorCode.ROOM_EXPIRED));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(101L));

            assertThatThrownBy(() -> mediaAccessService.getAccessUrls(userId, request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ROOM_EXPIRED));

            verify(roomAccessService, never()).requireActiveMember(any(), any());
            verify(mediaAssetRepository, never()).findActiveAssetIdsInRoom(any(), any());
        }

        @Test
        @DisplayName("Người ngoài phòng -> ném NOT_ROOM_MEMBER")
        void requireActiveMember_notMember_throwsException() {
            when(roomAccessService.requireActiveRoom(roomId)).thenReturn(mock(ChatRoom.class));
            when(roomAccessService.requireActiveMember(userId, roomId))
                    .thenThrow(new AppException(ErrorCode.NOT_ROOM_MEMBER));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(101L));

            assertThatThrownBy(() -> mediaAccessService.getAccessUrls(userId, request))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_ROOM_MEMBER));

            verify(mediaAssetRepository, never()).findActiveAssetIdsInRoom(any(), any());
            verify(mediaAssetRepository, never()).findAllById(any());
        }

        @Test
        @DisplayName("Danh sách mediaAssetIds rỗng -> trả về danh sách rỗng mà không gọi DB tệp")
        void emptyMediaAssetIds_returnsEmpty() {
            when(roomAccessService.requireActiveRoom(roomId)).thenReturn(mock(ChatRoom.class));
            when(roomAccessService.requireActiveMember(userId, roomId)).thenReturn(mock(RoomMember.class));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of());
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).isEmpty();
            verify(mediaAssetRepository, never()).findActiveAssetIdsInRoom(any(), any());
            verify(mediaAssetRepository, never()).findAllById(any());
        }

        @Test
        @DisplayName("Danh sách mediaAssetIds chỉ chứa phần tử null -> lọc sạch và trả về rỗng không gọi DB")
        void onlyNullMediaAssetIds_returnsEmptyWithoutDbQuery() {
            when(roomAccessService.requireActiveRoom(roomId)).thenReturn(mock(ChatRoom.class));
            when(roomAccessService.requireActiveMember(userId, roomId)).thenReturn(mock(RoomMember.class));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, Collections.singletonList(null));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).isEmpty();
            verify(mediaAssetRepository, never()).findActiveAssetIdsInRoom(any(), any());
            verify(mediaAssetRepository, never()).findAllById(any());
        }
    }

    @Nested
    @DisplayName("2. Kiểm tra chống IDOR và quyền truy cập tệp")
    class IdorSecurityTests {

        @BeforeEach
        void mockRoomAndMember() {
            when(roomAccessService.requireActiveRoom(roomId)).thenReturn(mock(ChatRoom.class));
            when(roomAccessService.requireActiveMember(userId, roomId)).thenReturn(mock(RoomMember.class));
        }

        @Test
        @DisplayName("Tệp không tồn tại trong DB -> trả về MEDIA_NOT_FOUND, status và metadata đều là null")
        void assetNotFoundInDb_returnsMediaNotFoundWithNullMetadata() {
            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(Collections.emptyList());
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(Collections.emptyList());

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(999L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            MediaAccessItemResponse item = response.items().get(0);
            assertThat(item.mediaAssetId()).isEqualTo(999L);
            assertThat(item.error()).isEqualTo("MEDIA_NOT_FOUND");
            assertThat(item.status()).isNull();
            assertThat(item.kind()).isNull();
            assertThat(item.contentType()).isNull();
            assertThat(item.urls()).isNull();
        }

        @Test
        @DisplayName("Tệp thuộc phòng khác -> trả về MEDIA_NOT_FOUND, status là null")
        void assetInDifferentRoom_returnsMediaNotFound() {
            MediaAsset otherRoomAsset = MediaAsset.singleUpload(
                    userId, 99L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/other.jpg"
            );
            otherRoomAsset.setId(201L);

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(Collections.emptyList());
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(otherRoomAsset));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(201L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            MediaAccessItemResponse item = response.items().get(0);
            assertThat(item.mediaAssetId()).isEqualTo(201L);
            assertThat(item.error()).isEqualTo("MEDIA_NOT_FOUND");
            assertThat(item.status()).isNull();
        }

        @Test
        @DisplayName("Tệp nháp của người khác trong cùng phòng -> trả về MEDIA_NOT_FOUND")
        void draftOfOtherUser_returnsMediaNotFound() {
            Long otherUserId = 2L;
            MediaAsset otherUserDraft = MediaAsset.singleUpload(
                    otherUserId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/draft.jpg"
            );
            otherUserDraft.setId(202L);

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(Collections.emptyList());
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(otherUserDraft));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(202L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            MediaAccessItemResponse item = response.items().get(0);
            assertThat(item.mediaAssetId()).isEqualTo(202L);
            assertThat(item.error()).isEqualTo("MEDIA_NOT_FOUND");
            assertThat(item.status()).isNull();
        }

        @Test
        @DisplayName("Tệp nháp của chính mình nhưng tải ở phòng khác -> trả về MEDIA_NOT_FOUND")
        void draftOfSelfInDifferentRoom_returnsMediaNotFound() {
            MediaAsset selfDraftDifferentRoom = MediaAsset.singleUpload(
                    userId, 88L, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/draft2.jpg"
            );
            selfDraftDifferentRoom.setId(203L);

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(Collections.emptyList());
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(selfDraftDifferentRoom));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(203L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            MediaAccessItemResponse item = response.items().get(0);
            assertThat(item.mediaAssetId()).isEqualTo(203L);
            assertThat(item.error()).isEqualTo("MEDIA_NOT_FOUND");
            assertThat(item.status()).isNull();
        }

        @Test
        @DisplayName("Tệp thuộc tin nhắn đã bị xóa mềm (deleted_at IS NOT NULL) -> trả về MEDIA_NOT_FOUND")
        void softDeletedMessageAsset_returnsMediaNotFound() {
            MediaAsset softDeletedMsgAsset = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/msg.jpg"
            );
            softDeletedMsgAsset.setId(204L);
            softDeletedMsgAsset.attachToMessage(999L, 0);

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(Collections.emptyList());
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(softDeletedMsgAsset));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(204L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            MediaAccessItemResponse item = response.items().get(0);
            assertThat(item.mediaAssetId()).isEqualTo(204L);
            assertThat(item.error()).isEqualTo("MEDIA_NOT_FOUND");
            assertThat(item.status()).isNull();
        }

        @Test
        @DisplayName("Chính chủ xem trước tệp nháp chưa gắn tin nhắn (messageId == null) trong đúng phòng -> Thành công")
        void draftOfSelfInSameRoom_previewAllowed() {
            MediaAsset selfDraft = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 1024L, "raw/draft.jpg"
            );
            selfDraft.setId(205L);
            selfDraft.markAvailable("display/2026/11/draft.webp", "thumb/2026/11/draft.webp", 800, 600);

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(Collections.emptyList());
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(selfDraft));

            when(objectStorage.presignGet(eq("display/2026/11/draft.webp"), any()))
                    .thenReturn(new PresignedUrlResponse("http://s3.local/display.webp", "GET", Map.of(), Instant.now().plusSeconds(1800)));
            when(objectStorage.presignGet(eq("thumb/2026/11/draft.webp"), any()))
                    .thenReturn(new PresignedUrlResponse("http://s3.local/thumb.webp", "GET", Map.of(), Instant.now().plusSeconds(1800)));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(205L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            MediaAccessItemResponse item = response.items().get(0);
            assertThat(item.mediaAssetId()).isEqualTo(205L);
            assertThat(item.status()).isEqualTo(MediaStatus.AVAILABLE);
            assertThat(item.error()).isNull();
            assertThat(item.urls().display()).isEqualTo("http://s3.local/display.webp");
            assertThat(item.urls().thumbnail()).isEqualTo("http://s3.local/thumb.webp");
        }
    }

    @Nested
    @DisplayName("3. Kiểm tra trạng thái AVAILABLE, ký Presigned GET và tính động Cache-Control")
    class AvailableSigningTests {

        @BeforeEach
        void mockRoomAndMember() {
            when(roomAccessService.requireActiveRoom(roomId)).thenReturn(mock(ChatRoom.class));
            when(roomAccessService.requireActiveMember(userId, roomId)).thenReturn(mock(RoomMember.class));
        }

        @Test
        @DisplayName("AVAILABLE ảnh WebP: Ký đúng displayKey, thumbKey và tính động Cache-Control (1800s -> max-age=1500)")
        void availableImage_signsCorrectHeaders() {
            MediaAsset asset = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 2048L, "raw/img.jpg"
            );
            asset.setId(301L);
            asset.markAvailable("display/2026/11/img.webp", "thumb/2026/11/img.webp", 1920, 1080);

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(List.of(301L));
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(asset));

            ArgumentCaptor<PresignGetRequest> displayCaptor = ArgumentCaptor.forClass(PresignGetRequest.class);
            ArgumentCaptor<PresignGetRequest> thumbCaptor = ArgumentCaptor.forClass(PresignGetRequest.class);

            when(objectStorage.presignGet(eq("display/2026/11/img.webp"), displayCaptor.capture()))
                    .thenReturn(new PresignedUrlResponse("http://s3.local/img.webp", "GET", Map.of(), Instant.now().plusSeconds(1800)));
            when(objectStorage.presignGet(eq("thumb/2026/11/img.webp"), thumbCaptor.capture()))
                    .thenReturn(new PresignedUrlResponse("http://s3.local/thumb.webp", "GET", Map.of(), Instant.now().plusSeconds(1800)));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(301L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            MediaAccessItemResponse item = response.items().get(0);
            assertThat(item.mediaAssetId()).isEqualTo(301L);
            assertThat(item.status()).isEqualTo(MediaStatus.AVAILABLE);
            assertThat(item.contentType()).isEqualTo("image/webp");
            assertThat(item.width()).isEqualTo(1920);
            assertThat(item.height()).isEqualTo(1080);
            assertThat(item.urlExpiresInSeconds()).isEqualTo(1800L);
            assertThat(item.urls().display()).isEqualTo("http://s3.local/img.webp");
            assertThat(item.urls().thumbnail()).isEqualTo("http://s3.local/thumb.webp");

            // Kiểm tra thông số ký GET
            PresignGetRequest displayReq = displayCaptor.getValue();
            assertThat(displayReq.responseContentType()).isEqualTo("image/webp");
            assertThat(displayReq.contentDisposition()).isEqualTo("inline");
            assertThat(displayReq.responseCacheControl()).isEqualTo("private, max-age=1500");
            assertThat(displayReq.ttl()).isEqualTo(Duration.ofMinutes(30));

            PresignGetRequest thumbReq = thumbCaptor.getValue();
            assertThat(thumbReq.responseContentType()).isEqualTo("image/webp");
            assertThat(thumbReq.contentDisposition()).isEqualTo("inline");
            assertThat(thumbReq.responseCacheControl()).isEqualTo("private, max-age=1500");
            assertThat(thumbReq.ttl()).isEqualTo(Duration.ofMinutes(30));
        }

        @Test
        @DisplayName("Tính động Cache-Control khi TTL ngắn (<= 300s): ttl=180s -> max-age=120")
        void dynamicCacheControl_shortTtl_calculatesCorrectMaxAge() {
            MediaStorageProperties shortTtlProps = new MediaStorageProperties(
                    "test-media-bucket",
                    "us-east-1",
                    "http://localhost:9000",
                    "http://localhost:9000",
                    true,
                    "accessKey",
                    "secretKey",
                    5242880,
                    Duration.ofMinutes(15),
                    Duration.ofSeconds(180) // 180s <= 300s -> max-age = 180 - 60 = 120
            );
            MediaAccessService shortTtlService = new MediaAccessServiceImpl(
                    roomAccessService, mediaAssetRepository, objectStorage, shortTtlProps
            );

            MediaAsset asset = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 2048L, "raw/img.jpg"
            );
            asset.setId(310L);
            asset.markAvailable("display/2026/11/img.webp", null, 1920, 1080);

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(List.of(310L));
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(asset));

            ArgumentCaptor<PresignGetRequest> displayCaptor = ArgumentCaptor.forClass(PresignGetRequest.class);
            when(objectStorage.presignGet(eq("display/2026/11/img.webp"), displayCaptor.capture()))
                    .thenReturn(new PresignedUrlResponse("http://s3.local/img.webp", "GET", Map.of(), Instant.now().plusSeconds(180)));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(310L));
            BatchAccessUrlResponse response = shortTtlService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            assertThat(displayCaptor.getValue().responseCacheControl()).isEqualTo("private, max-age=120");
        }

        @Test
        @DisplayName("AVAILABLE Video MP4: Nhận diện chuẩn MIME video/mp4 từ displayKey")
        void availableVideo_resolvesMp4Mime() {
            MediaAsset asset = MediaAsset.multipartUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.VIDEO, MediaQuality.STANDARD,
                    "video/quicktime", 50000000L, "raw/vid.mov", "upload-1"
            );
            asset.setId(302L);
            asset.markAvailable("display/2026/11/vid.mp4", "thumb/2026/11/vid.webp", 1920, 1080, 15000);

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(List.of(302L));
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(asset));

            ArgumentCaptor<PresignGetRequest> displayCaptor = ArgumentCaptor.forClass(PresignGetRequest.class);
            when(objectStorage.presignGet(eq("display/2026/11/vid.mp4"), displayCaptor.capture()))
                    .thenReturn(new PresignedUrlResponse("http://s3.local/vid.mp4", "GET", Map.of(), Instant.now().plusSeconds(1800)));
            when(objectStorage.presignGet(eq("thumb/2026/11/vid.webp"), any()))
                    .thenReturn(new PresignedUrlResponse("http://s3.local/vid-thumb.webp", "GET", Map.of(), Instant.now().plusSeconds(1800)));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(302L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            MediaAccessItemResponse item = response.items().get(0);
            assertThat(item.contentType()).isEqualTo("video/mp4");
            assertThat(item.durationMs()).isEqualTo(15000);
            assertThat(displayCaptor.getValue().responseContentType()).isEqualTo("video/mp4");
        }

        @Test
        @DisplayName("AVAILABLE khi không có thumbKey -> urls.thumbnail() trả về null")
        void availableWithoutThumbKey_thumbnailIsNull() {
            MediaAsset asset = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 512L, "raw/small.jpg"
            );
            asset.setId(303L);
            asset.markAvailable("display/2026/11/small.webp", null, 100, 100);

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(List.of(303L));
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(asset));

            when(objectStorage.presignGet(eq("display/2026/11/small.webp"), any()))
                    .thenReturn(new PresignedUrlResponse("http://s3.local/small.webp", "GET", Map.of(), Instant.now().plusSeconds(1800)));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(303L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            MediaAccessItemResponse item = response.items().get(0);
            assertThat(item.urls().display()).isEqualTo("http://s3.local/small.webp");
            assertThat(item.urls().thumbnail()).isNull();
            verify(objectStorage, never()).presignGet(eq(null), any());
        }

        @Test
        @DisplayName("AVAILABLE nhưng displayKey bị null/rỗng -> trả về MEDIA_FAILED với status FAILED")
        void availableWithNullDisplayKey_returnsMediaFailed() {
            MediaAsset asset = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 512L, "raw/broken.jpg"
            );
            asset.setId(304L);
            asset.markAvailable(null, null, 100, 100);

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(List.of(304L));
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(asset));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(304L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            MediaAccessItemResponse item = response.items().get(0);
            assertThat(item.mediaAssetId()).isEqualTo(304L);
            assertThat(item.status()).isEqualTo(MediaStatus.FAILED);
            assertThat(item.error()).isEqualTo("MEDIA_FAILED");
            assertThat(item.urls()).isNull();
            verify(objectStorage, never()).presignGet(any(), any());
        }

        @Test
        @DisplayName("AVAILABLE nhưng objectStorage trả về presigned url null/blank -> trả về MEDIA_FAILED với status FAILED")
        void availableWithNullPresignedDisplayUrl_returnsMediaFailed() {
            MediaAsset asset = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 512L, "raw/ok.jpg"
            );
            asset.setId(305L);
            asset.markAvailable("display/broken.webp", null, 100, 100);

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(List.of(305L));
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(asset));

            when(objectStorage.presignGet(eq("display/broken.webp"), any()))
                    .thenReturn(new PresignedUrlResponse(null, "GET", Map.of(), Instant.now().plusSeconds(1800)));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(305L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            MediaAccessItemResponse item = response.items().get(0);
            assertThat(item.mediaAssetId()).isEqualTo(305L);
            assertThat(item.status()).isEqualTo(MediaStatus.FAILED);
            assertThat(item.error()).isEqualTo("MEDIA_FAILED");
            assertThat(item.urls()).isNull();
        }
    }

    @Nested
    @DisplayName("4. Kiểm tra các trạng thái xử lý và trạng thái kết thúc")
    class StatusBranchesTests {

        @BeforeEach
        void mockRoomAndMember() {
            when(roomAccessService.requireActiveRoom(roomId)).thenReturn(mock(ChatRoom.class));
            when(roomAccessService.requireActiveMember(userId, roomId)).thenReturn(mock(RoomMember.class));
        }

        @Test
        @DisplayName("CREATING, UPLOADING, COMPLETING, PROCESSING -> trả về status, urls == null, error == null")
        void inProgressStatuses_returnStatusWithoutUrls() {
            MediaAsset processingAsset = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 2048L, "raw/p.jpg"
            );
            processingAsset.setId(401L);
            processingAsset.markProcessing("image/jpeg");

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(List.of(401L));
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(processingAsset));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(401L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(1);
            MediaAccessItemResponse item = response.items().get(0);
            assertThat(item.mediaAssetId()).isEqualTo(401L);
            assertThat(item.status()).isEqualTo(MediaStatus.PROCESSING);
            assertThat(item.urls()).isNull();
            assertThat(item.error()).isNull();
            verify(objectStorage, never()).presignGet(any(), any());
        }

        @Test
        @DisplayName("DELETED và PURGED -> trả về MEDIA_DELETED, urls == null")
        void deletedAndPurgedStatuses_returnMediaDeleted() {
            MediaAsset deletedAsset = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 2048L, "raw/del.jpg"
            );
            deletedAsset.setId(402L);
            deletedAsset.softDelete();

            MediaAsset purgedAsset = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 2048L, "raw/purged.jpg"
            );
            purgedAsset.setId(403L);
            purgedAsset.markPurged();

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(List.of(402L, 403L));
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(deletedAsset, purgedAsset));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(402L, 403L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(2);
            assertThat(response.items().get(0).status()).isEqualTo(MediaStatus.DELETED);
            assertThat(response.items().get(0).error()).isEqualTo("MEDIA_DELETED");
            assertThat(response.items().get(0).urls()).isNull();

            assertThat(response.items().get(1).status()).isEqualTo(MediaStatus.PURGED);
            assertThat(response.items().get(1).error()).isEqualTo("MEDIA_DELETED");
            assertThat(response.items().get(1).urls()).isNull();
        }

        @Test
        @DisplayName("FAILED -> trả về MEDIA_FAILED, BLOCKED -> trả về MEDIA_BLOCKED")
        void failedAndBlockedStatuses_returnExpectedErrors() {
            MediaAsset failedAsset = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 2048L, "raw/f.jpg"
            );
            failedAsset.setId(404L);
            failedAsset.markFailed("Lỗi FFmpeg");

            MediaAsset blockedAsset = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 2048L, "raw/b.jpg"
            );
            blockedAsset.setId(405L);
            blockedAsset.markBlocked("Mã độc");

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(List.of(404L, 405L));
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(failedAsset, blockedAsset));

            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, List.of(404L, 405L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(2);
            assertThat(response.items().get(0).status()).isEqualTo(MediaStatus.FAILED);
            assertThat(response.items().get(0).error()).isEqualTo("MEDIA_FAILED");

            assertThat(response.items().get(1).status()).isEqualTo(MediaStatus.BLOCKED);
            assertThat(response.items().get(1).error()).isEqualTo("MEDIA_BLOCKED");
        }

        @Test
        @DisplayName("Khử trùng lặp ID yêu cầu và bỏ qua phần tử null nhưng vẫn giữ nguyên thứ tự ban đầu")
        void deduplication_preservesOrderAndIgnoresNull() {
            MediaAsset asset1 = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 2048L, "raw/1.jpg"
            );
            asset1.setId(501L);
            asset1.markProcessing("image/jpeg");

            MediaAsset asset2 = MediaAsset.singleUpload(
                    userId, roomId, UUID.randomUUID(), MediaKind.IMAGE, MediaQuality.STANDARD,
                    "image/jpeg", 2048L, "raw/2.jpg"
            );
            asset2.setId(502L);
            asset2.markProcessing("image/jpeg");

            when(mediaAssetRepository.findActiveAssetIdsInRoom(eq(roomId), anyCollection()))
                    .thenReturn(List.of(501L, 502L));
            when(mediaAssetRepository.findAllById(anyCollection()))
                    .thenReturn(List.of(asset1, asset2));

            // Gửi trùng lặp [502L, null, 501L, 502L, 501L]
            BatchAccessUrlRequest request = new BatchAccessUrlRequest(roomId, Arrays.asList(502L, null, 501L, 502L, 501L));
            BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);

            assertThat(response.items()).hasSize(2);
            assertThat(response.items().get(0).mediaAssetId()).isEqualTo(502L);
            assertThat(response.items().get(1).mediaAssetId()).isEqualTo(501L);
        }
    }
}
