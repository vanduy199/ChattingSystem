package com.ChattingSystem.media.worker.runner;

import com.ChattingSystem.media.worker.runner.dto.MediaProbeResult;

import java.nio.file.Path;

public interface FfmpegRunner {

    /**
     * Trích xuất metadata thực tế của tệp đa phương tiện qua ffprobe.
     *
     * @param inputPath Đường dẫn file đầu vào trên đĩa cục bộ
     * @return MediaProbeResult chứa các thông số kỹ thuật (width, height, duration, codec...)
     */
    MediaProbeResult probe(Path inputPath);

    /**
     * Nén và chuyển đổi ảnh tĩnh sang chuẩn WebP, xóa siêu dữ liệu EXIF/GPS.
     *
     * @param input   File ảnh gốc
     * @param output  File WebP đích
     * @param maxSize Kích thước cạnh dài tối đa (px)
     * @param quality Chất lượng nén WebP (0-100)
     */
    void convertImage(Path input, Path output, int maxSize, int quality);

    /**
     * Trích xuất ảnh thu nhỏ (thumbnail) WebP kích thước <= 300px, xóa GPS.
     *
     * @param input      File nguồn (ảnh hoặc video)
     * @param output     File thumbnail WebP đích
     * @param isVideo    true nếu là video, false nếu là ảnh
     * @param durationMs Thời lượng video (ms) để quyết định vị trí seek (-ss 1 hoặc -ss 0)
     */
    void extractThumbnail(Path input, Path output, boolean isVideo, Integer durationMs);

    /**
     * Sao chép luồng siêu tốc (Remux) sang MP4 +faststart không tốn CPU transcode.
     *
     * @param input  File video nguồn (đã là H.264 + AAC)
     * @param output File MP4 đích
     */
    void remuxVideo(Path input, Path output);

    /**
     * Chuyển mã (Transcode) video sang chuẩn H.264 / AAC +faststart.
     *
     * @param input  File video nguồn (HEVC, MOV, 10-bit...)
     * @param output File MP4 đích
     */
    void transcodeVideo(Path input, Path output);
}
