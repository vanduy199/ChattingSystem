package com.ChattingSystem.media.service.impl;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.MemberStatus;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.media.dto.request.CompleteUploadRequest;
import com.ChattingSystem.media.dto.request.CompletedPartDto;
import com.ChattingSystem.media.dto.request.InitUploadRequest;
import com.ChattingSystem.media.dto.response.*;
import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaQuality;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.repository.MediaAssetRepository;
import com.ChattingSystem.media.service.MediaUploadService;
import com.ChattingSystem.media.storage.dto.request.CompletedPartRequest;
import com.ChattingSystem.media.storage.dto.response.ObjectMetadataResponse;
import com.ChattingSystem.media.storage.dto.response.PartSummaryResponse;
import com.ChattingSystem.media.storage.dto.response.PresignedUrlResponse;
import com.ChattingSystem.media.storage.exception.NoSuchUploadStorageException;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.media.util.MediaSniffer;
import com.ChattingSystem.media.util.ObjectKeys;
import com.ChattingSystem.realtime.event.RoomEvent;
import com.ChattingSystem.realtime.event.RoomEventType;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.MediaLimitsProperties;
import com.ChattingSystem.shared.properties.MediaStorageProperties;
import com.ChattingSystem.user.entity.Role;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MediaUploadServiceImpl implements MediaUploadService {

    private static final Logger log = LoggerFactory.getLogger(MediaUploadServiceImpl.class);

    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MediaAssetRepository mediaAssetRepository;
    private final RoomAccessService roomAccessService;
    private final UserRepository userRepository;
    private final ObjectStorage objectStorage;
    private final MediaLimitsProperties limitsProperties;
    private final MediaStorageProperties storageProperties;
    private final TransactionTemplate transactionTemplate;
    private final RoomMemberRepository roomMemberRepository;
    private final ApplicationEventPublisher events;

    @Autowired
    public MediaUploadServiceImpl(MediaAssetRepository mediaAssetRepository,
                                  RoomAccessService roomAccessService,
                                  UserRepository userRepository,
                                  ObjectStorage objectStorage,
                                  MediaLimitsProperties limitsProperties,
                                  MediaStorageProperties storageProperties,
                                  PlatformTransactionManager transactionManager,
                                  RoomMemberRepository roomMemberRepository,
                                  ApplicationEventPublisher events) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.roomAccessService = roomAccessService;
        this.userRepository = userRepository;
        this.objectStorage = objectStorage;
        this.limitsProperties = limitsProperties;
        this.storageProperties = storageProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.roomMemberRepository = roomMemberRepository;
        this.events = events;
    }

    public MediaUploadServiceImpl(MediaAssetRepository mediaAssetRepository,
                                  RoomAccessService roomAccessService,
                                  UserRepository userRepository,
                                  ObjectStorage objectStorage,
                                  MediaLimitsProperties limitsProperties,
                                  MediaStorageProperties storageProperties,
                                  RoomMemberRepository roomMemberRepository,
                                  ApplicationEventPublisher events) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.roomAccessService = roomAccessService;
        this.userRepository = userRepository;
        this.objectStorage = objectStorage;
        this.limitsProperties = limitsProperties;
        this.storageProperties = storageProperties;
        this.transactionTemplate = createPassThroughTransactionTemplate();
        this.roomMemberRepository = roomMemberRepository;
        this.events = events;
    }

    /**
     * Constructor phụ phục vụ Unit Test khi không cần TransactionManager thật.
     */
    public MediaUploadServiceImpl(MediaAssetRepository mediaAssetRepository,
                                  RoomAccessService roomAccessService,
                                  UserRepository userRepository,
                                  ObjectStorage objectStorage,
                                  MediaLimitsProperties limitsProperties,
                                  MediaStorageProperties storageProperties) {
        this(mediaAssetRepository, roomAccessService, userRepository, objectStorage, limitsProperties, storageProperties, null, null);
    }

    private record ExistingAssetResult(MediaAsset asset, boolean shouldSignUrl) {}

    @Override
    public InitUploadResponse initUpload(Long userId, InitUploadRequest request) {
        // === BƯỚC 1: VALIDATE NGOÀI TRANSACTION (Không giữ DB connection) ===
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ChatRoom room = roomAccessService.requireActiveRoom(request.roomId());
        roomAccessService.requireActiveMember(userId, request.roomId());
        boolean isPrivateRoom = "private".equalsIgnoreCase(room.getRoomType());

        String normalizedMime = request.contentType().trim().toLowerCase();
        MediaKind kind = normalizedMime.startsWith("video/") ? MediaKind.VIDEO : MediaKind.IMAGE;

        // 1.2 Quyền Guest: Chỉ được ở phòng public, cấm video
        if (user.getRole() == Role.GUEST) {
            if (isPrivateRoom) {
                throw new AppException(ErrorCode.LOGIN_REQUIRED_TO_CHAT, "Khách không được phép tải tệp trong phòng riêng tư");
            }
            if (kind == MediaKind.VIDEO) {
                throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Khách không được phép tải video");
            }
            if (request.hd()) {
                throw new AppException(ErrorCode.ACCESS_DENIED, "Khách không được phép tải ảnh chất lượng HD");
            }
        }

        // 1.3 Chế độ HD chỉ áp dụng cho ảnh
        if (kind == MediaKind.VIDEO && request.hd()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Chế độ HD chỉ áp dụng cho hình ảnh");
        }

        // 1.4 Kiểm tra Whitelist MIME khai báo
        if (!MediaSniffer.isDeclaredMimeAllowed(normalizedMime, user.getRole(), room.getRoomType())) {
            throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Định dạng tệp (" + normalizedMime + ") không được hỗ trợ");
        }

        // 1.5 Kiểm tra trần dung lượng (format %.1f MB)
        long maxAllowedBytes = limitsProperties.getMaxSizeBytes(kind, user.getRole(), room.getRoomType());
        if (request.sizeBytes() > maxAllowedBytes) {
            throw new AppException(ErrorCode.MEDIA_TOO_LARGE,
                    String.format("Dung lượng tệp (%.1f MB) vượt quá giới hạn cho phép (%.1f MB)",
                            (double) request.sizeBytes() / (1024 * 1024),
                            (double) maxAllowedBytes / (1024 * 1024)));
        }

        int chunkSize = getChunkSize();
        Duration ttl = storageProperties.putUrlTtl();
        String extension = resolveExtension(normalizedMime);
        String rawKey = ObjectKeys.rawKey(request.clientUploadId(), extension);
        MediaQuality quality = request.hd() ? MediaQuality.HD : MediaQuality.STANDARD;

        // === BƯỚC 2: TRANSACTION 1 NGẮN (Lock, Idempotency & Quota check) ===
        Object result = transactionTemplate.execute(status -> {
            mediaAssetRepository.acquireUserAdvisoryLock(userId);

            // Kiểm tra Idempotency TRƯỚC KHI tính rate-limit và quota
            Optional<MediaAsset> existing = mediaAssetRepository.findByUploaderIdAndClientUploadId(userId, request.clientUploadId());
            if (existing.isPresent()) {
                MediaAsset asset = existing.get();

                // Siết chặt Idempotency: roomId, sizeBytes, kind, quality phải khớp hoàn toàn
                if (!asset.getRoomId().equals(request.roomId())
                        || asset.getSizeBytes() != request.sizeBytes()
                        || asset.getKind() != kind
                        || asset.getQuality() != quality) {
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "Thông tin tệp tải lên không khớp với phiên đã khởi tạo trước đó");
                }

                // 1. Kiểm tra các trạng thái kết thúc TRƯỚC isExpired
                if (asset.getStatus() == MediaStatus.FAILED || asset.getStatus() == MediaStatus.DELETED || asset.getStatus() == MediaStatus.PURGED) {
                    throw new AppException(ErrorCode.MEDIA_DELETED, "Tệp đã bị xóa hoặc hủy tải lên");
                }
                if (asset.getStatus() == MediaStatus.BLOCKED) {
                    throw new AppException(ErrorCode.INVALID_MEDIA_SIGNATURE, "Tệp bị chặn do vi phạm quy tắc an toàn");
                }

                // 2. Kiểm tra các trạng thái đã hoàn tất TRƯỚC isExpired
                if (asset.getStatus() == MediaStatus.PROCESSING || asset.getStatus() == MediaStatus.AVAILABLE) {
                    return new ExistingAssetResult(asset, false);
                }

                // 3. Đang dở dang CREATING / COMPLETING
                if (asset.getStatus() == MediaStatus.CREATING || asset.getStatus() == MediaStatus.COMPLETING) {
                    throw new AppException(ErrorCode.UPLOAD_IN_PROGRESS, "Tệp đang trong quá trình khởi tạo hoặc xử lý, vui lòng thử lại sau");
                }

                // 4. Phiên UPLOADING dở dang: kiểm tra hết hạn 24 giờ
                if (isExpired(asset)) {
                    throw new AppException(ErrorCode.UPLOAD_EXPIRED, "Phiên tải lên đã hết hạn");
                }

                return new ExistingAssetResult(asset, true);
            }

            // Kiểm tra Rate-limit 1 phút (maxInitPerMinute)
            long initCount = mediaAssetRepository.countInitSince(userId, Instant.now().minus(Duration.ofMinutes(1)));
            int maxInitPerMin = limitsProperties.maxInitPerMinute().getLimit(user.getRole(), room.getRoomType());
            if (initCount >= maxInitPerMin) {
                throw new AppException(ErrorCode.UPLOAD_RATE_LIMITED, "Bạn khởi tạo quá nhiều phiên tải lên trong 1 phút, vui lòng thử lại sau");
            }

            // Kiểm tra số upload đang mở trong 24 giờ (maxOpenUploads)
            Instant notExpiredAfter = Instant.now().minus(SESSION_TTL);
            long openUploads = mediaAssetRepository.countOpenUploads(userId, notExpiredAfter);
            int maxOpen = limitsProperties.maxOpenUploads().getLimit(user.getRole(), room.getRoomType());
            if (openUploads >= maxOpen) {
                throw new AppException(ErrorCode.UPLOAD_RATE_LIMITED, "Bạn đang có quá nhiều phiên tải lên đang mở, vui lòng hoàn tất hoặc thử lại sau");
            }

            // Kiểm tra Quota HD trong ngày tại phòng Public
            if (request.hd() && !isPrivateRoom) {
                Instant startOfDay = LocalDate.now(VIETNAM_ZONE).atStartOfDay(VIETNAM_ZONE).toInstant();
                long hdUsedToday = mediaAssetRepository.countHDInDay(userId, startOfDay);
                if (hdUsedToday >= 5) {
                    throw new AppException(ErrorCode.HD_QUOTA_EXCEEDED, "Bạn đã dùng hết hạn mức 5 lượt tải ảnh HD trong ngày tại phòng công khai");
                }
            }

            // Lưu bản ghi MediaAsset mới (dùng chunkSize thống nhất)
            MediaAsset newAsset;
            if (request.sizeBytes() <= chunkSize) {
                newAsset = MediaAsset.singleUpload(userId, request.roomId(), request.clientUploadId(),
                        kind, quality, normalizedMime, request.sizeBytes(), rawKey);
            } else {
                newAsset = MediaAsset.creating(userId, request.roomId(), request.clientUploadId(),
                        kind, quality, normalizedMime, request.sizeBytes(), rawKey);
            }
            return mediaAssetRepository.save(newAsset);
        });

        // === BƯỚC 3: XỬ LÝ NGOÀI TRANSACTION (Gọi S3 và Ký URL) ===
        if (result instanceof ExistingAssetResult existingResult) {
            MediaAsset asset = existingResult.asset();
            if (!existingResult.shouldSignUrl()) {
                return new InitUploadResponse(
                        asset.getId(), asset.getStatus(), asset.getKind(),
                        asset.getUploadId() != null ? "MULTIPART" : "SINGLE",
                        chunkSize,
                        asset.getUploadId() != null ? (int) Math.ceil((double) asset.getSizeBytes() / chunkSize) : 1,
                        calculateExpiresAt(asset), 0, List.of()
                );
            }
            return buildResignedInitResponse(asset, ttl, chunkSize);
        }

        MediaAsset asset = (MediaAsset) result;

        if (request.sizeBytes() <= chunkSize) {
            // === SINGLE PUT ===
            PresignedUrlResponse target = objectStorage.presignSinglePut(rawKey, normalizedMime, request.sizeBytes(), ttl);
            UploadTargetDto targetDto = new UploadTargetDto(1, "PUT", target.url(), target.signedHeaders());

            return new InitUploadResponse(
                    asset.getId(),
                    asset.getStatus(),
                    asset.getKind(),
                    "SINGLE",
                    chunkSize,
                    1,
                    calculateExpiresAt(asset),
                    ttl.toSeconds(),
                    List.of(targetDto)
            );
        } else {
            // === MULTIPART UPLOAD: Gọi S3 ngoài Tx -> Tx2 ngắn cập nhật uploadId ===
            String uploadId;
            try {
                uploadId = objectStorage.createMultipartUpload(rawKey, normalizedMime);
            } catch (Exception e) {
                log.error("Lỗi khi tạo multipart upload trên storage cho asset {}: {}", asset.getId(), e.getMessage());
                transactionTemplate.execute(status -> {
                    mediaAssetRepository.markCreatingFailed(asset.getId(), truncateError("Lỗi khởi tạo storage: " + e.getMessage()), Instant.now());
                    return null;
                });
                throw new AppException(ErrorCode.STORAGE_ERROR, "Không thể khởi tạo phiên tải lên trên dịch vụ lưu trữ");
            }

            // Tx 2 ngắn: cập nhật uploadId và chuyển CREATING -> UPLOADING
            int updated = transactionTemplate.execute(status ->
                    mediaAssetRepository.updateUploadIdAndMarkUploading(asset.getId(), uploadId, Instant.now()));

            if (updated == 0) {
                // Tranh chấp: asset đã bị abortUpload hoặc thay đổi trạng thái trước khi kịp ghi uploadId
                try {
                    objectStorage.abortMultipartUpload(rawKey, uploadId);
                } catch (Exception ex) {
                    log.warn("Lỗi dọn dẹp multipart upload sau tranh chấp thất bại: {}", ex.getMessage());
                }
                throw new AppException(ErrorCode.MEDIA_DELETED, "Phiên tải lên đã bị hủy hoặc thay đổi trạng thái trước khi hoàn tất khởi tạo");
            }

            int totalParts = (int) Math.ceil((double) request.sizeBytes() / chunkSize);
            List<UploadTargetDto> targets = new ArrayList<>(totalParts);

            for (int partNum = 1; partNum <= totalParts; partNum++) {
                long partLength = (partNum == totalParts)
                        ? (request.sizeBytes() - (long) (partNum - 1) * chunkSize)
                        : chunkSize;

                PresignedUrlResponse partRes = objectStorage.presignUploadPart(rawKey, uploadId, partNum, partLength, ttl);
                targets.add(new UploadTargetDto(partNum, "PUT", partRes.url(), partRes.signedHeaders()));
            }

            return new InitUploadResponse(
                    asset.getId(),
                    MediaStatus.UPLOADING,
                    asset.getKind(),
                    "MULTIPART",
                    chunkSize,
                    totalParts,
                    calculateExpiresAt(asset),
                    ttl.toSeconds(),
                    targets
            );
        }
    }

    @Override
    public UploadStatusResponse getUploadStatus(Long userId, Long mediaAssetId) {
        MediaAsset asset = mediaAssetRepository.findById(mediaAssetId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));

        if (!asset.getUploaderId().equals(userId)) {
            throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
        }

        int chunkSize = getChunkSize();

        // 1. Phân loại các trạng thái kết thúc TRƯỚC isExpired
        if (asset.getStatus() == MediaStatus.FAILED || asset.getStatus() == MediaStatus.DELETED || asset.getStatus() == MediaStatus.PURGED) {
            throw new AppException(ErrorCode.MEDIA_DELETED, "Tệp đã bị xóa hoặc hủy tải lên");
        }
        if (asset.getStatus() == MediaStatus.BLOCKED) {
            throw new AppException(ErrorCode.INVALID_MEDIA_SIGNATURE, "Tệp bị chặn do vi phạm quy tắc an toàn");
        }

        // 2. Phân loại trạng thái đã hoàn tất TRƯỚC isExpired (cho phép xem tệp đã tải xong quá 24h)
        if (asset.getStatus() == MediaStatus.PROCESSING || asset.getStatus() == MediaStatus.AVAILABLE) {
            return new UploadStatusResponse(
                    asset.getId(),
                    asset.getStatus(),
                    asset.getUploadId() != null ? "MULTIPART" : "SINGLE",
                    asset.getUploadId() != null ? (int) Math.ceil((double) asset.getSizeBytes() / chunkSize) : 1,
                    calculateExpiresAt(asset),
                    0,
                    List.of(),
                    List.of()
            );
        }

        // 3. Phân loại trạng thái đang khởi tạo hoặc đang ghép dở
        if (asset.getStatus() == MediaStatus.CREATING || asset.getStatus() == MediaStatus.COMPLETING) {
            throw new AppException(ErrorCode.UPLOAD_IN_PROGRESS, "Tệp đang trong quá trình khởi tạo hoặc ghép mảnh, vui lòng thử lại sau 1-2 giây");
        }

        // 4. Giờ mới kiểm tra hết hạn 24 giờ cho phiên chưa hoàn tất (UPLOADING)
        if (isExpired(asset)) {
            throw new AppException(ErrorCode.UPLOAD_EXPIRED, "Phiên tải lên đã hết hạn sau 24 giờ");
        }

        // 5. Trạng thái UPLOADING: Hỗ trợ Resume khi mất mạng
        Duration ttl = storageProperties.putUrlTtl();

        if (asset.getUploadId() == null) {
            // Single PUT: Ký lại URL PUT mới
            PresignedUrlResponse target = objectStorage.presignSinglePut(asset.getRawKey(), asset.getContentType(), asset.getSizeBytes(), ttl);
            UploadTargetDto targetDto = new UploadTargetDto(1, "PUT", target.url(), target.signedHeaders());

            return new UploadStatusResponse(
                    asset.getId(),
                    MediaStatus.UPLOADING,
                    "SINGLE",
                    1,
                    calculateExpiresAt(asset),
                    ttl.toSeconds(),
                    List.of(),
                    List.of(targetDto)
            );
        } else {
            // Multipart: Gọi listParts từ storage đối chiếu các mảnh đã có và ký lại các mảnh còn thiếu
            List<PartSummaryResponse> s3Parts;
            try {
                s3Parts = objectStorage.listParts(asset.getRawKey(), asset.getUploadId());
            } catch (NoSuchUploadStorageException e) {
                // Kiểm tra lại DB xem /complete có vừa chạy xong đồng thời không
                MediaAsset refreshed = mediaAssetRepository.findById(mediaAssetId).orElse(asset);
                if (refreshed.getStatus() == MediaStatus.PROCESSING || refreshed.getStatus() == MediaStatus.AVAILABLE) {
                    return new UploadStatusResponse(
                            refreshed.getId(),
                            refreshed.getStatus(),
                            "MULTIPART",
                            (int) Math.ceil((double) refreshed.getSizeBytes() / chunkSize),
                            calculateExpiresAt(refreshed),
                            0,
                            List.of(),
                            List.of()
                    );
                }

                // Nếu DB vẫn là UPLOADING và objectStorage.head tồn tại -> trả về UPLOADING với remainingUploadTargets rỗng để client gọi tiếp /complete
                Optional<ObjectMetadataResponse> meta = objectStorage.head(asset.getRawKey());
                if (meta.isPresent()) {
                    return new UploadStatusResponse(
                            asset.getId(),
                            MediaStatus.UPLOADING,
                            "MULTIPART",
                            (int) Math.ceil((double) asset.getSizeBytes() / chunkSize),
                            calculateExpiresAt(asset),
                            0,
                            List.of(),
                            List.of()
                    );
                }
                throw new AppException(ErrorCode.UPLOAD_EXPIRED, "Phiên tải lên không còn tồn tại trên dịch vụ lưu trữ");
            }

            Set<Integer> completedPartNumbers = s3Parts.stream()
                    .map(PartSummaryResponse::partNumber)
                    .collect(Collectors.toSet());

            List<CompletedPartSummaryDto> completedDtos = s3Parts.stream()
                    .map(p -> new CompletedPartSummaryDto(p.partNumber(), p.etag(), p.sizeBytes()))
                    .toList();

            int totalParts = (int) Math.ceil((double) asset.getSizeBytes() / chunkSize);
            List<UploadTargetDto> remainingTargets = new ArrayList<>();

            for (int partNum = 1; partNum <= totalParts; partNum++) {
                if (!completedPartNumbers.contains(partNum)) {
                    long partLength = (partNum == totalParts)
                            ? (asset.getSizeBytes() - (long) (partNum - 1) * chunkSize)
                            : chunkSize;

                    PresignedUrlResponse partRes = objectStorage.presignUploadPart(asset.getRawKey(), asset.getUploadId(), partNum, partLength, ttl);
                    remainingTargets.add(new UploadTargetDto(partNum, "PUT", partRes.url(), partRes.signedHeaders()));
                }
            }

            return new UploadStatusResponse(
                    asset.getId(),
                    MediaStatus.UPLOADING,
                    "MULTIPART",
                    totalParts,
                    calculateExpiresAt(asset),
                    ttl.toSeconds(),
                    completedDtos,
                    remainingTargets
            );
        }
    }

    @Override
    public CompleteUploadResponse completeUpload(Long userId, Long mediaAssetId, CompleteUploadRequest request) {
        MediaAsset asset = mediaAssetRepository.findById(mediaAssetId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));

        if (!asset.getUploaderId().equals(userId)) {
            throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
        }

        // Kiểm tra quyền thành viên phòng chat
        roomAccessService.requireActiveMember(userId, asset.getRoomId());

        // Kiểm tra trạng thái đã hoàn tất TRƯỚC isExpired
        if (asset.getStatus() == MediaStatus.PROCESSING || asset.getStatus() == MediaStatus.AVAILABLE) {
            return new CompleteUploadResponse(asset.getId(), asset.getStatus(), asset.getKind());
        }

        // Kiểm tra các trạng thái kết thúc TRƯỚC isExpired
        if (asset.getStatus() == MediaStatus.FAILED || asset.getStatus() == MediaStatus.DELETED || asset.getStatus() == MediaStatus.PURGED) {
            throw new AppException(ErrorCode.MEDIA_DELETED, "Tệp đã bị xóa hoặc hủy tải lên");
        }
        if (asset.getStatus() == MediaStatus.BLOCKED) {
            throw new AppException(ErrorCode.INVALID_MEDIA_SIGNATURE, "Tệp bị chặn do vi phạm quy tắc an toàn");
        }

        // Kiểm tra hết hạn cho phiên chưa hoàn tất
        if (isExpired(asset)) {
            throw new AppException(ErrorCode.UPLOAD_EXPIRED, "Phiên tải lên đã hết hạn");
        }

        // 1. Atomic Claim UPLOADING -> COMPLETING trong Tx ngắn
        Instant notExpiredAfter = Instant.now().minus(SESSION_TTL);
        int claimed = transactionTemplate.execute(status ->
                mediaAssetRepository.claimForCompleting(mediaAssetId, userId, Instant.now(), notExpiredAfter));

        if (claimed == 0) {
            MediaAsset refreshed = mediaAssetRepository.findById(mediaAssetId).orElseThrow();
            if (refreshed.getStatus() == MediaStatus.PROCESSING || refreshed.getStatus() == MediaStatus.AVAILABLE) {
                return new CompleteUploadResponse(refreshed.getId(), refreshed.getStatus(), refreshed.getKind());
            }
            if (refreshed.getStatus() == MediaStatus.COMPLETING) {
                throw new AppException(ErrorCode.UPLOAD_IN_PROGRESS, "Tệp đang trong quá trình xử lý, vui lòng thử lại sau 1-2 giây");
            }
            throw new AppException(ErrorCode.MEDIA_NOT_READY, "Trạng thái tệp không hợp lệ để hoàn tất: " + refreshed.getStatus());
        }

        int chunkSize = getChunkSize();
        boolean transitioned = false;

        try {
            // 2. Nếu là Multipart: Đối chiếu mảnh và hoàn tất ghép ngoài Tx
            if (asset.getUploadId() != null) {
                int expectedTotalParts = (int) Math.ceil((double) asset.getSizeBytes() / chunkSize);

                if (request == null || request.parts() == null || request.parts().isEmpty()) {
                    transitioned = true;
                    revertToUploading(asset.getId());
                    throw new AppException(ErrorCode.UPLOAD_INCOMPLETE, "Danh sách mảnh hoàn tất không được để trống");
                }

                if (request.parts().size() != expectedTotalParts) {
                    transitioned = true;
                    revertToUploading(asset.getId());
                    throw new AppException(ErrorCode.UPLOAD_INCOMPLETE,
                            "Số lượng mảnh trong yêu cầu (" + request.parts().size() + ") không khớp với số mảnh kỳ vọng (" + expectedTotalParts + ")");
                }

                Set<Integer> clientPartNumbers = new HashSet<>();
                for (CompletedPartDto partDto : request.parts()) {
                    if (partDto == null || partDto.partNumber() <= 0 || !clientPartNumbers.add(partDto.partNumber())) {
                        transitioned = true;
                        revertToUploading(asset.getId());
                        throw new AppException(ErrorCode.UPLOAD_INCOMPLETE, "Danh sách mảnh chứa partNumber không hợp lệ hoặc bị trùng lặp: " + (partDto != null ? partDto.partNumber() : null));
                    }
                }

                for (int p = 1; p <= expectedTotalParts; p++) {
                    if (!clientPartNumbers.contains(p)) {
                        transitioned = true;
                        revertToUploading(asset.getId());
                        throw new AppException(ErrorCode.UPLOAD_INCOMPLETE, "Thiếu mảnh số " + p + " trong yêu cầu hoàn tất");
                    }
                }

                List<PartSummaryResponse> s3Parts = null;
                boolean alreadyMerged = false;

                try {
                    s3Parts = objectStorage.listParts(asset.getRawKey(), asset.getUploadId());
                } catch (NoSuchUploadStorageException e) {
                    // Nếu gặp NoSuchUpload, kiểm tra file trên storage xem đã ghép thành công trước đó chưa
                    Optional<ObjectMetadataResponse> meta = objectStorage.head(asset.getRawKey());
                    if (meta.isPresent()) {
                        alreadyMerged = true;
                    } else {
                        transitioned = true;
                        revertToUploading(asset.getId());
                        throw new AppException(ErrorCode.UPLOAD_EXPIRED, "Phiên tải lên không còn tồn tại trên dịch vụ lưu trữ");
                    }
                }

                if (!alreadyMerged) {
                    if (s3Parts.size() != expectedTotalParts) {
                        transitioned = true;
                        revertToUploading(asset.getId());
                        throw new AppException(ErrorCode.UPLOAD_INCOMPLETE,
                                "Số lượng mảnh trên lưu trữ không khớp (đã có " + s3Parts.size() + "/" + expectedTotalParts + " mảnh)");
                    }

                    // Kiểm tra các mảnh trên S3 phải liên tục từ 1 đến expectedTotalParts
                    Set<Integer> partNumbers = s3Parts.stream().map(PartSummaryResponse::partNumber).collect(Collectors.toSet());
                    for (int p = 1; p <= expectedTotalParts; p++) {
                        if (!partNumbers.contains(p)) {
                            transitioned = true;
                            revertToUploading(asset.getId());
                            throw new AppException(ErrorCode.UPLOAD_INCOMPLETE, "Thiếu mảnh số " + p + " trên dịch vụ lưu trữ");
                        }
                    }

                    // Đối chiếu ETag client gửi lên với ETag lưu trên S3 (bỏ dấu nháy kép)
                    Map<Integer, String> clientPartMap = request.parts().stream()
                            .collect(Collectors.toMap(CompletedPartDto::partNumber, CompletedPartDto::etag, (a, b) -> a));

                    for (PartSummaryResponse s3Part : s3Parts) {
                        String clientEtag = clientPartMap.get(s3Part.partNumber());
                        if (clientEtag == null || !cleanEtag(clientEtag).equalsIgnoreCase(cleanEtag(s3Part.etag()))) {
                            transitioned = true;
                            blockAndCleanup(asset.getId(), asset.getRawKey(), asset.getUploadId(),
                                    "ETag mảnh " + s3Part.partNumber() + " không khớp");
                            throw new AppException(ErrorCode.UPLOAD_SIZE_MISMATCH, "ETag của mảnh không khớp với dữ liệu đã tải lên");
                        }
                    }

                    // Kiểm tra kích thước từng mảnh chống gian lận
                    long totalUploadedBytes = 0;
                    for (PartSummaryResponse p : s3Parts) {
                        long expectedPartLength = (p.partNumber() == expectedTotalParts)
                                ? (asset.getSizeBytes() - (long) (p.partNumber() - 1) * chunkSize)
                                : chunkSize;

                        if (p.sizeBytes() != expectedPartLength) {
                            transitioned = true;
                            blockAndCleanup(asset.getId(), asset.getRawKey(), asset.getUploadId(),
                                    "Mảnh " + p.partNumber() + " có kích thước không hợp lệ: " + p.sizeBytes());
                            throw new AppException(ErrorCode.UPLOAD_SIZE_MISMATCH, "Kích thước mảnh không hợp lệ");
                        }
                        totalUploadedBytes += p.sizeBytes();
                    }

                    if (totalUploadedBytes != asset.getSizeBytes()) {
                        transitioned = true;
                        blockAndCleanup(asset.getId(), asset.getRawKey(), asset.getUploadId(),
                                "Tổng kích thước các mảnh (" + totalUploadedBytes + ") không khớp với khai báo (" + asset.getSizeBytes() + ")");
                        throw new AppException(ErrorCode.UPLOAD_SIZE_MISMATCH, "Tổng dung lượng các mảnh không khớp");
                    }

                    // Ghép mảnh S3
                    List<CompletedPartRequest> completedParts = s3Parts.stream()
                            .sorted(Comparator.comparingInt(PartSummaryResponse::partNumber))
                            .map(p -> new CompletedPartRequest(p.partNumber(), p.etag()))
                            .toList();

                    objectStorage.completeMultipartUpload(asset.getRawKey(), asset.getUploadId(), completedParts);
                }
            }

            // 3. Kiểm tra dung lượng thực tế qua headObject
            Optional<ObjectMetadataResponse> meta = objectStorage.head(asset.getRawKey());
            if (meta.isEmpty()) {
                transitioned = true;
                revertToUploading(asset.getId());
                throw new AppException(ErrorCode.STORAGE_ERROR, "Không tìm thấy tệp trên dịch vụ lưu trữ sau khi tải lên");
            }

            if (meta.get().contentLength() != asset.getSizeBytes()) {
                transitioned = true;
                blockAndCleanup(asset.getId(), asset.getRawKey(), asset.getUploadId(),
                        "Dung lượng thực tế trên storage (" + meta.get().contentLength() + ") khác với khai báo (" + asset.getSizeBytes() + ")");
                throw new AppException(ErrorCode.UPLOAD_SIZE_MISMATCH, "Dung lượng tệp thực tế không khớp với khai báo");
            }

            // 4. Ngửi Magic Bytes qua 8 KiB đầu tiên
            byte[] magicBytes = objectStorage.readRange(asset.getRawKey(), 0, 8191);
            Optional<MediaSniffer.SniffResult> sniffResult = MediaSniffer.sniff(magicBytes);

            if (sniffResult.isEmpty() || !MediaSniffer.matchesDeclaredKind(sniffResult.get(), asset.getKind())) {
                transitioned = true;
                blockAndCleanup(asset.getId(), asset.getRawKey(), asset.getUploadId(),
                        "Chữ ký tệp thực tế không hợp lệ hoặc không khớp nhóm loại đã khai");
                throw new AppException(ErrorCode.INVALID_MEDIA_SIGNATURE, "Nội dung tệp không khớp với định dạng khai báo");
            }

            // 5. Kiểm tra Whitelist vai trò và loại phòng
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            ChatRoom room = roomAccessService.requireActiveRoom(asset.getRoomId());
            if (!MediaSniffer.isAllowed(sniffResult.get(), user.getRole(), room.getRoomType())) {
                transitioned = true;
                blockAndCleanup(asset.getId(), asset.getRawKey(), asset.getUploadId(),
                        "Định dạng tệp (" + sniffResult.get().contentType() + ") không được phép đối với vai trò hoặc loại phòng này");
                throw new AppException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Định dạng tệp không được hỗ trợ");
            }

            // 6. Chấp nhận tệp -> Cập nhật sang PROCESSING bằng UPDATE có điều kiện trong Tx ngắn
            int updated = transactionTemplate.execute(status ->
                    mediaAssetRepository.updateStatusToProcessing(mediaAssetId, sniffResult.get().contentType(), Instant.now()));

            if (updated == 0) {
                transitioned = true;
                throw new AppException(ErrorCode.UPLOAD_IN_PROGRESS, "Tệp đang trong quá trình xử lý, vui lòng thử lại sau 1-2 giây");
            }

            transitioned = true;
            return new CompleteUploadResponse(asset.getId(), MediaStatus.PROCESSING, asset.getKind());

        } catch (AppException e) {
            if (!transitioned) {
                revertToUploading(asset.getId());
                transitioned = true;
            }
            throw e;
        } catch (Exception e) {
            log.error("Lỗi storage khi hoàn tất tải lên asset {}: {}", mediaAssetId, e.getMessage(), e);
            if (!transitioned) {
                revertToUploading(asset.getId());
                transitioned = true;
            }
            // Không rò rỉ message nội bộ của exception ra client
            throw new AppException(ErrorCode.STORAGE_ERROR, "Lỗi dịch vụ lưu trữ khi hoàn tất tệp");
        }
    }

    @Override
    public void abortUpload(Long userId, Long mediaAssetId) {
        MediaAsset asset = mediaAssetRepository.findById(mediaAssetId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));

        if (!asset.getUploaderId().equals(userId)) {
            throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
        }

        if (asset.getStatus() == MediaStatus.FAILED || asset.getStatus() == MediaStatus.DELETED || asset.getStatus() == MediaStatus.PURGED) {
            return; // Đã bị hủy trước đó (idempotent)
        }

        if (asset.getStatus() == MediaStatus.COMPLETING) {
            throw new AppException(ErrorCode.UPLOAD_IN_PROGRESS, "Tệp đang trong quá trình ghép, không thể hủy lúc này");
        }

        if (asset.getStatus() == MediaStatus.PROCESSING || asset.getStatus() == MediaStatus.AVAILABLE) {
            throw new AppException(ErrorCode.MEDIA_NOT_READY, "Tệp đã tải lên hoàn tất, không thể hủy");
        }

        boolean wasCreatingWithoutUploadId = (asset.getStatus() == MediaStatus.CREATING && asset.getUploadId() == null);

        // Atomic claim hủy: Chuyển CREATING hoặc UPLOADING sang FAILED trong Tx ngắn trước
        int claimed = transactionTemplate.execute(status ->
                mediaAssetRepository.claimForAbort(mediaAssetId, userId, truncateError("USER_ABORTED"), Instant.now()));

        if (claimed == 0) {
            MediaAsset refreshed = mediaAssetRepository.findById(mediaAssetId).orElseThrow();
            if (refreshed.getStatus() == MediaStatus.COMPLETING) {
                throw new AppException(ErrorCode.UPLOAD_IN_PROGRESS, "Tệp đang trong quá trình ghép, không thể hủy lúc này");
            }
            if (refreshed.getStatus() == MediaStatus.FAILED || refreshed.getStatus() == MediaStatus.DELETED || refreshed.getStatus() == MediaStatus.PURGED) {
                return; // Đã bị hủy trước đó
            }
            throw new AppException(ErrorCode.MEDIA_NOT_READY, "Không thể hủy tệp ở trạng thái: " + refreshed.getStatus());
        }

        // Nếu ban đầu là CREATING và chưa có uploadId, đọc lại DB xem Tx2 của initUpload có kịp ghi uploadId trước khi claimForAbort không
        String uploadIdToAbort = asset.getUploadId();
        if (uploadIdToAbort == null && wasCreatingWithoutUploadId) {
            uploadIdToAbort = mediaAssetRepository.findById(mediaAssetId)
                    .map(MediaAsset::getUploadId)
                    .orElse(null);
        }

        // Xóa dữ liệu storage ngoài Tx (best-effort)
        try {
            if (uploadIdToAbort != null) {
                objectStorage.abortMultipartUpload(asset.getRawKey(), uploadIdToAbort);
            }
            objectStorage.delete(asset.getRawKey());
        } catch (Exception e) {
            log.warn("Lỗi dọn dẹp storage khi hủy upload {}: {}", mediaAssetId, e.getMessage());
        }
    }

    @Override
    public void deleteAsset(Long userId, Long mediaAssetId) {
        MediaAsset asset = mediaAssetRepository.findById(mediaAssetId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));

        Optional<RoomMember> activeMemberOpt = (roomMemberRepository != null)
                ? roomMemberRepository.findByRoomIdAndUserIdAndStatus(asset.getRoomId(), userId, MemberStatus.ACTIVE)
                : Optional.empty();

        boolean isUploader = asset.getUploaderId().equals(userId);
        boolean isActiveMember = activeMemberOpt.isPresent();
        boolean isOwner = isActiveMember && activeMemberOpt.get().getRole() == MemberRole.OWNER;

        MediaStatus status = asset.getStatus();

        // 1. UPLOADING: Chỉ uploaderId được phép gọi (người khác ném MEDIA_NOT_FOUND); tái sử dụng abortUpload
        if (status == MediaStatus.UPLOADING) {
            if (!isUploader) {
                throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
            }
            abortUpload(userId, mediaAssetId);
            return;
        }

        // 2. CREATING hoặc COMPLETING: Chỉ uploader mới được biết trạng thái tệp (người khác ném MEDIA_NOT_FOUND)
        if (status == MediaStatus.CREATING || status == MediaStatus.COMPLETING) {
            if (isUploader) {
                throw new AppException(ErrorCode.UPLOAD_IN_PROGRESS, "Tệp đang trong quá trình xử lý, vui lòng thử lại sau");
            }
            throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
        }

        // 3. DELETED hoặc PURGED: Kiểm tra quyền hợp lệ (uploader HOẶC tệp có messageId != null và caller là OWNER active)
        if (status == MediaStatus.DELETED || status == MediaStatus.PURGED) {
            if (isUploader || (asset.getMessageId() != null && isOwner)) {
                return; // Idempotent thành công
            }
            throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
        }

        // 4. PROCESSING, AVAILABLE, FAILED, BLOCKED:
        if (asset.getMessageId() == null) {
            // Tệp nháp chưa gửi: CHỈ uploaderId được xóa (người khác, kể cả OWNER phòng, ném MEDIA_NOT_FOUND để chống dò ID)
            if (!isUploader) {
                throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
            }
        } else {
            // Tệp đã gắn vào tin nhắn: uploader HOẶC active room OWNER
            if (!isUploader && !isOwner) {
                if (isActiveMember) {
                    throw new AppException(ErrorCode.ACCESS_DENIED, "Chỉ chủ sở hữu tệp hoặc chủ phòng mới có quyền xóa");
                } else {
                    throw new AppException(ErrorCode.MEDIA_NOT_FOUND);
                }
            }
        }

        // Cập nhật nguyên tử sang DELETED trong transaction ngắn
        Instant now = Instant.now();
        Long messageId = asset.getMessageId();
        Long roomId = asset.getRoomId();
        MediaKind kind = asset.getKind();

        Integer updated = transactionTemplate.execute(txStatus -> {
            int count = mediaAssetRepository.markStatusToDeleted(mediaAssetId, now);
            if (count == 1 && (status == MediaStatus.PROCESSING || status == MediaStatus.AVAILABLE)) {
                Long latestMessageId = mediaAssetRepository.findById(mediaAssetId)
                        .map(MediaAsset::getMessageId)
                        .orElse(messageId);
                if (latestMessageId != null && events != null) {
                    events.publishEvent(new RoomEvent(roomId, RoomEventType.MEDIA_STATUS,
                            MediaStatusEventDto.deleted(mediaAssetId, roomId, latestMessageId, kind)));
                }
            }
            return count;
        });

        if (updated == null || updated == 0) {
            MediaAsset refreshed = mediaAssetRepository.findById(mediaAssetId)
                    .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));
            if (refreshed.getStatus() == MediaStatus.DELETED || refreshed.getStatus() == MediaStatus.PURGED) {
                return; // Idempotent khi có request xóa đồng thời
            }
            if (refreshed.getStatus() == MediaStatus.COMPLETING) {
                throw new AppException(ErrorCode.UPLOAD_IN_PROGRESS, "Tệp đang trong quá trình xử lý, vui lòng thử lại sau");
            }
            throw new AppException(ErrorCode.MEDIA_NOT_READY, "Không thể xóa tệp ở trạng thái: " + refreshed.getStatus());
        }
    }

    private InitUploadResponse buildResignedInitResponse(MediaAsset asset, Duration ttl, int chunkSize) {
        if (asset.getUploadId() == null) {
            PresignedUrlResponse target = objectStorage.presignSinglePut(asset.getRawKey(), asset.getContentType(), asset.getSizeBytes(), ttl);
            UploadTargetDto targetDto = new UploadTargetDto(1, "PUT", target.url(), target.signedHeaders());

            return new InitUploadResponse(
                    asset.getId(),
                    asset.getStatus(),
                    asset.getKind(),
                    "SINGLE",
                    chunkSize,
                    1,
                    calculateExpiresAt(asset),
                    ttl.toSeconds(),
                    List.of(targetDto)
            );
        } else {
            int totalParts = (int) Math.ceil((double) asset.getSizeBytes() / chunkSize);
            List<UploadTargetDto> targets = new ArrayList<>(totalParts);

            for (int partNum = 1; partNum <= totalParts; partNum++) {
                long partLength = (partNum == totalParts)
                        ? (asset.getSizeBytes() - (long) (partNum - 1) * chunkSize)
                        : chunkSize;

                PresignedUrlResponse partRes = objectStorage.presignUploadPart(asset.getRawKey(), asset.getUploadId(), partNum, partLength, ttl);
                targets.add(new UploadTargetDto(partNum, "PUT", partRes.url(), partRes.signedHeaders()));
            }

            return new InitUploadResponse(
                    asset.getId(),
                    asset.getStatus(),
                    asset.getKind(),
                    "MULTIPART",
                    chunkSize,
                    totalParts,
                    calculateExpiresAt(asset),
                    ttl.toSeconds(),
                    targets
            );
        }
    }

    private void revertToUploading(Long assetId) {
        try {
            transactionTemplate.execute(status -> {
                mediaAssetRepository.revertStatusToUploading(assetId, Instant.now());
                return null;
            });
        } catch (Exception e) {
            log.warn("Lỗi khi hoàn lại trạng thái UPLOADING cho asset {}: {}", assetId, e.getMessage());
        }
    }

    private void blockAndCleanup(Long assetId, String rawKey, String uploadId, String reason) {
        try {
            transactionTemplate.execute(status -> {
                mediaAssetRepository.updateStatusToBlocked(assetId, truncateError(reason), Instant.now());
                return null;
            });
        } catch (Exception e) {
            log.warn("Lỗi khi đánh dấu BLOCKED cho asset {}: {}", assetId, e.getMessage());
        }

        try {
            if (uploadId != null) {
                objectStorage.abortMultipartUpload(rawKey, uploadId);
            }
            objectStorage.delete(rawKey);
        } catch (Exception e) {
            log.warn("Lỗi khi dọn dẹp storage cho asset bị block {}: {}", assetId, e.getMessage());
        }
    }

    private static String truncateError(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    private boolean isExpired(MediaAsset asset) {
        Instant createdAt = asset.getCreatedAt() != null ? asset.getCreatedAt() : Instant.now();
        return createdAt.plus(SESSION_TTL).isBefore(Instant.now());
    }

    private Instant calculateExpiresAt(MediaAsset asset) {
        Instant createdAt = asset.getCreatedAt() != null ? asset.getCreatedAt() : Instant.now();
        return createdAt.plus(SESSION_TTL);
    }

    private int getChunkSize() {
        return (storageProperties != null && storageProperties.chunkSizeBytes() > 0)
                ? storageProperties.chunkSizeBytes()
                : 5 * 1024 * 1024;
    }

    private static String cleanEtag(String etag) {
        if (etag == null) {
            return "";
        }
        return etag.replace("\"", "").trim();
    }

    private static String resolveExtension(String mime) {
        return switch (mime) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/avif" -> "avif";
            case "image/gif" -> "gif";
            case "video/mp4" -> "mp4";
            case "video/quicktime" -> "mov";
            default -> "bin";
        };
    }

    private static TransactionTemplate createPassThroughTransactionTemplate() {
        return new TransactionTemplate(new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        });
    }
}
