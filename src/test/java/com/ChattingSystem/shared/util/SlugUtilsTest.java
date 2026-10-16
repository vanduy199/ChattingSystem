package com.ChattingSystem.shared.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @Test
    void stripsVietnameseDiacritics() {
        assertThat(SlugUtils.slugify("Hướng dẫn sử dụng ChatChit"))
                .isEqualTo("huong-dan-su-dung-chatchit");
        assertThat(SlugUtils.slugify("Tiếng Việt có dấu: ắ ằ ẳ ẵ ặ ơ ư ữ ỹ"))
                .isEqualTo("tieng-viet-co-dau-a-a-a-a-a-o-u-u-y");
    }

    @Test
    void mapsDStrokeToD() {
        assertThat(SlugUtils.slugify("Đường đi đến Đà Lạt")).isEqualTo("duong-di-den-da-lat");
    }

    @Test
    void collapsesSymbolsAndTrimsDashes() {
        assertThat(SlugUtils.slugify("  --Hello,   World!!  Spring Boot 4.1 -- ")).isEqualTo("hello-world-spring-boot-4-1");
        assertThat(SlugUtils.slugify("C++ & Java_25")).isEqualTo("c-java-25");
    }

    @Test
    void returnsEmptyWhenNothingUsable() {
        assertThat(SlugUtils.slugify(null)).isEmpty();
        assertThat(SlugUtils.slugify("   ")).isEmpty();
        assertThat(SlugUtils.slugify("!!! ??? ***")).isEmpty();
    }

    @Test
    void truncatesWithoutTrailingDash() {
        String slug = SlugUtils.slugify("abc def ghi", 8);
        assertThat(slug).isEqualTo("abc-def");
        assertThat(slug.length()).isLessThanOrEqualTo(8);
    }

    @Test
    void resultMatchesContractPattern() {
        assertThat(SlugUtils.slugify("Bài viết đầu tiên — Xin chào 👋 2026"))
                .matches("^[a-z0-9]+(-[a-z0-9]+)*$")
                .isEqualTo("bai-viet-dau-tien-xin-chao-2026");
    }
}
