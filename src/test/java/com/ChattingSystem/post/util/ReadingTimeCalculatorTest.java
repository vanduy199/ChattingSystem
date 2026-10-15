package com.ChattingSystem.post.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingTimeCalculatorTest {

    private static String words(int count) {
        return "từ ".repeat(count);
    }

    @Test
    void minimumIsOneMinute() {
        assertThat(ReadingTimeCalculator.readingMinutes(null)).isEqualTo(1);
        assertThat(ReadingTimeCalculator.readingMinutes("")).isEqualTo(1);
        assertThat(ReadingTimeCalculator.readingMinutes("Xin chào")).isEqualTo(1);
    }

    @Test
    void roundsUpPer200Words() {
        assertThat(ReadingTimeCalculator.readingMinutes(words(200))).isEqualTo(1);
        assertThat(ReadingTimeCalculator.readingMinutes(words(201))).isEqualTo(2);
        assertThat(ReadingTimeCalculator.readingMinutes(words(400))).isEqualTo(2);
        assertThat(ReadingTimeCalculator.readingMinutes(words(1001))).isEqualTo(6);
    }

    @Test
    void countsPlainTextOfHtmlNotMarkup() {
        String html = """
                <h2>Tiêu đề bài viết</h2>                <ul><li>một</li><li>hai</li></ul>                <pre class="language-java"><code class="language-java">code here</code></pre>                <p>Xem <a href="https://example.com/a/very/long/url" title="liên kết dài">tài liệu</a> và                 <img src="https://cdn.example/img.png" alt="ảnh minh họa"> — <strong>hết</strong>.</p>""";
        // "Tiêu đề bài viết" (4) + "một hai" (2) + "code here" (2) + "Xem tài liệu và hết" (5);
        // tag names, attributes, URLs, alt text and "—" are not words
        assertThat(ReadingTimeCalculator.countWords(html)).isEqualTo(13);
    }

    @Test
    void emptyMarkupHasNoWords() {
        assertThat(ReadingTimeCalculator.countWords("<p></p><p>&nbsp;</p>")).isZero();
        assertThat(ReadingTimeCalculator.readingMinutes("<p></p>")).isEqualTo(1);
    }

    @Test
    void wrapsWordsInParagraphs() {
        assertThat(ReadingTimeCalculator.readingMinutes("<p>" + words(201) + "</p>")).isEqualTo(2);
    }
}
