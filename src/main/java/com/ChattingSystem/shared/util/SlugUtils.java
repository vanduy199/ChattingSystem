package com.ChattingSystem.shared.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds URL slugs (kebab-case, {@code [a-z0-9-]}) from free text, stripping Vietnamese diacritics.
 */
public final class SlugUtils {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

    private SlugUtils() {}

    public static String slugify(String input) {
        return slugify(input, Integer.MAX_VALUE);
    }

    /**
     * @return the slug, cut to at most {@code maxLength} characters without a trailing dash;
     *         empty when the input has no letters or digits.
     */
    public static String slugify(String input, int maxLength) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String text = input.replace('đ', 'd').replace('Đ', 'D');
        text = Normalizer.normalize(text, Normalizer.Form.NFD);
        text = COMBINING_MARKS.matcher(text).replaceAll("");
        text = text.toLowerCase(Locale.ROOT);
        text = NON_ALPHANUMERIC.matcher(text).replaceAll("-");
        text = EDGE_DASHES.matcher(text).replaceAll("");

        if (text.length() > maxLength) {
            text = EDGE_DASHES.matcher(text.substring(0, maxLength)).replaceAll("");
        }
        return text;
    }
}
