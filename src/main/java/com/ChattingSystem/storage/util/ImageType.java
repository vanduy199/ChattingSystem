package com.ChattingSystem.storage.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** Allowed upload image formats, detected from the file's magic bytes (client content type is not trusted). */
public enum ImageType {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp"),
    AVIF("image/avif", "avif");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final String contentType;
    private final String extension;

    ImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageType> detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return Optional.empty();
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (ascii(bytes, 0, 6).equals("GIF87a") || ascii(bytes, 0, 6).equals("GIF89a")) {
            return Optional.of(GIF);
        }
        if (ascii(bytes, 0, 4).equals("RIFF") && ascii(bytes, 8, 4).equals("WEBP")) {
            return Optional.of(WEBP);
        }
        if (isAvif(bytes)) {
            return Optional.of(AVIF);
        }
        return Optional.empty();
    }

    /** ISO-BMFF {@code ftyp} box whose major or compatible brands include {@code avif} / {@code avis}. */
    private static boolean isAvif(byte[] bytes) {
        if (!ascii(bytes, 4, 4).equals("ftyp")) {
            return false;
        }
        long boxSize = ((bytes[0] & 0xFFL) << 24) | ((bytes[1] & 0xFFL) << 16)
                | ((bytes[2] & 0xFFL) << 8) | (bytes[3] & 0xFFL);
        int end = (int) Math.min(boxSize, bytes.length);
        // major brand at 8, minor version at 12, compatible brands from 16
        for (int offset = 8; offset + 4 <= end; offset += (offset == 8 ? 8 : 4)) {
            String brand = ascii(bytes, offset, 4);
            if (brand.equals("avif") || brand.equals("avis")) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] prefix) {
        return bytes.length >= offset + prefix.length
                && Arrays.equals(bytes, offset, offset + prefix.length, prefix, 0, prefix.length);
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        if (bytes.length < offset + length) {
            return "";
        }
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }
}
