package com.ChattingSystem.media.util;

import com.ChattingSystem.media.entity.MediaKind;
import com.ChattingSystem.user.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MediaSnifferTest {

    @Test
    @DisplayName("Nhận diện ảnh JPEG từ Magic Bytes FF D8 FF")
    void sniff_Jpeg() {
        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};
        Optional<MediaSniffer.SniffResult> result = MediaSniffer.sniff(jpegHeader);

        assertThat(result).isPresent();
        assertThat(result.get().kind()).isEqualTo(MediaKind.IMAGE);
        assertThat(result.get().contentType()).isEqualTo("image/jpeg");
        assertThat(result.get().extension()).isEqualTo("jpg");
    }

    @Test
    @DisplayName("Nhận diện ảnh PNG từ Magic Bytes")
    void sniff_Png() {
        byte[] pngHeader = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
        Optional<MediaSniffer.SniffResult> result = MediaSniffer.sniff(pngHeader);

        assertThat(result).isPresent();
        assertThat(result.get().kind()).isEqualTo(MediaKind.IMAGE);
        assertThat(result.get().contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("Nhận diện video MP4 từ ftyp box")
    void sniff_Mp4() {
        byte[] mp4Header = buildFtypBox("isom", "mp41");
        Optional<MediaSniffer.SniffResult> result = MediaSniffer.sniff(mp4Header);

        assertThat(result).isPresent();
        assertThat(result.get().kind()).isEqualTo(MediaKind.VIDEO);
        assertThat(result.get().contentType()).isEqualTo("video/mp4");
        assertThat(result.get().extension()).isEqualTo("mp4");
    }

    @Test
    @DisplayName("Từ chối file mã độc giả danh ảnh (file Windows EXE có header MZ)")
    void sniff_ExecutableSpoofedAsImage_ReturnsEmpty() {
        byte[] exeHeader = new byte[]{'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0};
        Optional<MediaSniffer.SniffResult> result = MediaSniffer.sniff(exeHeader);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Chính sách Guest: Chỉ được tải ảnh cơ bản ở phòng Public, cấm tải video")
    void isAllowed_GuestRules() {
        MediaSniffer.SniffResult jpeg = new MediaSniffer.SniffResult(MediaKind.IMAGE, "image/jpeg", "jpg");
        MediaSniffer.SniffResult mp4 = new MediaSniffer.SniffResult(MediaKind.VIDEO, "video/mp4", "mp4");

        // Guest trong phòng Public: ảnh được phép, video bị cấm
        assertThat(MediaSniffer.isAllowed(jpeg, Role.GUEST, "public")).isTrue();
        assertThat(MediaSniffer.isAllowed(mp4, Role.GUEST, "public")).isFalse();

        // Guest trong phòng Private: cấm mọi tải lên
        assertThat(MediaSniffer.isAllowed(jpeg, Role.GUEST, "private")).isFalse();
    }

    @Test
    @DisplayName("Chính sách User: Phòng Public cho phép MP4 nhưng cấm QuickTime MOV; Phòng Private cho phép cả MOV")
    void isAllowed_UserRules() {
        MediaSniffer.SniffResult mp4 = new MediaSniffer.SniffResult(MediaKind.VIDEO, "video/mp4", "mp4");
        MediaSniffer.SniffResult mov = new MediaSniffer.SniffResult(MediaKind.VIDEO, "video/quicktime", "mov");

        // Phòng Public: MP4 được, MOV bị cấm (chỉ dành cho Private)
        assertThat(MediaSniffer.isAllowed(mp4, Role.USER, "public")).isTrue();
        assertThat(MediaSniffer.isAllowed(mov, Role.USER, "public")).isFalse();

        // Phòng Private: cả MP4 và MOV đều được phép
        assertThat(MediaSniffer.isAllowed(mp4, Role.USER, "private")).isTrue();
        assertThat(MediaSniffer.isAllowed(mov, Role.USER, "private")).isTrue();
    }

    @Test
    @DisplayName("Kiểm tra MIME khai báo hợp lệ (isDeclaredMimeAllowed) theo từng vai trò và loại phòng")
    void isDeclaredMimeAllowed_Matrix() {
        // 1. Guest phòng Public: chỉ nhận jpeg, png, webp; cấm gif, avif, mp4
        assertThat(MediaSniffer.isDeclaredMimeAllowed("image/jpeg", Role.GUEST, "public")).isTrue();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("image/png", Role.GUEST, "public")).isTrue();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("image/webp", Role.GUEST, "public")).isTrue();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("image/gif", Role.GUEST, "public")).isFalse();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("image/avif", Role.GUEST, "public")).isFalse();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("video/mp4", Role.GUEST, "public")).isFalse();

        // Guest phòng Private: cấm tất cả
        assertThat(MediaSniffer.isDeclaredMimeAllowed("image/jpeg", Role.GUEST, "private")).isFalse();

        // 2. User phòng Public: nhận 5 loại ảnh + video/mp4; cấm video/quicktime
        assertThat(MediaSniffer.isDeclaredMimeAllowed("image/jpeg", Role.USER, "public")).isTrue();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("image/png", Role.USER, "public")).isTrue();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("image/webp", Role.USER, "public")).isTrue();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("image/avif", Role.USER, "public")).isTrue();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("image/gif", Role.USER, "public")).isTrue();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("video/mp4", Role.USER, "public")).isTrue();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("video/quicktime", Role.USER, "public")).isFalse();

        // 3. User phòng Private: nhận 5 loại ảnh + video/mp4 + video/quicktime
        assertThat(MediaSniffer.isDeclaredMimeAllowed("video/quicktime", Role.USER, "private")).isTrue();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("video/quicktime", Role.USER, "  PRIVATE  ")).isTrue();
        // Kiểm tra chặt: phòng không phải 'private' (ví dụ 'group', 'secret', 'public') đều bị từ chối
        assertThat(MediaSniffer.isDeclaredMimeAllowed("video/quicktime", Role.USER, "group")).isFalse();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("video/quicktime", Role.USER, "secret")).isFalse();
        assertThat(MediaSniffer.isDeclaredMimeAllowed("application/pdf", Role.USER, "private")).isFalse();
    }

    @Test
    @DisplayName("Kiểm tra khớp loại đã khai báo (IMAGE vs VIDEO)")
    void matchesDeclaredKind() {
        MediaSniffer.SniffResult image = new MediaSniffer.SniffResult(MediaKind.IMAGE, "image/jpeg", "jpg");

        assertThat(MediaSniffer.matchesDeclaredKind(image, MediaKind.IMAGE)).isTrue();
        assertThat(MediaSniffer.matchesDeclaredKind(image, MediaKind.VIDEO)).isFalse();
    }

    private byte[] buildFtypBox(String majorBrand, String... compatibleBrands) {
        int totalSize = 16 + (compatibleBrands.length * 4);
        byte[] data = new byte[totalSize];
        data[0] = (byte) ((totalSize >> 24) & 0xFF);
        data[1] = (byte) ((totalSize >> 16) & 0xFF);
        data[2] = (byte) ((totalSize >> 8) & 0xFF);
        data[3] = (byte) (totalSize & 0xFF);
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, data, 4, 4);
        System.arraycopy(majorBrand.getBytes(StandardCharsets.US_ASCII), 0, data, 8, 4);
        int offset = 16;
        for (String brand : compatibleBrands) {
            System.arraycopy(brand.getBytes(StandardCharsets.US_ASCII), 0, data, offset, 4);
            offset += 4;
        }
        return data;
    }
}
