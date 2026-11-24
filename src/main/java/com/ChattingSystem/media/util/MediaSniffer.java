package com.ChattingSystem.media.util;

import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.storage.util.ImageType;
import com.ChattingSystem.user.entity.Role;

import java.util.Optional;
import java.util.Set;

/**
 * Tiện ích kiểm tra chữ ký nhị phân (Magic Bytes) và chính sách Whitelist định dạng.
 */
public final class MediaSniffer {

    private static final Set<String> GUEST_ALLOWED_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );

    private static final Set<String> USER_PUBLIC_IMAGE_MIME = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/avif", "image/gif"
    );

    private MediaSniffer() {
    }

    public record SniffResult(MediaKind kind, String contentType, String extension) {
    }

    /**
     * Phân tích chữ ký nhị phân của tệp để xác định loại tệp thực tế.
     * Tận dụng ImageType có sẵn cho ảnh, và VideoType cho video.
     */
    public static Optional<SniffResult> sniff(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return Optional.empty();
        }

        // 1. Thử nhận diện ảnh (JPEG, PNG, GIF, WebP, AVIF)
        Optional<ImageType> imageType = ImageType.detect(bytes);
        if (imageType.isPresent()) {
            return Optional.of(new SniffResult(
                    MediaKind.IMAGE,
                    imageType.get().contentType(),
                    imageType.get().extension()
            ));
        }

        // 2. Thử nhận diện video (MP4, QuickTime MOV)
        Optional<VideoType> videoType = VideoType.detect(bytes);
        if (videoType.isPresent()) {
            return Optional.of(new SniffResult(
                    MediaKind.VIDEO,
                    videoType.get().contentType(),
                    videoType.get().extension()
            ));
        }

        return Optional.empty();
    }

    /**
     * Kiểm tra tệp sau khi sniff Magic Bytes có thuộc Whitelist theo vai trò người dùng và loại phòng hay không.
     */
    public static boolean isAllowed(SniffResult result, Role userRole, String roomType) {
        if (result == null || userRole == null || roomType == null) {
            return false;
        }
        return isDeclaredMimeAllowed(result.contentType(), userRole, roomType);
    }

    /**
     * Kiểm tra MIME type khai báo lúc khởi tạo có thuộc Whitelist theo vai trò người dùng và loại phòng hay không.
     */
    public static boolean isDeclaredMimeAllowed(String normalizedMime, Role userRole, String roomType) {
        if (normalizedMime == null || userRole == null || roomType == null) {
            return false;
        }

        String mime = normalizedMime.trim().toLowerCase();
        boolean isPublic = "public".equalsIgnoreCase(roomType.trim());

        // 1. Người dùng Khách (Guest): Chỉ phòng public, chỉ ảnh cơ bản (jpeg, png, webp), cấm video
        if (userRole == Role.GUEST) {
            if (!isPublic) {
                return false;
            }
            return GUEST_ALLOWED_MIME.contains(mime);
        }

        // 2. Người dùng đã đăng nhập (USER, ADMIN)
        if (USER_PUBLIC_IMAGE_MIME.contains(mime)) {
            return true;
        }

        if ("video/mp4".equals(mime)) {
            return true;
        }

        if ("video/quicktime".equals(mime)) {
            // Chỉ cho phép video QuickTime MOV trong phòng riêng tư (private)
            return "private".equalsIgnoreCase(roomType.trim());
        }

        return false;
    }

    /**
     * Kiểm tra định dạng phát hiện có khớp với nhóm loại đã khai báo lúc khởi tạo hay không.
     */
    public static boolean matchesDeclaredKind(SniffResult result, MediaKind declaredKind) {
        return result != null && declaredKind != null && result.kind() == declaredKind;
    }
}
