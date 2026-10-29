package com.ChattingSystem.chatroom.util;

/**
 * Khoảng cách gần đúng hiển thị cho người dùng: "Dưới 100 m", bước 50 m đến 1 km,
 * bước 100 m sau đó. Không bao giờ trả số mét chính xác.
 */
public final class DistanceLabel {

    private DistanceLabel() {
    }

    public static String of(double meters) {
        if (meters < 100) {
            return "Dưới 100 m";
        }
        if (meters < 975) {
            long rounded = Math.round(meters / 50) * 50;
            return "Khoảng " + rounded + " m";
        }
        long hundreds = Math.round(meters / 100);
        long whole = hundreds / 10;
        long tenth = hundreds % 10;
        return tenth == 0
                ? "Khoảng " + whole + " km"
                : "Khoảng " + whole + "," + tenth + " km";
    }
}
