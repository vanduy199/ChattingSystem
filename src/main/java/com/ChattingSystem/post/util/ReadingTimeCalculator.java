package com.ChattingSystem.post.util;

import org.jsoup.Jsoup;

import java.util.regex.Pattern;

/** {@code readingMinutes = ceil(words / 200)}, at least 1; words are counted in the HTML's plain text. */
public final class ReadingTimeCalculator {

    public static final int WORDS_PER_MINUTE = 200;

    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");
    /** A token counts as a word only if it contains a letter or digit (skips "-", "—", "•", ...). */
    private static final Pattern HAS_LETTER_OR_DIGIT = Pattern.compile("[\\p{L}\\p{N}]");

    private ReadingTimeCalculator() {}

    public static int countWords(String html) {
        if (html == null || html.isBlank()) {
            return 0;
        }
        String text = Jsoup.parseBodyFragment(html).body().text().trim();
        if (text.isEmpty()) {
            return 0;
        }
        int words = 0;
        for (String token : WHITESPACE.split(text)) {
            if (HAS_LETTER_OR_DIGIT.matcher(token).find()) {
                words++;
            }
        }
        return words;
    }

    public static int readingMinutes(String html) {
        int words = countWords(html);
        return Math.max(1, (words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE);
    }
}
