package com.ChattingSystem.storage.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeTest {

    private static byte[] padded(byte[] head) {
        byte[] bytes = new byte[32];
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void detectsSupportedFormats() {
        assertThat(ImageType.detect(padded(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})))
                .contains(ImageType.JPEG);
        assertThat(ImageType.detect(padded(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})))
                .contains(ImageType.PNG);
        assertThat(ImageType.detect(padded(ascii("GIF89a")))).contains(ImageType.GIF);
        assertThat(ImageType.detect(padded(ascii("RIFF\0\0\0\0WEBPVP8 ")))).contains(ImageType.WEBP);
        assertThat(ImageType.detect(padded(ascii("\0\0\0\u0018ftypavif\0\0\0\0mif1")))).contains(ImageType.AVIF);
        assertThat(ImageType.detect(padded(ascii("\0\0\0\u001Cftypmif1\0\0\0\0mif1avif")))).contains(ImageType.AVIF);
    }

    @Test
    void rejectsOtherContent() {
        assertThat(ImageType.detect(padded(ascii("<svg xmlns=")))).isEmpty();
        assertThat(ImageType.detect(padded(ascii("%PDF-1.7")))).isEmpty();
        assertThat(ImageType.detect(padded(ascii("\0\0\0\u0018ftypheic\0\0\0\0mif1")))).isEmpty();
        assertThat(ImageType.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(ImageType.detect(null)).isEmpty();
    }
}
