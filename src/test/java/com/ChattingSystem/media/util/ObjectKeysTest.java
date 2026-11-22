package com.ChattingSystem.media.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectKeysTest {

    @Test
    @DisplayName("Sinh rawKey theo đúng format raw/{yyyy}/{MM}/{dd}/{uuid}.{ext}")
    void rawKey_FormatMatches() {
        UUID id = UUID.fromString("7d9f3b52-1c64-4c1e-9a77-0f6f5c2f8a10");
        String key = ObjectKeys.rawKey(id, "mp4");

        assertThat(key).matches("^raw/\\d{4}/\\d{2}/\\d{2}/7d9f3b52-1c64-4c1e-9a77-0f6f5c2f8a10\\.mp4$");
    }

    @Test
    @DisplayName("Tự động chuẩn hóa extension có dấu chấm")
    void rawKey_CleansLeadingDot() {
        UUID id = UUID.fromString("7d9f3b52-1c64-4c1e-9a77-0f6f5c2f8a10");
        String key = ObjectKeys.rawKey(id, ".jpg");

        assertThat(key).endsWith(".jpg");
        assertThat(key).doesNotContain("..jpg");
    }

    @Test
    @DisplayName("Sinh thumbKey luôn có extension webp")
    void thumbKey_AlwaysWebp() {
        UUID id = UUID.fromString("7d9f3b52-1c64-4c1e-9a77-0f6f5c2f8a10");
        String key = ObjectKeys.thumbKey(id);

        assertThat(key).matches("^thumb/\\d{4}/\\d{2}/\\d{2}/7d9f3b52-1c64-4c1e-9a77-0f6f5c2f8a10\\.webp$");
    }
}
