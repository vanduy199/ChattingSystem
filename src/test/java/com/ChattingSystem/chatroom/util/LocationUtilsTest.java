package com.ChattingSystem.chatroom.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class LocationUtilsTest {

    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    @Test
    void fuzz_movesPointAndSnapsToGrid() {
        RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
        double lat = 21.028511, lng = 105.854178;

        for (int i = 0; i < 1000; i++) {
            LocationFuzzer.Point p = LocationFuzzer.fuzz(lat, lng, random);

            // Lệch tối đa: 150 m + nửa đường chéo ô lưới (~80 m)
            assertThat(haversineMeters(lat, lng, p.lat(), p.lng())).isLessThan(240);
            // Nằm trên lưới 0.001°
            assertThat(p.lat() / LocationFuzzer.GRID_DEG).isCloseTo(Math.rint(p.lat() / LocationFuzzer.GRID_DEG), offset(1e-6));
            assertThat(p.lng() / LocationFuzzer.GRID_DEG).isCloseTo(Math.rint(p.lng() / LocationFuzzer.GRID_DEG), offset(1e-6));
            // Không bao giờ trả lại đúng tọa độ thật
            assertThat(p.lat() == lat && p.lng() == lng).isFalse();
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0      | Dưới 100 m",
            "99.9   | Dưới 100 m",
            "100    | Khoảng 100 m",
            "349    | Khoảng 350 m",
            "974    | Khoảng 950 m",
            "975    | Khoảng 1 km",
            "1249   | Khoảng 1,2 km",
            "2960   | Khoảng 3 km"
    })
    void distanceLabel_roundsToApproximateBuckets(double meters, String expected) {
        assertThat(DistanceLabel.of(meters)).isEqualTo(expected);
    }
}
