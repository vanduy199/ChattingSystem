package com.ChattingSystem.chatroom.util;

import java.util.random.RandomGenerator;

/**
 * Làm mờ tâm phòng để không lộ vị trí thật của người tạo: dịch ngẫu nhiên 50–150 m
 * theo một hướng bất kỳ rồi bám vào lưới ~100 m. Mọi truy vấn quét/khoảng cách
 * dùng điểm đã làm mờ này, không bao giờ dùng tâm thật.
 */
public final class LocationFuzzer {

    static final double MIN_OFFSET_M = 50;
    static final double MAX_OFFSET_M = 150;
    /** Bước lưới theo vĩ độ, ~111 m. */
    static final double GRID_DEG = 0.001;

    private static final double METERS_PER_DEG_LAT = 111_320;

    private LocationFuzzer() {
    }

    public record Point(double lat, double lng) {
    }

    public static Point fuzz(double lat, double lng, RandomGenerator random) {
        double distance = MIN_OFFSET_M + random.nextDouble() * (MAX_OFFSET_M - MIN_OFFSET_M);
        double bearing = random.nextDouble() * 2 * Math.PI;

        double dLat = distance * Math.cos(bearing) / METERS_PER_DEG_LAT;
        double dLng = distance * Math.sin(bearing) / (METERS_PER_DEG_LAT * Math.cos(Math.toRadians(lat)));

        double fuzzedLat = clamp(snap(lat + dLat), -90, 90);
        double fuzzedLng = wrapLng(snap(lng + dLng));
        return new Point(fuzzedLat, fuzzedLng);
    }

    private static double snap(double deg) {
        return Math.round(deg / GRID_DEG) * GRID_DEG;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double wrapLng(double lng) {
        if (lng > 180) {
            return lng - 360;
        }
        if (lng < -180) {
            return lng + 360;
        }
        return lng;
    }
}
