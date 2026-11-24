package com.ChattingSystem.media.service.impl;

import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.media.dto.request.BatchAccessUrlRequest;
import com.ChattingSystem.media.dto.response.BatchAccessUrlResponse;
import com.ChattingSystem.media.dto.response.MediaAccessItemResponse;
import com.ChattingSystem.media.dto.response.MediaUrlsDto;
import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.repository.MediaAssetRepository;
import com.ChattingSystem.media.service.MediaAccessService;
import com.ChattingSystem.media.storage.dto.request.PresignGetRequest;
import com.ChattingSystem.media.storage.dto.response.PresignedUrlResponse;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.shared.properties.MediaStorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaAccessServiceImpl implements MediaAccessService {

    private final RoomAccessService roomAccessService;
    private final MediaAssetRepository mediaAssetRepository;
    private final ObjectStorage objectStorage;
    private final MediaStorageProperties storageProperties;

    @Override
    public BatchAccessUrlResponse getAccessUrls(Long userId, BatchAccessUrlRequest request) {
        Objects.requireNonNull(userId, "userId không được để trống");
        Objects.requireNonNull(request, "request không được để trống");

        // 1. Kiểm tra phòng tồn tại/còn hạn và quyền thành viên phòng (chặn tuyệt đối phòng hết hạn/người ngoài)
        roomAccessService.requireActiveRoom(request.roomId());
        roomAccessService.requireActiveMember(userId, request.roomId());

        if (request.mediaAssetIds() == null || request.mediaAssetIds().isEmpty()) {
            return new BatchAccessUrlResponse(List.of());
        }

        // 2. Lọc bỏ phần tử null, khử trùng lặp và bảo toàn thứ tự ban đầu
        Set<Long> uniqueIds = request.mediaAssetIds().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (uniqueIds.isEmpty()) {
            return new BatchAccessUrlResponse(List.of());
        }

        // Truy vấn DB theo lô (không N+1) - giải phóng kết nối DB ngay sau khi đọc xong
        List<Long> activeRoomAssetIds = mediaAssetRepository.findActiveAssetIdsInRoom(request.roomId(), uniqueIds);
        Set<Long> activeAssetIdSet = new HashSet<>(activeRoomAssetIds);

        List<MediaAsset> assetList = mediaAssetRepository.findAllById(uniqueIds);
        Map<Long, MediaAsset> assetMap = assetList.stream()
                .collect(Collectors.toMap(MediaAsset::getId, Function.identity(), (existing, replacing) -> existing));

        // 3. Tính động Cache-Control theo storageProperties.getUrlTtl()
        long ttlSeconds = storageProperties.getUrlTtl().toSeconds();
        long cacheMaxAge = ttlSeconds <= 300 ? Math.max(0, ttlSeconds - 60) : ttlSeconds - 300;
        String cacheControl = "private, max-age=" + cacheMaxAge;

        List<MediaAccessItemResponse> items = new ArrayList<>(uniqueIds.size());

        // 4. Duyệt từng ID theo thứ tự client yêu cầu và kiểm tra quyền
        for (Long id : uniqueIds) {
            MediaAsset asset = assetMap.get(id);
            if (asset == null) {
                items.add(MediaAccessItemResponse.errorOnly(id, null, "MEDIA_NOT_FOUND"));
                continue;
            }

            // Điều kiện hợp lệ:
            // a) Thuộc tin nhắn còn sống của chính roomId
            // b) Hoặc tệp nháp chưa gửi của chính người tải lên trong đúng roomId đó
            boolean isRoomActiveAsset = activeAssetIdSet.contains(asset.getId());
            boolean isUploaderDraftInSameRoom = (asset.getMessageId() == null
                    && request.roomId().equals(asset.getRoomId())
                    && userId.equals(asset.getUploaderId()));

            if (!isRoomActiveAsset && !isUploaderDraftInSameRoom) {
                items.add(MediaAccessItemResponse.errorOnly(id, null, "MEDIA_NOT_FOUND"));
                continue;
            }

            // 5. Xử lý theo asset.getStatus() cho tệp hợp lệ
            switch (asset.getStatus()) {
                case AVAILABLE -> items.add(buildAvailableItem(asset, cacheControl));
                case CREATING, UPLOADING, COMPLETING, PROCESSING -> items.add(
                        MediaAccessItemResponse.inProgress(
                                asset.getId(),
                                asset.getStatus(),
                                asset.getKind(),
                                asset.getContentType(),
                                asset.getWidth(),
                                asset.getHeight(),
                                asset.getDurationMs()
                        )
                );
                case DELETED, PURGED -> items.add(
                        MediaAccessItemResponse.errorOnly(asset.getId(), asset.getStatus(), "MEDIA_DELETED")
                );
                case FAILED -> items.add(
                        MediaAccessItemResponse.errorOnly(asset.getId(), asset.getStatus(), "MEDIA_FAILED")
                );
                case BLOCKED -> items.add(
                        MediaAccessItemResponse.errorOnly(asset.getId(), asset.getStatus(), "MEDIA_BLOCKED")
                );
            }
        }

        return new BatchAccessUrlResponse(items);
    }

    private MediaAccessItemResponse buildAvailableItem(MediaAsset asset, String cacheControl) {
        if (asset.getDisplayKey() == null || asset.getDisplayKey().isBlank()) {
            log.warn("Tệp media asset {} ở trạng thái AVAILABLE nhưng displayKey bị rỗng", asset.getId());
            return MediaAccessItemResponse.errorOnly(asset.getId(), MediaStatus.FAILED, "MEDIA_FAILED");
        }

        String displayMime = resolveDisplayMime(asset.getDisplayKey(), asset.getContentType());
        PresignGetRequest displayReq = new PresignGetRequest(
                storageProperties.getUrlTtl(),
                displayMime,
                "inline",
                cacheControl
        );
        PresignedUrlResponse presigned = objectStorage.presignGet(asset.getDisplayKey(), displayReq);
        String displayUrl = (presigned != null) ? presigned.url() : null;

        if (displayUrl == null || displayUrl.isBlank()) {
            log.warn("Không thể ký Presigned GET cho displayKey của asset {}", asset.getId());
            return MediaAccessItemResponse.errorOnly(asset.getId(), MediaStatus.FAILED, "MEDIA_FAILED");
        }

        String thumbUrl = null;
        if (asset.getThumbKey() != null && !asset.getThumbKey().isBlank()) {
            PresignGetRequest thumbReq = new PresignGetRequest(
                    storageProperties.getUrlTtl(),
                    "image/webp",
                    "inline",
                    cacheControl
            );
            PresignedUrlResponse thumbPresigned = objectStorage.presignGet(asset.getThumbKey(), thumbReq);
            thumbUrl = (thumbPresigned != null) ? thumbPresigned.url() : null;
        }

        MediaUrlsDto urls = new MediaUrlsDto(thumbUrl, displayUrl);
        return MediaAccessItemResponse.available(
                asset.getId(),
                asset.getKind(),
                displayMime,
                asset.getWidth(),
                asset.getHeight(),
                asset.getDurationMs(),
                storageProperties.getUrlTtl().toSeconds(),
                urls
        );
    }

    private String resolveDisplayMime(String displayKey, String fallbackContentType) {
        if (displayKey != null) {
            String lower = displayKey.toLowerCase();
            if (lower.endsWith(".webp")) {
                return "image/webp";
            } else if (lower.endsWith(".mp4")) {
                return "video/mp4";
            } else if (lower.endsWith(".gif")) {
                return "image/gif";
            }
        }
        return (fallbackContentType != null && !fallbackContentType.isBlank())
                ? fallbackContentType
                : "application/octet-stream";
    }
}
