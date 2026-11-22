package com.ChattingSystem.media.worker.service.impl;

import com.ChattingSystem.media.dto.response.MediaStatusEventDto;
import com.ChattingSystem.media.entity.MediaAsset;
import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.media.entity.MediaQuality;
import com.ChattingSystem.media.entity.MediaStatus;
import com.ChattingSystem.media.repository.MediaAssetRepository;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.media.util.ObjectKeys;
import com.ChattingSystem.media.worker.exception.MediaValidationException;
import com.ChattingSystem.media.worker.runner.FfmpegRunner;
import com.ChattingSystem.media.worker.runner.dto.MediaProbeResult;
import com.ChattingSystem.media.worker.service.MediaProcessor;
import com.ChattingSystem.realtime.event.RoomEvent;
import com.ChattingSystem.realtime.event.RoomEventType;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.MediaWorkerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class MediaProcessorImpl implements MediaProcessor {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessorImpl.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final MediaAssetRepository mediaAssetRepo;
    private final ObjectStorage objectStorage;
    private final FfmpegRunner ffmpegRunner;
    private final MediaWorkerProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate txTemplate;

    public MediaProcessorImpl(MediaAssetRepository mediaAssetRepo,
                              ObjectStorage objectStorage,
                              FfmpegRunner ffmpegRunner,
                              MediaWorkerProperties properties,
                              ApplicationEventPublisher events,
                              TransactionTemplate txTemplate) {
        this.mediaAssetRepo = mediaAssetRepo;
        this.objectStorage = objectStorage;
        this.ffmpegRunner = ffmpegRunner;
        this.properties = properties;
        this.events = events;
        this.txTemplate = txTemplate;
    }

    @Override
    public void process(Long mediaAssetId) {
        Objects.requireNonNull(mediaAssetId, "mediaAssetId must not be null");

        // 1. Đọc MediaAsset ngoài transaction dài; nếu không tồn tại hoặc status != PROCESSING thì bỏ qua
        Optional<MediaAsset> assetOpt = mediaAssetRepo.findById(mediaAssetId);
        if (assetOpt.isEmpty()) {
            log.warn("MediaAsset with id {} not found, skipping processing", mediaAssetId);
            return;
        }

        MediaAsset asset = assetOpt.get();
        if (asset.getStatus() != MediaStatus.PROCESSING) {
            log.info("MediaAsset with id {} is not in PROCESSING status (current: {}), skipping",
                    mediaAssetId, asset.getStatus());
            return;
        }

        Long roomId = asset.getRoomId();
        Long messageId = asset.getMessageId();
        UUID clientUploadId = asset.getClientUploadId();
        MediaKind kind = asset.getKind();
        MediaQuality quality = asset.getQuality();
        String rawKey = asset.getRawKey();
        String originalContentType = asset.getContentType();

        // 2. Tạo thư mục tạm riêng cho job
        Path workDir = Path.of(properties.tempDir(), mediaAssetId + "_" + UUID.randomUUID());
        try {
            Files.createDirectories(workDir);

            // 3. Tải tệp raw từ Storage về máy chủ
            String rawFileName = rawKey.contains("/") ? rawKey.substring(rawKey.lastIndexOf('/') + 1) : rawKey;
            Path rawPath = workDir.resolve("raw_" + rawFileName);
            objectStorage.downloadToTemp(rawKey, rawPath, properties.maxDownloadBytes());

            // 4. Probe kiểm tra an toàn tệp raw
            MediaProbeResult rawProbe;
            try {
                rawProbe = ffmpegRunner.probe(rawPath);
            } catch (MediaValidationException e) {
                handleBlocked(asset, e.getMessage(), rawKey);
                return;
            }

            int width = rawProbe.width();
            int height = rawProbe.height();
            if (width <= 0 || height <= 0) {
                handleBlocked(asset, "Không thể xác định kích thước hợp lệ của tệp phương tiện", rawKey);
                return;
            }

            long totalPixels = (long) width * (long) height;
            if (totalPixels > properties.maxPixels()) {
                handleBlocked(asset, String.format("Kích thước tệp (%dx%d = %d px) vượt quá giới hạn tối đa (%d px)",
                        width, height, totalPixels, properties.maxPixels()), rawKey);
                return;
            }

            if (kind == MediaKind.VIDEO) {
                if (!rawProbe.hasVideo()) {
                    handleBlocked(asset, "Tệp video không chứa luồng hình ảnh hợp lệ", rawKey);
                    return;
                }
                Integer durationMs = rawProbe.durationMs();
                if (durationMs != null && durationMs > properties.maxVideoDurationSeconds() * 1000L) {
                    handleBlocked(asset, String.format("Thời lượng video (%d ms) vượt quá giới hạn tối đa (%d giây)",
                            durationMs, properties.maxVideoDurationSeconds()), rawKey);
                    return;
                }
            }

            // 5. Xử lý chuyển đổi theo định dạng
            Path displayPath;
            Path thumbPath = workDir.resolve("thumb.webp");
            String displayMime;
            String displayExt;

            boolean isGif = "image/gif".equalsIgnoreCase(originalContentType);

            if (isGif) {
                // Ảnh GIF: copy rawPath giữ nguyên animation làm display (.gif), trích frame đầu làm thumb (.webp)
                displayPath = workDir.resolve("display.gif");
                Files.copy(rawPath, displayPath, StandardCopyOption.REPLACE_EXISTING);
                ffmpegRunner.extractThumbnail(rawPath, thumbPath, false, null);
                displayMime = "image/gif";
                displayExt = "gif";
            } else if (kind == MediaKind.IMAGE) {
                // Ảnh tĩnh: convert sang .webp theo chất lượng (Standard hoặc HD)
                displayPath = workDir.resolve("display.webp");
                int maxSize = (quality == MediaQuality.HD) ? 4096 : 2048;
                int qualityVal = (quality == MediaQuality.HD) ? 92 : 82;
                ffmpegRunner.convertImage(rawPath, displayPath, maxSize, qualityVal);
                ffmpegRunner.extractThumbnail(rawPath, thumbPath, false, null);
                displayMime = "image/webp";
                displayExt = "webp";
            } else {
                // Video: kiểm tra xem có thể remux giữ nguyên không hay phải transcode
                displayPath = workDir.resolve("display.mp4");
                boolean canRemux = "h264".equalsIgnoreCase(rawProbe.videoCodec())
                        && "yuv420p".equalsIgnoreCase(rawProbe.pixelFormat())
                        && rawProbe.rotation() == 0
                        && rawProbe.width() <= 1920
                        && rawProbe.height() <= 1920
                        && (!rawProbe.hasAudio() || "aac".equalsIgnoreCase(rawProbe.audioCodec()));

                if (canRemux) {
                    try {
                        ffmpegRunner.remuxVideo(rawPath, displayPath);
                    } catch (AppException e) {
                        log.warn("Remux video failed for asset {}, falling back to transcode: {}", mediaAssetId, e.getMessage());
                        ffmpegRunner.transcodeVideo(rawPath, displayPath);
                    }
                } else {
                    ffmpegRunner.transcodeVideo(rawPath, displayPath);
                }
                // Trích thumbnail từ displayPath để đồng bộ tỷ lệ và định dạng
                ffmpegRunner.extractThumbnail(displayPath, thumbPath, true, rawProbe.durationMs());
                displayMime = "video/mp4";
                displayExt = "mp4";
            }

            // 6. Probe lại displayPath để lấy chính xác kích thước và thời lượng cuối cùng
            MediaProbeResult displayProbe = ffmpegRunner.probe(displayPath);
            int finalWidth = displayProbe.width() > 0 ? displayProbe.width() : rawProbe.width();
            int finalHeight = displayProbe.height() > 0 ? displayProbe.height() : rawProbe.height();

            // Không lưu durationMs cho ảnh (MediaKind.IMAGE)
            Integer calcDurationMs = null;
            if (kind == MediaKind.VIDEO) {
                calcDurationMs = displayProbe.durationMs() != null ? displayProbe.durationMs() : rawProbe.durationMs();
                if (calcDurationMs != null && calcDurationMs > properties.maxVideoDurationSeconds() * 1000L) {
                    handleBlocked(asset, String.format("Thời lượng video (%d ms) vượt quá giới hạn tối đa (%d giây)",
                            calcDurationMs, properties.maxVideoDurationSeconds()), rawKey);
                    return;
                }
            }
            final Integer finalDurationMs = calcDurationMs;

            // 7. Upload display và thumb lên Storage
            String displayKey = ObjectKeys.displayKey(clientUploadId, displayExt);
            String thumbKey = ObjectKeys.thumbKey(clientUploadId);

            objectStorage.putFile(displayKey, displayPath, displayMime);
            objectStorage.putFile(thumbKey, thumbPath, "image/webp");

            // 8. Transaction ngắn: Cập nhật DB sang AVAILABLE và bắn RoomEvent
            Instant now = Instant.now();
            Boolean updatedSuccess = txTemplate.execute(status -> {
                int updated = mediaAssetRepo.markProcessingAvailable(
                        mediaAssetId,
                        displayKey,
                        thumbKey,
                        displayMime,
                        finalWidth,
                        finalHeight,
                        finalDurationMs,
                        now
                );

                if (updated == 1) {
                    // Lấy messageId mới nhất nếu tin nhắn được gửi trong lúc worker đang xử lý
                    Long latestMessageId = mediaAssetRepo.findById(mediaAssetId)
                            .map(MediaAsset::getMessageId)
                            .orElse(messageId);

                    MediaStatusEventDto eventDto = MediaStatusEventDto.available(
                            mediaAssetId,
                            roomId,
                            latestMessageId,
                            kind,
                            finalWidth,
                            finalHeight,
                            finalDurationMs
                    );
                    events.publishEvent(new RoomEvent(roomId, RoomEventType.MEDIA_STATUS, eventDto));
                    return true;
                }
                return false;
            });

            // 9. Nếu cập nhật thất bại (tệp đã bị xóa trong lúc xử lý) -> dọn best-effort display & thumb đã tải lên Storage
            if (!Boolean.TRUE.equals(updatedSuccess)) {
                log.warn("MediaAsset {} was removed or updated during processing, rolling back uploaded artifacts", mediaAssetId);
                deleteQuietly(displayKey);
                deleteQuietly(thumbKey);
                return;
            }

            // 10. Ngoài transaction: Xóa rawKey trên Storage (best-effort)
            deleteQuietly(rawKey);

        } catch (MediaValidationException e) {
            handleBlocked(asset, e.getMessage(), rawKey);
        } catch (AppException e) {
            if (e.getErrorCode() == ErrorCode.MEDIA_TOO_LARGE) {
                log.warn("MediaAsset {} exceeds max download threshold, marking BLOCKED: {}", mediaAssetId, e.getMessage());
                handleBlocked(asset, e.getMessage(), rawKey);
                return;
            }
            log.error("Storage/FFmpeg error during media processing for asset {}: {}", mediaAssetId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Transient error during media processing for asset {}: {}", mediaAssetId, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR, "Lỗi trong quá trình xử lý media: " + e.getMessage());
        } finally {
            // Luôn dọn dẹp toàn bộ thư mục tạm trên ổ cứng
            deleteRecursivelyQuietly(workDir);
        }
    }

    private void handleBlocked(MediaAsset asset, String failureReason, String rawKey) {
        String safeDbReason = (failureReason == null || failureReason.isBlank()) ? "MEDIA_BLOCKED" : failureReason;
        if (safeDbReason.length() > MAX_ERROR_LENGTH) {
            safeDbReason = safeDbReason.substring(0, MAX_ERROR_LENGTH);
        }
        final String truncatedReason = safeDbReason;
        Instant now = Instant.now();

        txTemplate.executeWithoutResult(status -> {
            int updated = mediaAssetRepo.markProcessingBlocked(asset.getId(), truncatedReason, now);
            if (updated == 1) {
                Long latestMessageId = mediaAssetRepo.findById(asset.getId())
                        .map(MediaAsset::getMessageId)
                        .orElse(asset.getMessageId());

                MediaStatusEventDto eventDto = MediaStatusEventDto.blocked(
                        asset.getId(),
                        asset.getRoomId(),
                        latestMessageId,
                        asset.getKind(),
                        "MEDIA_BLOCKED"
                );
                events.publishEvent(new RoomEvent(asset.getRoomId(), RoomEventType.MEDIA_STATUS, eventDto));
            }
        });

        if (rawKey != null) {
            deleteQuietly(rawKey);
        }
    }

    private void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            objectStorage.delete(key);
        } catch (Exception e) {
            log.warn("Failed to delete key {}: {}", key, e.getMessage());
        }
    }

    private void deleteRecursivelyQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        Files.deleteIfExists(dir);
                    } catch (IOException ignored) {
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warn("Failed to clean up temporary directory {}: {}", path, e.getMessage());
        }
    }
}
