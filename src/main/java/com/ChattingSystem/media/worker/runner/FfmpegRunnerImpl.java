package com.ChattingSystem.media.worker.runner;

import com.ChattingSystem.media.worker.exception.MediaValidationException;
import com.ChattingSystem.media.worker.runner.dto.MediaProbeResult;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.MediaWorkerProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class FfmpegRunnerImpl implements FfmpegRunner {

    private static final Logger log = LoggerFactory.getLogger(FfmpegRunnerImpl.class);
    private static final int MAX_STDERR_CHARS = 16 * 1024; // Giới hạn stderr tối đa 16 KB
    private static final int MAX_STDOUT_CHARS = 256 * 1024; // Giới hạn stdout tối đa 256 KB (tránh cắt cụt JSON ffprobe)

    private final MediaWorkerProperties properties;
    private final JsonMapper jsonMapper;

    public FfmpegRunnerImpl(MediaWorkerProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public MediaProbeResult probe(Path inputPath) {
        Objects.requireNonNull(inputPath, "inputPath must not be null");
        List<String> cmd = buildProbeCommand(inputPath);
        ProcessResult result = runProcess(cmd, properties.jobTimeout(), true);

        if (result.exitCode() != 0) {
            log.error("ffprobe failed on file {}: exitCode={}, stderr={}", inputPath, result.exitCode(), result.stderr());
            throw new MediaValidationException("Tệp phương tiện bị hỏng hoặc không thể nhận diện cấu trúc");
        }

        try {
            JsonNode root = jsonMapper.readTree(result.stdout());
            JsonNode streams = root.path("streams");
            JsonNode format = root.path("format");

            int width = 0;
            int height = 0;
            String videoCodec = null;
            String audioCodec = null;
            String pixelFormat = null;
            int rotation = 0;
            boolean hasVideo = false;
            boolean hasAudio = false;
            Integer videoStreamDurationMs = null;

            if (streams.isArray()) {
                for (JsonNode stream : streams) {
                    // Bỏ qua cover art đính kèm (attached_pic == 1) khi tìm luồng video chính
                    int attachedPic = stream.path("disposition").path("attached_pic").asInt(0);
                    if (attachedPic == 1) {
                        continue;
                    }

                    String codecType = stream.path("codec_type").asText("");
                    if ("video".equalsIgnoreCase(codecType) && !hasVideo) {
                        hasVideo = true;
                        videoCodec = stream.path("codec_name").asText(null);
                        width = stream.path("width").asInt(0);
                        height = stream.path("height").asInt(0);
                        pixelFormat = stream.path("pix_fmt").asText(null);

                        // Trích xuất góc xoay rotation từ stream_tags hoặc stream_side_data
                        int rotate = stream.path("tags").path("rotate").asInt(0);
                        if (rotate == 0 && stream.has("side_data_list")) {
                            for (JsonNode sideData : stream.path("side_data_list")) {
                                if (sideData.has("rotation")) {
                                    rotate = sideData.path("rotation").asInt(0);
                                    break;
                                }
                            }
                        }
                        rotation = Math.abs(rotate) % 360;

                        // Lưu trữ duration của video stream phòng trường hợp format.duration thiếu
                        if (stream.has("duration")) {
                            try {
                                double streamDurSec = Double.parseDouble(stream.path("duration").asText("0"));
                                if (streamDurSec > 0) {
                                    videoStreamDurationMs = (int) Math.round(streamDurSec * 1000.0);
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    } else if ("audio".equalsIgnoreCase(codecType) && !hasAudio) {
                        hasAudio = true;
                        audioCodec = stream.path("codec_name").asText(null);
                    }
                }
            }

            String formatName = format.path("format_name").asText(null);
            Integer durationMs = null;
            if (format.has("duration")) {
                try {
                    double durationSec = Double.parseDouble(format.path("duration").asText("0"));
                    if (durationSec > 0) {
                        durationMs = (int) Math.round(durationSec * 1000.0);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            // Fallback sang video stream duration nếu format.duration thiếu
            if (durationMs == null && videoStreamDurationMs != null) {
                durationMs = videoStreamDurationMs;
            }

            return new MediaProbeResult(
                    width,
                    height,
                    durationMs,
                    videoCodec,
                    audioCodec,
                    pixelFormat,
                    rotation,
                    hasVideo,
                    hasAudio,
                    formatName
            );
        } catch (Exception e) {
            log.error("Failed to parse ffprobe json for {}: {}", inputPath, e.getMessage(), e);
            throw new MediaValidationException("Không thể đọc thông số kỹ thuật của tệp: " + e.getMessage(), e);
        }
    }

    @Override
    public void convertImage(Path input, Path output, int maxSize, int quality) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
        List<String> cmd = buildConvertImageCommand(input, output, maxSize, quality);
        ProcessResult result = runProcess(cmd, properties.jobTimeout(), false);
        if (result.exitCode() != 0) {
            log.error("ffmpeg convertImage failed for {}: stderr={}", input, result.stderr());
            throw new AppException(ErrorCode.STORAGE_ERROR, "Không thể xử lý tối ưu hóa ảnh");
        }
    }

    @Override
    public void extractThumbnail(Path input, Path output, boolean isVideo, Integer durationMs) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
        List<String> cmd = buildExtractThumbnailCommand(input, output, isVideo, durationMs);
        ProcessResult result = runProcess(cmd, properties.jobTimeout(), false);
        if (result.exitCode() != 0) {
            log.error("ffmpeg extractThumbnail failed for {}: stderr={}", input, result.stderr());
            throw new AppException(ErrorCode.STORAGE_ERROR, "Không thể tạo ảnh thu nhỏ (thumbnail)");
        }
    }

    @Override
    public void remuxVideo(Path input, Path output) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
        List<String> cmd = buildRemuxVideoCommand(input, output);
        ProcessResult result = runProcess(cmd, properties.jobTimeout(), false);
        if (result.exitCode() != 0) {
            log.error("ffmpeg remuxVideo failed for {}: stderr={}", input, result.stderr());
            throw new AppException(ErrorCode.STORAGE_ERROR, "Không thể sao chép luồng video (remux)");
        }
    }

    @Override
    public void transcodeVideo(Path input, Path output) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
        List<String> cmd = buildTranscodeVideoCommand(input, output);
        ProcessResult result = runProcess(cmd, properties.jobTimeout(), false);
        if (result.exitCode() != 0) {
            log.error("ffmpeg transcodeVideo failed for {}: stderr={}", input, result.stderr());
            throw new AppException(ErrorCode.STORAGE_ERROR, "Không thể chuyển mã video (transcode)");
        }
    }

    // ==========================================
    // Package-Private Command Builders (Unit Testable)
    // ==========================================

    List<String> buildProbeCommand(Path inputPath) {
        return List.of(
                "ffprobe",
                "-v", "error",
                "-protocol_whitelist", "file",
                "-show_entries", "stream=codec_type,codec_name,width,height,pix_fmt,duration:stream_disposition=attached_pic:stream_tags=rotate:stream_side_data=rotation",
                "-show_entries", "format=duration,format_name",
                "-of", "json",
                inputPath.toAbsolutePath().toString()
        );
    }

    List<String> buildConvertImageCommand(Path input, Path output, int maxSize, int quality) {
        return List.of(
                "ffmpeg",
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-threads", "2",
                "-protocol_whitelist", "file",
                "-i", input.toAbsolutePath().toString(),
                "-vf", String.format("scale='min(%d,iw)':'min(%d,ih)':force_original_aspect_ratio=decrease", maxSize, maxSize),
                "-frames:v", "1",
                "-c:v", "libwebp",
                "-quality", String.valueOf(quality),
                "-threads", "2",
                "-map_metadata", "-1",
                "-y",
                output.toAbsolutePath().toString()
        );
    }

    List<String> buildExtractThumbnailCommand(Path input, Path output, boolean isVideo, Integer durationMs) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-nostdin");
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-threads");
        cmd.add("2");
        cmd.add("-protocol_whitelist");
        cmd.add("file");

        if (isVideo) {
            // Đặt -ss TRƯỚC -i để seek nhanh theo keyframe
            cmd.add("-ss");
            if (durationMs == null || durationMs < 1500) {
                cmd.add("0");
            } else {
                cmd.add("1");
            }
        }

        cmd.add("-i");
        cmd.add(input.toAbsolutePath().toString());
        cmd.add("-vf");
        cmd.add("scale='min(300,iw)':'min(300,ih)':force_original_aspect_ratio=decrease");
        cmd.add("-frames:v");
        cmd.add("1");
        cmd.add("-an");
        cmd.add("-sn");
        cmd.add("-dn");
        cmd.add("-c:v");
        cmd.add("libwebp");
        cmd.add("-quality");
        cmd.add("75");
        cmd.add("-threads");
        cmd.add("2");
        cmd.add("-map_metadata");
        cmd.add("-1");
        cmd.add("-y");
        cmd.add(output.toAbsolutePath().toString());

        return List.copyOf(cmd);
    }

    List<String> buildRemuxVideoCommand(Path input, Path output) {
        return List.of(
                "ffmpeg",
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-threads", "2",
                "-protocol_whitelist", "file",
                "-i", input.toAbsolutePath().toString(),
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-c", "copy",
                "-movflags", "+faststart",
                "-map_metadata", "-1",
                "-y",
                output.toAbsolutePath().toString()
        );
    }

    List<String> buildTranscodeVideoCommand(Path input, Path output) {
        return List.of(
                "ffmpeg",
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-threads", "2",
                "-protocol_whitelist", "file",
                "-i", input.toAbsolutePath().toString(),
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-vf", "scale='min(1920,iw)':'min(1920,ih)':force_original_aspect_ratio=decrease,scale='max(2,trunc(iw/2)*2)':'max(2,trunc(ih/2)*2)'",
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-crf", "23",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "128k",
                "-threads", "2",
                "-movflags", "+faststart",
                "-map_metadata", "-1",
                "-y",
                output.toAbsolutePath().toString()
        );
    }

    // ==========================================
    // Low-Level Process Execution (Anti-Deadlock)
    // ==========================================

    ProcessResult runProcess(List<String> command, Duration timeout, boolean captureStdout) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            // Riêng ffprobe KHÔNG gộp stderr vào stdout để không làm hỏng JSON
            pb.redirectErrorStream(false);
            if (!captureStdout) {
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            }
            process = pb.start();
            final Process proc = process;

            CompletableFuture<String> stdoutFuture = captureStdout
                    ? CompletableFuture.supplyAsync(() -> readStream(proc.getInputStream(), MAX_STDOUT_CHARS))
                    : CompletableFuture.completedFuture("");
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readStream(proc.getErrorStream(), MAX_STDERR_CHARS));

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                throw new AppException(ErrorCode.STORAGE_ERROR, "Tiến trình FFmpeg vượt quá thời gian chờ (timeout)");
            }

            String stdout = stdoutFuture.get(2, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(2, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            return new ProcessResult(exitCode, stdout, stderr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("FFmpeg process interrupted: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR, "Tiến trình FFmpeg bị gián đoạn: " + e.getMessage());
        } catch (AppException | MediaValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Execution error for command {}: {}", command.getFirst(), e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR, "Lỗi khi chạy tiến trình xử lý media: " + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String readStream(InputStream is, int maxChars) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[1024];
            int read;
            int total = 0;
            while ((read = reader.read(buf)) != -1) {
                if (total < maxChars) {
                    int toAppend = Math.min(read, maxChars - total);
                    sb.append(buf, 0, toAppend);
                    total += toAppend;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
