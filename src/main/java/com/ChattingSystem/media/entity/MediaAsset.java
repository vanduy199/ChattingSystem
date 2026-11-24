package com.ChattingSystem.media.entity;

import com.ChattingSystem.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "media_assets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaAsset extends BaseEntity {

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "client_upload_id", nullable = false)
    private UUID clientUploadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private MediaKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality", nullable = false, length = 10)
    private MediaQuality quality = MediaQuality.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MediaStatus status;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "upload_id", length = 1024)
    private String uploadId;

    @Column(name = "raw_key", nullable = false, length = 500)
    private String rawKey;

    @Column(name = "display_key", length = 500)
    private String displayKey;

    @Column(name = "thumb_key", length = 500)
    private String thumbKey;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private MediaAsset(Long uploaderId,
                       Long roomId,
                       UUID clientUploadId,
                       MediaKind kind,
                       MediaQuality quality,
                       MediaStatus status,
                       String contentType,
                       Long sizeBytes,
                       String rawKey,
                       String uploadId) {
        this.uploaderId = Objects.requireNonNull(uploaderId, "uploaderId must not be null");
        this.roomId = Objects.requireNonNull(roomId, "roomId must not be null");
        this.clientUploadId = Objects.requireNonNull(clientUploadId, "clientUploadId must not be null");
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.quality = (quality != null) ? quality : MediaQuality.STANDARD;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.sizeBytes = Objects.requireNonNull(sizeBytes, "sizeBytes must not be null");
        this.rawKey = Objects.requireNonNull(rawKey, "rawKey must not be null");
        this.uploadId = uploadId;
    }

    /**
     * Static Factory method cho tệp nhỏ (≤ 5MB) tải lên 1 lần (Single PUT).
     */
    public static MediaAsset singleUpload(Long uploaderId,
                                          Long roomId,
                                          UUID clientUploadId,
                                          MediaKind kind,
                                          MediaQuality quality,
                                          String contentType,
                                          long sizeBytes,
                                          String rawKey) {
        return new MediaAsset(uploaderId, roomId, clientUploadId, kind, quality,
                MediaStatus.UPLOADING, contentType, sizeBytes, rawKey, null);
    }

    /**
     * Static Factory method cho tệp lớn (> 5MB) chuẩn bị khởi tạo Multipart Upload (CREATING).
     * Hỗ trợ mô hình 2 transaction ngắn để không giữ DB connection khi gọi sang Storage.
     */
    public static MediaAsset creating(Long uploaderId,
                                      Long roomId,
                                      UUID clientUploadId,
                                      MediaKind kind,
                                      MediaQuality quality,
                                      String contentType,
                                      long sizeBytes,
                                      String rawKey) {
        return new MediaAsset(uploaderId, roomId, clientUploadId, kind, quality,
                MediaStatus.CREATING, contentType, sizeBytes, rawKey, null);
    }

    /**
     * Static Factory method cho tệp lớn (> 5MB) đã có sẵn uploadId (Multipart Upload).
     */
    public static MediaAsset multipartUpload(Long uploaderId,
                                             Long roomId,
                                             UUID clientUploadId,
                                             MediaKind kind,
                                             MediaQuality quality,
                                             String contentType,
                                             long sizeBytes,
                                             String rawKey,
                                             String uploadId) {
        return new MediaAsset(uploaderId, roomId, clientUploadId, kind, quality,
                MediaStatus.UPLOADING, contentType, sizeBytes, rawKey,
                Objects.requireNonNull(uploadId, "uploadId must not be null for multipart upload"));
    }

    // === Domain Business Methods ===

    public void markCompleting() {
        this.status = MediaStatus.COMPLETING;
    }

    public void markUploading() {
        this.status = MediaStatus.UPLOADING;
    }

    public void markUploading(String uploadId) {
        this.status = MediaStatus.UPLOADING;
        this.uploadId = Objects.requireNonNull(uploadId, "uploadId must not be null");
    }

    public void markProcessing(String detectedContentType) {
        this.status = MediaStatus.PROCESSING;
        if (detectedContentType != null && !detectedContentType.isBlank()) {
            this.contentType = detectedContentType;
        }
        this.nextRunAt = Instant.now();
        this.attempts = 0;
        this.lockedUntil = null;
    }

    public void markAvailable(String displayKey, String thumbKey, Integer width, Integer height, Integer durationMs) {
        this.status = MediaStatus.AVAILABLE;
        this.displayKey = displayKey;
        this.thumbKey = thumbKey;
        this.width = width;
        this.height = height;
        this.durationMs = durationMs;
    }

    public void markAvailable(String displayKey, String thumbKey, Integer width, Integer height) {
        markAvailable(displayKey, thumbKey, width, height, null);
    }

    public void markPurged() {
        this.status = MediaStatus.PURGED;
    }

    public void markFailed(String error) {
        this.status = MediaStatus.FAILED;
        this.lastError = truncateError(error);
    }

    public void markBlocked(String reason) {
        this.status = MediaStatus.BLOCKED;
        this.lastError = truncateError(reason);
    }

    public void attachToMessage(Long messageId, int sortOrder) {
        this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }
        this.sortOrder = sortOrder;
    }

    public void softDelete() {
        this.status = MediaStatus.DELETED;
        this.deletedAt = Instant.now();
    }

    private static String truncateError(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
