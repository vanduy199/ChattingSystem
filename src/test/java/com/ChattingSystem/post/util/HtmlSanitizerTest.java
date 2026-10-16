package com.ChattingSystem.post.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    @Test
    void removesScriptsStylesIframesAndEventHandlers() {
        String html = """
                <p onclick="alert(1)" style="color:red">Xin chào</p>\
                <script>alert(1)</script><style>p{}</style>\
                <iframe src="https://evil.example"></iframe>\
                <img src="https://cdn.example/a.png" onerror="alert(1)">""";
        assertThat(HtmlSanitizer.sanitize(html))
                .isEqualTo("<p>Xin chào</p><img src=\"https://cdn.example/a.png\">");
    }

    @Test
    void removesJavascriptAndDataHrefs() {
        assertThat(HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">x</a>")).isEqualTo("<a>x</a>");
        assertThat(HtmlSanitizer.sanitize("<a href=\" JaVaScRiPt:alert(1)\">x</a>")).isEqualTo("<a>x</a>");
        assertThat(HtmlSanitizer.sanitize("<a href=\"data:text/html;base64,PHNjcmlwdD4=\">x</a>")).isEqualTo("<a>x</a>");
        assertThat(HtmlSanitizer.sanitize("<a href=\"//evil.example\">x</a>")).isEqualTo("<a>x</a>");
    }

    @Test
    void keepsAllowedLinks() {
        assertThat(HtmlSanitizer.sanitize("<a href=\"https://chatchit.me/a?b=1&amp;c=2\" title=\"t\">x</a>"))
                .isEqualTo("<a href=\"https://chatchit.me/a?b=1&amp;c=2\" title=\"t\">x</a>");
        assertThat(HtmlSanitizer.sanitize("<a href=\"mailto:hi@chatchit.me\">x</a>"))
                .isEqualTo("<a href=\"mailto:hi@chatchit.me\">x</a>");
    }

    @Test
    void keepsRelativeAndAnchorLinks() {
        assertThat(HtmlSanitizer.sanitize("<a href=\"/blog/bai-viet\">x</a>"))
                .isEqualTo("<a href=\"/blog/bai-viet\">x</a>");
        assertThat(HtmlSanitizer.sanitize("<a href=\"#muc-1\">x</a>"))
                .isEqualTo("<a href=\"#muc-1\">x</a>");
    }

    @Test
    void convertsH1ToH2() {
        assertThat(HtmlSanitizer.sanitize("<h1>Tiêu đề</h1><h3>Phụ</h3>"))
                .isEqualTo("<h2>Tiêu đề</h2><h3>Phụ</h3>");
    }

    @Test
    void enforcesRelOnTargetBlank() {
        assertThat(HtmlSanitizer.sanitize("<a href=\"https://a.example\" target=\"_blank\" rel=\"opener\">x</a>"))
                .isEqualTo("<a href=\"https://a.example\" target=\"_blank\" rel=\"noopener noreferrer\">x</a>");
        assertThat(HtmlSanitizer.sanitize("<a href=\"https://a.example\" target=\"_blank\">x</a>"))
                .isEqualTo("<a href=\"https://a.example\" target=\"_blank\" rel=\"noopener noreferrer\">x</a>");
    }

    @Test
    void imageSourcesMustBeHttpOrHttps() {
        assertThat(HtmlSanitizer.sanitize("<img src=\"https://cdn.example/a.png\" alt=\"Ảnh\" width=\"640\" height=\"480\">"))
                .isEqualTo("<img src=\"https://cdn.example/a.png\" alt=\"Ảnh\" width=\"640\" height=\"480\">");
        assertThat(HtmlSanitizer.sanitize("<img src=\"data:image/png;base64,iVBORw0KGgo=\" alt=\"x\">"))
                .isEqualTo("<img alt=\"x\">");
        assertThat(HtmlSanitizer.sanitize("<img src=\"javascript:alert(1)\">")).isEqualTo("<img>");
    }

    @Test
    void keepsOnlyLanguageClassesOnCode() {
        assertThat(HtmlSanitizer.sanitize("<pre class=\"hljs language-java\"><code class=\"language-java foo\">int a = 1;</code></pre>"))
                .isEqualTo("<pre class=\"language-java\"><code class=\"language-java\">int a = 1;</code></pre>");
        assertThat(HtmlSanitizer.sanitize("<code class=\"evil\">x</code>")).isEqualTo("<code>x</code>");
        assertThat(HtmlSanitizer.sanitize("<p class=\"language-java\">x</p>")).isEqualTo("<p>x</p>");
    }

    @Test
    void keepsTablesWithSpans() {
        assertThat(HtmlSanitizer.sanitize("<table><tbody><tr><td colspan=\"2\" style=\"x\">a</td></tr></tbody></table>"))
                .isEqualTo("<table><tbody><tr><td colspan=\"2\">a</td></tr></tbody></table>");
    }

    @Test
    void keepsVietnameseTextIntact() {
        String html = "<p>Tiếng Việt có dấu: ắ ặ ồ ữ ỹ đ Đ — “trích dẫn”</p>";
        assertThat(HtmlSanitizer.sanitize(html)).isEqualTo(html);
    }

    @Test
    void contentCheck() {
        assertThat(HtmlSanitizer.hasContent(HtmlSanitizer.sanitize("<p></p><p>&nbsp;</p><br>"))).isFalse();
        assertThat(HtmlSanitizer.hasContent(HtmlSanitizer.sanitize("<script>alert(1)</script>"))).isFalse();
        assertThat(HtmlSanitizer.hasContent(HtmlSanitizer.sanitize("<img src=\"data:image/png;base64,AA==\">"))).isFalse();
        assertThat(HtmlSanitizer.hasContent(HtmlSanitizer.sanitize("<p>Chào</p>"))).isTrue();
        assertThat(HtmlSanitizer.hasContent(HtmlSanitizer.sanitize("<img src=\"https://cdn.example/a.png\">"))).isTrue();
    }
}
