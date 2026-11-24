package com.ChattingSystem.media.util;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Tiện ích sinh đường dẫn lưu trữ (Object Key) chuẩn hóa và an toàn trên Cloud Storage.
 */
public final class ObjectKeys {

    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private ObjectKeys() {
    }

    /**
     * Đường dẫn lưu tệp gốc người dùng tải lên: raw/{yyyy}/{MM}/{dd}/{uuid}.{ext}
     */
    public static String rawKey(UUID clientUploadId, String extension) {
        return buildKey("raw", clientUploadId, extension);
    }

    /**
     * Đường dẫn tệp hiển thị sau khi nén/hậu kỳ: display/{yyyy}/{MM}/{dd}/{uuid}.{ext}
     */
    public static String displayKey(UUID clientUploadId, String extension) {
        return buildKey("display", clientUploadId, extension);
    }

    /**
     * Đường dẫn ảnh thu nhỏ thumbnail: thumb/{yyyy}/{MM}/{dd}/{uuid}.webp
     */
    public static String thumbKey(UUID clientUploadId) {
        return buildKey("thumb", clientUploadId, "webp");
    }

    private static String buildKey(String prefix, UUID id, String extension) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        Objects.requireNonNull(id, "id must not be null");

        String cleanExt = (extension == null || extension.isBlank())
                ? "bin"
                : extension.trim().replaceFirst("^\\.", "");

        String datePath = LocalDate.now(ZoneOffset.UTC).format(DATE_PATH_FORMATTER);
        return String.format("%s/%s/%s.%s", prefix, datePath, id, cleanExt);
    }
}
