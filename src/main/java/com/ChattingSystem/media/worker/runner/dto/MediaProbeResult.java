package com.ChattingSystem.media.worker.runner.dto;

public record MediaProbeResult(
        int width,
        int height,
        Integer durationMs,
        String videoCodec,
        String audioCodec,
        String pixelFormat,
        int rotation,
        boolean hasVideo,
        boolean hasAudio,
        String formatName
) {
}
