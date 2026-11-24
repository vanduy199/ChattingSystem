package com.ChattingSystem.media.worker.runner;

import com.ChattingSystem.media.worker.exception.MediaValidationException;
import com.ChattingSystem.media.worker.runner.dto.MediaProbeResult;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.MediaWorkerProperties;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegRunnerUnitTest {

    private MediaWorkerProperties properties;
    private JsonMapper jsonMapper;
    private FfmpegRunnerImpl runner;

    @BeforeEach
    void setUp() {
        properties = new MediaWorkerProperties(
                true,
                1,
                Duration.ofSeconds(2),
                Duration.ofMinutes(10),
                4,
                Path.of(System.getProperty("java.io.tmpdir"), "media-work").toString(),
                256 * 1024 * 1024L,
                50_000_000L,
                300
        );
        jsonMapper = JsonMapper.builder().build();
        runner = new FfmpegRunnerImpl(properties, jsonMapper);
    }

    @Nested
    @DisplayName("Command Builder Unit Tests")
    class CommandBuilderTests {

        @Test
        @DisplayName("buildProbeCommand: đúng cờ ffprobe, whitelist file, stream_disposition=attached_pic và of json")
        void buildProbeCommand_shouldContainAllRequiredFlags() {
            Path input = Path.of("temp", "sample.mp4");
            List<String> cmd = runner.buildProbeCommand(input);

            assertThat(cmd.getFirst()).isEqualTo("ffprobe");
            assertThat(cmd).containsSequence("-v", "error");
            assertThat(cmd).containsSequence("-protocol_whitelist", "file");
            assertThat(cmd).contains(
                    "stream=codec_type,codec_name,width,height,pix_fmt,duration:stream_disposition=attached_pic:stream_tags=rotate:stream_side_data=rotation"
            );
            assertThat(cmd).contains("format=duration,format_name");
            assertThat(cmd).containsSequence("-of", "json");
            assertThat(cmd.getLast()).isEqualTo(input.toAbsolutePath().toString());
        }

        @Test
        @DisplayName("buildConvertImageCommand: chứa -threads 2 trước -i và -threads 2 ở output encoder")
        void buildConvertImageCommand_shouldContainDecoderAndEncoderThreads() {
            Path input = Path.of("temp", "input.jpg");
            Path output = Path.of("temp", "output.webp");

            List<String> cmd = runner.buildConvertImageCommand(input, output, 2048, 82);

            assertThat(cmd.getFirst()).isEqualTo("ffmpeg");
            assertThat(cmd).containsSequence("-protocol_whitelist", "file");
            assertThat(cmd).containsSequence("-vf", "scale='min(2048,iw)':'min(2048,ih)':force_original_aspect_ratio=decrease");
            assertThat(cmd).containsSequence("-c:v", "libwebp");
            assertThat(cmd).containsSequence("-quality", "82");
            assertThat(cmd).containsSequence("-map_metadata", "-1");
            assertThat(cmd.getLast()).isEqualTo(output.toAbsolutePath().toString());

            // Đảm bảo có 2 lần khai báo "-threads", "2" (cho decoder và encoder)
            int threadsCount = 0;
            for (int i = 0; i < cmd.size() - 1; i++) {
                if ("-threads".equals(cmd.get(i)) && "2".equals(cmd.get(i + 1))) {
                    threadsCount++;
                }
            }
            assertThat(threadsCount).isEqualTo(2);

            int inputIdx = cmd.indexOf("-i");
            int firstThreadsIdx = cmd.indexOf("-threads");
            int lastThreadsIdx = cmd.lastIndexOf("-threads");
            assertThat(firstThreadsIdx).isLessThan(inputIdx);
            assertThat(lastThreadsIdx).isGreaterThan(inputIdx);
        }

        @Test
        @DisplayName("buildExtractThumbnailCommand: xử lý ảnh không có -ss, chứa -threads 2 trước và sau -i")
        void buildExtractThumbnailCommand_image_shouldNotContainSsFlag() {
            Path input = Path.of("temp", "image.png");
            Path output = Path.of("temp", "thumb.webp");

            List<String> cmd = runner.buildExtractThumbnailCommand(input, output, false, null);

            assertThat(cmd).doesNotContain("-ss");
            assertThat(cmd).containsSequence("-vf", "scale='min(300,iw)':'min(300,ih)':force_original_aspect_ratio=decrease");
            assertThat(cmd).contains("-an", "-sn", "-dn");
            assertThat(cmd).containsSequence("-c:v", "libwebp");
            assertThat(cmd).containsSequence("-quality", "75");

            int threadsCount = 0;
            for (int i = 0; i < cmd.size() - 1; i++) {
                if ("-threads".equals(cmd.get(i)) && "2".equals(cmd.get(i + 1))) {
                    threadsCount++;
                }
            }
            assertThat(threadsCount).isEqualTo(2);
        }

        @Test
        @DisplayName("buildExtractThumbnailCommand: video >= 1500ms đặt -ss 1 TRƯỚC -i")
        void buildExtractThumbnailCommand_longVideo_shouldSeekOneSecondBeforeInput() {
            Path input = Path.of("temp", "video.mp4");
            Path output = Path.of("temp", "thumb.webp");

            List<String> cmd = runner.buildExtractThumbnailCommand(input, output, true, 5000);

            int ssIdx = cmd.indexOf("-ss");
            int inputIdx = cmd.indexOf("-i");

            assertThat(ssIdx).isGreaterThanOrEqualTo(0);
            assertThat(ssIdx).isLessThan(inputIdx);
            assertThat(cmd.get(ssIdx + 1)).isEqualTo("1");
        }

        @Test
        @DisplayName("buildExtractThumbnailCommand: video < 1500ms hoặc durationMs null đặt -ss 0 TRƯỚC -i")
        void buildExtractThumbnailCommand_shortVideo_shouldSeekZeroBeforeInput() {
            Path input = Path.of("temp", "video.mp4");
            Path output = Path.of("temp", "thumb.webp");

            List<String> cmdShort = runner.buildExtractThumbnailCommand(input, output, true, 1200);
            int ssIdxShort = cmdShort.indexOf("-ss");
            int inputIdxShort = cmdShort.indexOf("-i");
            assertThat(ssIdxShort).isLessThan(inputIdxShort);
            assertThat(cmdShort.get(ssIdxShort + 1)).isEqualTo("0");

            List<String> cmdNull = runner.buildExtractThumbnailCommand(input, output, true, null);
            int ssIdxNull = cmdNull.indexOf("-ss");
            int inputIdxNull = cmdNull.indexOf("-i");
            assertThat(ssIdxNull).isLessThan(inputIdxNull);
            assertThat(cmdNull.get(ssIdxNull + 1)).isEqualTo("0");
        }

        @Test
        @DisplayName("buildRemuxVideoCommand: copy stream, +faststart, map 0:v:0 và 0:a:0?")
        void buildRemuxVideoCommand_shouldContainRemuxParameters() {
            Path input = Path.of("temp", "input.mp4");
            Path output = Path.of("temp", "output.mp4");

            List<String> cmd = runner.buildRemuxVideoCommand(input, output);

            assertThat(cmd).containsSequence("-map", "0:v:0");
            assertThat(cmd).containsSequence("-map", "0:a:0?");
            assertThat(cmd).containsSequence("-c", "copy");
            assertThat(cmd).containsSequence("-movflags", "+faststart");
            assertThat(cmd).containsSequence("-map_metadata", "-1");
        }

        @Test
        @DisplayName("buildTranscodeVideoCommand: chứa -threads 2 trước và sau -i, scale max(2, trunc(iw/2)*2)")
        void buildTranscodeVideoCommand_shouldContainTranscodeParametersAndSafeScale() {
            Path input = Path.of("temp", "input.mov");
            Path output = Path.of("temp", "output.mp4");

            List<String> cmd = runner.buildTranscodeVideoCommand(input, output);

            assertThat(cmd).containsSequence(
                    "-vf",
                    "scale='min(1920,iw)':'min(1920,ih)':force_original_aspect_ratio=decrease,scale='max(2,trunc(iw/2)*2)':'max(2,trunc(ih/2)*2)'"
            );
            assertThat(cmd).containsSequence("-c:v", "libx264");
            assertThat(cmd).containsSequence("-preset", "veryfast");
            assertThat(cmd).containsSequence("-crf", "23");
            assertThat(cmd).containsSequence("-pix_fmt", "yuv420p");
            assertThat(cmd).containsSequence("-c:a", "aac");
            assertThat(cmd).containsSequence("-b:a", "128k");
            assertThat(cmd).containsSequence("-movflags", "+faststart");

            int threadsCount = 0;
            for (int i = 0; i < cmd.size() - 1; i++) {
                if ("-threads".equals(cmd.get(i)) && "2".equals(cmd.get(i + 1))) {
                    threadsCount++;
                }
            }
            assertThat(threadsCount).isEqualTo(2);

            int inputIdx = cmd.indexOf("-i");
            int firstThreadsIdx = cmd.indexOf("-threads");
            int lastThreadsIdx = cmd.lastIndexOf("-threads");
            assertThat(firstThreadsIdx).isLessThan(inputIdx);
            assertThat(lastThreadsIdx).isGreaterThan(inputIdx);
        }
    }

    @Nested
    @DisplayName("Probe Parsing & Execution Tests (Stubbed Process)")
    class ProbeParsingTests {

        @Test
        @DisplayName("probe: trích xuất thành công video + audio + rotation")
        void probe_shouldExtractVideoAndAudioMetadata() {
            String jsonOutput = """
                    {
                      "streams": [
                        {
                          "codec_type": "video",
                          "codec_name": "h264",
                          "width": 1920,
                          "height": 1080,
                          "pix_fmt": "yuv420p",
                          "side_data_list": [
                            { "rotation": -90 }
                          ]
                        },
                        {
                          "codec_type": "audio",
                          "codec_name": "aac"
                        }
                      ],
                      "format": {
                        "format_name": "mov,mp4,m4a,3gp,3g2,mj2",
                        "duration": "45.678"
                      }
                    }
                    """;

            FfmpegRunnerImpl stubRunner = new FfmpegRunnerImpl(properties, jsonMapper) {
                @Override
                ProcessResult runProcess(List<String> command, Duration timeout, boolean captureStdout) {
                    return new ProcessResult(0, jsonOutput, "");
                }
            };

            MediaProbeResult result = stubRunner.probe(Path.of("dummy.mp4"));

            assertThat(result.hasVideo()).isTrue();
            assertThat(result.hasAudio()).isTrue();
            assertThat(result.width()).isEqualTo(1920);
            assertThat(result.height()).isEqualTo(1080);
            assertThat(result.videoCodec()).isEqualTo("h264");
            assertThat(result.audioCodec()).isEqualTo("aac");
            assertThat(result.pixelFormat()).isEqualTo("yuv420p");
            assertThat(result.rotation()).isEqualTo(90);
            assertThat(result.durationMs()).isEqualTo(45678);
            assertThat(result.formatName()).isEqualTo("mov,mp4,m4a,3gp,3g2,mj2");
        }

        @Test
        @DisplayName("probe: bỏ qua luồng video là attached_pic (cover art MP3), lấy đúng video thật sau đó")
        void probe_shouldIgnoreAttachedPicCoverArt() {
            String jsonOutput = """
                    {
                      "streams": [
                        {
                          "codec_type": "video",
                          "codec_name": "png",
                          "width": 500,
                          "height": 500,
                          "disposition": {
                            "attached_pic": 1
                          }
                        },
                        {
                          "codec_type": "video",
                          "codec_name": "h264",
                          "width": 1280,
                          "height": 720,
                          "pix_fmt": "yuv420p"
                        }
                      ],
                      "format": {
                        "format_name": "mp4",
                        "duration": "10.0"
                      }
                    }
                    """;

            FfmpegRunnerImpl stubRunner = new FfmpegRunnerImpl(properties, jsonMapper) {
                @Override
                ProcessResult runProcess(List<String> command, Duration timeout, boolean captureStdout) {
                    return new ProcessResult(0, jsonOutput, "");
                }
            };

            MediaProbeResult result = stubRunner.probe(Path.of("dummy.mp4"));

            assertThat(result.hasVideo()).isTrue();
            assertThat(result.videoCodec()).isEqualTo("h264");
            assertThat(result.width()).isEqualTo(1280);
            assertThat(result.height()).isEqualTo(720);
        }

        @Test
        @DisplayName("probe: nếu chỉ có attached_pic (cover art của file audio), hasVideo phải là false")
        void probe_audioWithAttachedPicCoverOnly_shouldHaveVideoFalse() {
            String jsonOutput = """
                    {
                      "streams": [
                        {
                          "codec_type": "audio",
                          "codec_name": "mp3"
                        },
                        {
                          "codec_type": "video",
                          "codec_name": "jpeg",
                          "width": 600,
                          "height": 600,
                          "disposition": {
                            "attached_pic": 1
                          }
                        }
                      ],
                      "format": {
                        "format_name": "mp3",
                        "duration": "180.0"
                      }
                    }
                    """;

            FfmpegRunnerImpl stubRunner = new FfmpegRunnerImpl(properties, jsonMapper) {
                @Override
                ProcessResult runProcess(List<String> command, Duration timeout, boolean captureStdout) {
                    return new ProcessResult(0, jsonOutput, "");
                }
            };

            MediaProbeResult result = stubRunner.probe(Path.of("audio.mp3"));

            assertThat(result.hasVideo()).isFalse();
            assertThat(result.hasAudio()).isTrue();
            assertThat(result.audioCodec()).isEqualTo("mp3");
            assertThat(result.width()).isEqualTo(0);
        }

        @Test
        @DisplayName("probe: format.duration thiếu nhưng video stream có duration -> fallback lấy stream duration")
        void probe_formatDurationMissing_shouldFallbackToVideoStreamDuration() {
            String jsonOutput = """
                    {
                      "streams": [
                        {
                          "codec_type": "video",
                          "codec_name": "h264",
                          "width": 640,
                          "height": 360,
                          "duration": "15.420"
                        }
                      ],
                      "format": {
                        "format_name": "flv"
                      }
                    }
                    """;

            FfmpegRunnerImpl stubRunner = new FfmpegRunnerImpl(properties, jsonMapper) {
                @Override
                ProcessResult runProcess(List<String> command, Duration timeout, boolean captureStdout) {
                    return new ProcessResult(0, jsonOutput, "");
                }
            };

            MediaProbeResult result = stubRunner.probe(Path.of("video.flv"));

            assertThat(result.durationMs()).isEqualTo(15420);
        }

        @Test
        @DisplayName("probe: exitCode != 0 ném MediaValidationException")
        void probe_exitCodeNonZero_shouldThrowMediaValidationException() {
            FfmpegRunnerImpl stubRunner = new FfmpegRunnerImpl(properties, jsonMapper) {
                @Override
                ProcessResult runProcess(List<String> command, Duration timeout, boolean captureStdout) {
                    return new ProcessResult(1, "", "Invalid data found when processing input");
                }
            };

            assertThatThrownBy(() -> stubRunner.probe(Path.of("corrupt.mp4")))
                    .isInstanceOf(MediaValidationException.class)
                    .hasMessageContaining("Tệp phương tiện bị hỏng");
        }

        @Test
        @DisplayName("probe: json không hợp lệ ném MediaValidationException")
        void probe_invalidJson_shouldThrowMediaValidationException() {
            FfmpegRunnerImpl stubRunner = new FfmpegRunnerImpl(properties, jsonMapper) {
                @Override
                ProcessResult runProcess(List<String> command, Duration timeout, boolean captureStdout) {
                    return new ProcessResult(0, "{ not-a-json", "");
                }
            };

            assertThatThrownBy(() -> stubRunner.probe(Path.of("corrupt.mp4")))
                    .isInstanceOf(MediaValidationException.class)
                    .hasMessageContaining("Không thể đọc thông số kỹ thuật");
        }

        @Test
        @DisplayName("convertImage: thất bại ném AppException(STORAGE_ERROR)")
        void convertImage_failure_shouldThrowAppException() {
            FfmpegRunnerImpl stubRunner = new FfmpegRunnerImpl(properties, jsonMapper) {
                @Override
                ProcessResult runProcess(List<String> command, Duration timeout, boolean captureStdout) {
                    return new ProcessResult(1, "", "Encoding failed");
                }
            };

            assertThatThrownBy(() -> stubRunner.convertImage(Path.of("in.jpg"), Path.of("out.webp"), 2048, 80))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STORAGE_ERROR));
        }

        @Test
        @DisplayName("transcodeVideo: thất bại ném AppException(STORAGE_ERROR)")
        void transcodeVideo_failure_shouldThrowAppException() {
            FfmpegRunnerImpl stubRunner = new FfmpegRunnerImpl(properties, jsonMapper) {
                @Override
                ProcessResult runProcess(List<String> command, Duration timeout, boolean captureStdout) {
                    return new ProcessResult(1, "", "Transcode failed");
                }
            };

            assertThatThrownBy(() -> stubRunner.transcodeVideo(Path.of("in.mov"), Path.of("out.mp4")))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.STORAGE_ERROR));
        }
    }
}
