package com.ChattingSystem.media.util;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * Định dạng video được hỗ trợ, nhận diện thông qua chữ ký magic bytes (ISO-BMFF ftyp box).
 */
public enum VideoType {

    MP4("video/mp4", "mp4"),
    MOV("video/quicktime", "mov");

    // Chỉ nhận video MP4, không chứa audio M4A
    private static final Set<String> MP4_BRANDS = Set.of(
            "isom", "iso2", "mp41", "mp42", "avc1", "dash", "M4V ", "MSNV"
    );

    // Các major brands của ảnh HEIC/HEIF/AVIF - chặn ngay để không nhận nhầm thành video qua compatible brand isom/mp41
    private static final Set<String> IMAGE_BRANDS = Set.of(
            "heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1", "avif", "avis"
    );

    private final String contentType;
    private final String extension;

    VideoType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<VideoType> detect(byte[] bytes) {
        if (bytes == null || bytes.length < 16) {
            return Optional.empty();
        }

        // Kiểm tra ISO Base Media File Format: 4 byte từ index 4 phải là "ftyp"
        if (!"ftyp".equals(ascii(bytes, 4, 4))) {
            return Optional.empty();
        }

        long boxSize = ((bytes[0] & 0xFFL) << 24)
                | ((bytes[1] & 0xFFL) << 16)
                | ((bytes[2] & 0xFFL) << 8)
                | (bytes[3] & 0xFFL);

        // Kiểm tra nghiêm ngặt kích thước ftyp box: 16..1024 và chia hết cho 4
        if (boxSize < 16 || boxSize > 1024 || boxSize % 4 != 0) {
            return Optional.empty();
        }

        // Kiểm tra major brand tại offset 8..12: Chặn ngay nếu là ảnh HEIC/HEIF/AVIF
        String majorBrand = ascii(bytes, 8, 4);
        if (IMAGE_BRANDS.contains(majorBrand)) {
            return Optional.empty();
        }

        int end = Math.min(bytes.length, (int) boxSize);

        // major brand tại offset 8, minor version tại 12, compatible brands từ 16 trở đi
        for (int offset = 8; offset + 4 <= end; offset += (offset == 8 ? 8 : 4)) {
            String brand = ascii(bytes, offset, 4);
            if ("qt  ".equals(brand)) {
                return Optional.of(MOV);
            }
            if (MP4_BRANDS.contains(brand)) {
                return Optional.of(MP4);
            }
        }

        return Optional.empty();
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        if (bytes == null || bytes.length < offset + length) {
            return "";
        }
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
