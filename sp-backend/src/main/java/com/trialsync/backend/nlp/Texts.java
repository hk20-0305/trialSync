package com.trialsync.backend.nlp;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Small text helpers that reproduce Python string semantics exactly where the migrated code depends
 * on them.
 *
 * <p>Python indexes and slices {@code str} by code point and Java indexes {@code String} by UTF-16
 * unit. Every place the NLP layer truncates a label, bounds a suggestion or re-checks a quoted
 * source span is a place where that difference would change behaviour on non-BMP input, so those
 * operations go through this class rather than through {@code substring}/{@code length}.
 */
public final class Texts {

    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private Texts() {}

    /**
     * Equivalent of {@code str.casefold()} for the ASCII prompts this service matches against.
     *
     * <p>{@code casefold()} is more aggressive than {@code lower()} for a handful of non-ASCII
     * letters; every guardrail pattern in the Python module is plain lowercase ASCII, so the
     * distinction cannot change which branch is taken.
     */
    public static String casefold(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    /** {@code len(value)}: the number of code points, not UTF-16 units. */
    public static int length(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    /** {@code value[:limit]}, counted in code points and never splitting a surrogate pair. */
    public static String truncate(String value, int limit) {
        if (value == null || limit <= 0) {
            return value == null ? null : "";
        }
        int total = value.codePointCount(0, value.length());
        if (total <= limit) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, limit));
    }

    /**
     * {@code value[start:end]} with Python's clamping semantics: out-of-range bounds narrow the
     * slice instead of raising, and an inverted range yields an empty string.
     */
    public static String slice(String value, int start, int end) {
        if (value == null) {
            return "";
        }
        int total = value.codePointCount(0, value.length());
        int from = Math.max(0, Math.min(start, total));
        int to = Math.max(0, Math.min(end, total));
        if (to <= from) {
            return "";
        }
        return value.substring(
                value.offsetByCodePoints(0, from), value.offsetByCodePoints(0, to));
    }

    /** {@code " ".join(value.split()).strip()}: collapse every whitespace run to a single space. */
    public static String collapseWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return WHITESPACE.matcher(value).replaceAll(" ").strip();
    }
}
