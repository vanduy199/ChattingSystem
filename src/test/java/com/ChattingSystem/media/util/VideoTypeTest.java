package com.ChattingSystem.media.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VideoTypeTest {

    @Test
    @DisplayName("Nhận diện MP4 thành công với major brand isom")
    void detectMp4_MajorBrandIsom() {
        byte[] mp4Header = buildFtypBox("isom", "isom", "mp41");
        Optional<VideoType> result = VideoType.detect(mp4Header);

        assertThat(result).contains(VideoType.MP4);
        assertThat(result.get().contentType()).isEqualTo("video/mp4");
        assertThat(result.get().extension()).isEqualTo("mp4");
    }

    @Test
    @DisplayName("Nhận diện MP4 thành công khi major brand lạ nhưng compatible brand là mp42")
    void detectMp4_CompatibleBrandMp42() {
        byte[] mp4Header = buildFtypBox("dash", "mp42", "iso2");
        Optional<VideoType> result = VideoType.detect(mp4Header);

        assertThat(result).contains(VideoType.MP4);
    }

    @Test
    @DisplayName("Nhận diện QuickTime MOV thành công với brand qt  ")
    void detectMov_QuickTime() {
        byte[] movHeader = buildFtypBox("qt  ", "qt  ");
        Optional<VideoType> result = VideoType.detect(movHeader);

        assertThat(result).contains(VideoType.MOV);
        assertThat(result.get().contentType()).isEqualTo("video/quicktime");
        assertThat(result.get().extension()).isEqualTo("mov");
    }

    @Test
    @DisplayName("Chặn ảnh HEIC dù có compatible brand isom / mp41")
    void detect_HeicWithMp4CompatibleBrand_ReturnsEmpty() {
        byte[] heicHeader = buildFtypBox("heic", "isom", "mp41");
        Optional<VideoType> result = VideoType.detect(heicHeader);

        assertThat(result).isEmpty();

        byte[] mif1Header = buildFtypBox("mif1", "isom");
        assertThat(VideoType.detect(mif1Header)).isEmpty();

        byte[] avifHeader = buildFtypBox("avif", "isom");
        assertThat(VideoType.detect(avifHeader)).isEmpty();
    }

    @Test
    @DisplayName("Chặn file âm thanh M4A (chỉ nhận video)")
    void detect_M4aAudio_ReturnsEmpty() {
        byte[] m4aHeader = buildFtypBox("M4A ", "M4A ");
        Optional<VideoType> result = VideoType.detect(m4aHeader);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Chặn khi boxSize không hợp lệ (boxSize = 0, boxSize < 16, boxSize > 1024, boxSize lẻ)")
    void detect_InvalidBoxSizes_ReturnsEmpty() {
        // boxSize = 0
        byte[] boxZero = buildFtypBox("isom", "isom");
        boxZero[0] = 0; boxZero[1] = 0; boxZero[2] = 0; boxZero[3] = 0;
        assertThat(VideoType.detect(boxZero)).isEmpty();

        // boxSize = 12 (< 16)
        byte[] boxSmall = buildFtypBox("isom");
        boxSmall[0] = 0; boxSmall[1] = 0; boxSmall[2] = 0; boxSmall[3] = 12;
        assertThat(VideoType.detect(boxSmall)).isEmpty();

        // boxSize = 1028 (> 1024)
        byte[] boxHuge = buildFtypBox("isom");
        boxHuge[0] = 0; boxHuge[1] = 0; boxHuge[2] = 0x04; boxHuge[3] = 0x04; // 1028
        assertThat(VideoType.detect(boxHuge)).isEmpty();

        // boxSize % 4 != 0
        byte[] boxOdd = buildFtypBox("isom");
        boxOdd[0] = 0; boxOdd[1] = 0; boxOdd[2] = 0; boxOdd[3] = 21;
        assertThat(VideoType.detect(boxOdd)).isEmpty();
    }

    @Test
    @DisplayName("Từ chối khi không phải video (file ảnh PNG)")
    void detect_PngHeader_ReturnsEmpty() {
        byte[] pngHeader = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0};
        Optional<VideoType> result = VideoType.detect(pngHeader);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Từ chối khi chuỗi byte quá ngắn (< 16 bytes)")
    void detect_ShortBytes_ReturnsEmpty() {
        byte[] shortBytes = "ftyp".getBytes(StandardCharsets.US_ASCII);
        Optional<VideoType> result = VideoType.detect(shortBytes);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Từ chối khi box không phải ftyp (file text)")
    void detect_TextFile_ReturnsEmpty() {
        byte[] textBytes = "Hello World, this is not a video file!".getBytes(StandardCharsets.US_ASCII);
        Optional<VideoType> result = VideoType.detect(textBytes);

        assertThat(result).isEmpty();
    }

    private byte[] buildFtypBox(String majorBrand, String... compatibleBrands) {
        int totalSize = 16 + (compatibleBrands.length * 4);
        byte[] data = new byte[totalSize];

        // Box size (4 bytes big-endian)
        data[0] = (byte) ((totalSize >> 24) & 0xFF);
        data[1] = (byte) ((totalSize >> 16) & 0xFF);
        data[2] = (byte) ((totalSize >> 8) & 0xFF);
        data[3] = (byte) (totalSize & 0xFF);

        // "ftyp" (4 bytes)
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, data, 4, 4);

        // Major brand (4 bytes)
        System.arraycopy(majorBrand.getBytes(StandardCharsets.US_ASCII), 0, data, 8, 4);

        // Minor version (4 bytes, đặt 0)
        data[12] = 0;
        data[13] = 0;
        data[14] = 0x02;
        data[15] = 0;

        // Compatible brands
        int offset = 16;
        for (String brand : compatibleBrands) {
            System.arraycopy(brand.getBytes(StandardCharsets.US_ASCII), 0, data, offset, 4);
            offset += 4;
        }

        return data;
    }
}
