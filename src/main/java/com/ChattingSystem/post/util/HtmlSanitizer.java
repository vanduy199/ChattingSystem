package com.ChattingSystem.post.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sanitizes blog post bodies (Tiptap HTML) against the allow-list in the frontend contract ("Post HTML").
 * <ul>
 *   <li>{@code h1} becomes {@code h2} (the post title is the page's only h1);</li>
 *   <li>{@code a[href]}: http, https, mailto, or relative {@code /path} and {@code #anchor};
 *       {@code img[src]}: http, https;</li>
 *   <li>{@code code[class]}/{@code pre[class]} keep only {@code language-*} classes;</li>
 *   <li>{@code target="_blank"} links get {@code rel="noopener noreferrer"};</li>
 *   <li>everything else (scripts, styles, iframes, event handlers, inline style, javascript:/data: URLs) is removed.</li>
 * </ul>
 */
public final class HtmlSanitizer {

    private static final String[] ALLOWED_TAGS = {
            "p", "br", "h2", "h3", "h4", "strong", "b", "em", "i", "u", "s", "code", "pre",
            "blockquote", "ul", "ol", "li", "a", "img", "hr", "figure", "figcaption",
            "table", "thead", "tbody", "tr", "th", "td"
    };

    private static final Pattern LANGUAGE_CLASS = Pattern.compile("^language-[a-z0-9+-]+$");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\p{Z}]+");
    private static final Pattern HAS_WHITESPACE = Pattern.compile(".*\\s.*", Pattern.DOTALL);
    private static final String BLANK_TARGET_REL = "noopener noreferrer";

    private static final Safelist SAFELIST = new PostSafelist()
            .addTags(ALLOWED_TAGS)
            .addAttributes("a", "href", "title", "target", "rel")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("code", "class")
            .addAttributes("pre", "class")
            .addProtocols("img", "src", "http", "https");

    private HtmlSanitizer() {}

    /** @return the sanitized HTML (not pretty-printed); empty for null/blank input. */
    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document dirty = Jsoup.parseBodyFragment(html);
        dirty.select("h1").tagName("h2");
        dirty.select("code[class], pre[class]").forEach(HtmlSanitizer::keepLanguageClasses);

        Document clean = new Cleaner(SAFELIST).clean(dirty);
        clean.select("a[target]").forEach(link -> {
            if ("_blank".equalsIgnoreCase(link.attr("target").trim())) {
                link.attr("target", "_blank");
                link.attr("rel", BLANK_TARGET_REL);
            }
        });
        clean.outputSettings().prettyPrint(false);
        return clean.body().html();
    }

    /** @return whether the (sanitized) HTML has any visible text or at least one image. */
    public static boolean hasContent(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        Document document = Jsoup.parseBodyFragment(html);
        return !WHITESPACE.matcher(document.body().text()).replaceAll("").isEmpty()
                || !document.select("img[src]").isEmpty();
    }

    private static void keepLanguageClasses(Element element) {
        String languageClasses = Arrays.stream(WHITESPACE.split(element.attr("class").trim()))
                .filter(token -> LANGUAGE_CLASS.matcher(token).matches())
                .collect(Collectors.joining(" "));
        if (languageClasses.isEmpty()) {
            element.removeAttr("class");
        } else {
            element.attr("class", languageClasses);
        }
    }

    /**
     * jsoup's protocol check needs a base URI to accept relative links, which would also let through
     * relative {@code img} sources and other paths; {@code a[href]} is therefore checked here instead.
     */
    private static final class PostSafelist extends Safelist {

        @Override
        public boolean isSafeAttribute(String tagName, Element element, Attribute attribute) {
            if ("a".equals(tagName) && "href".equalsIgnoreCase(attribute.getKey())) {
                String href = attribute.getValue().trim();
                if (!isSafeHref(href)) {
                    return false;
                }
                attribute.setValue(href);
                return true;
            }
            if (("code".equals(tagName) || "pre".equals(tagName)) && "class".equalsIgnoreCase(attribute.getKey())) {
                // non-language classes were already stripped in sanitize(); this is the final gate
                return Arrays.stream(attribute.getValue().split(" "))
                        .allMatch(token -> LANGUAGE_CLASS.matcher(token).matches());
            }
            return super.isSafeAttribute(tagName, element, attribute);
        }

        private static boolean isSafeHref(String href) {
            if (href.startsWith("#")) {
                return href.length() > 1 && !HAS_WHITESPACE.matcher(href).matches();
            }
            if (href.startsWith("/")) {
                // "//host" is protocol-relative and "/\host" is treated like it by browsers
                return href.length() == 1 || (href.charAt(1) != '/' && href.charAt(1) != '\\');
            }
            String lower = href.toLowerCase(Locale.ROOT);
            return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:");
        }
    }
}
