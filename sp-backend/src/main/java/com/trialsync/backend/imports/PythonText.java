package com.trialsync.backend.imports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text primitives with Python's exact semantics.
 *
 * <p>The import pipeline stores character offsets that clients send back and that the server then
 * validates against persisted spans, so "close enough" string handling is not good enough here.
 * Three differences between the two languages matter and are neutralised by this class:
 *
 * <ul>
 *   <li><b>Indexing.</b> A Python {@code str} is indexed by code point; a Java {@code String} is
 *       indexed by UTF-16 unit. Every offset the parser records is therefore converted to a code
 *       point index, so a document containing an astral character produces the same
 *       {@code start}/{@code end} numbers in both implementations.
 *   <li><b>Whitespace.</b> {@code str.strip()} and the regex {@code \s} class use
 *       {@code str.isspace()}, which counts NBSP, U+2007, U+202F and U+0085 as space.
 *       {@link Character#isWhitespace} deliberately excludes the non-breaking ones, so a dedicated
 *       predicate is used instead.
 *   <li><b>Rounding.</b> {@code round(value, digits)} rounds half to even against the exact binary
 *       value of the double, not against its shortest decimal representation.
 * </ul>
 */
public final class PythonText {

    private PythonText() {}

    /**
     * {@code str.isspace()} for one code point.
     *
     * <p>{@link Character#isWhitespace} covers the ASCII controls and the separators Python treats
     * as space except the non-breaking ones; {@link Character#isSpaceChar} adds those (U+00A0,
     * U+2007, U+202F) along with the line and paragraph separators. U+0085 belongs to neither in
     * Java but is whitespace in Python. Note that U+200B is excluded by both, which is correct.
     */
    public static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0x0085;
    }

    /**
     * {@code str.isprintable()} for one code point: everything outside the Cc, Cf, Cs, Co, Cn, Zl,
     * Zp and Zs categories, plus the ordinary space, which Python considers printable.
     */
    public static boolean isPrintable(int codePoint) {
        if (codePoint == ' ') {
            return true;
        }
        int type = Character.getType(codePoint);
        return type != Character.CONTROL
                && type != Character.FORMAT
                && type != Character.SURROGATE
                && type != Character.PRIVATE_USE
                && type != Character.UNASSIGNED
                && type != Character.LINE_SEPARATOR
                && type != Character.PARAGRAPH_SEPARATOR
                && type != Character.SPACE_SEPARATOR;
    }

    /**
     * {@code str.isalnum()} for one code point: alphabetic, or any of the three numeric categories
     * Python's {@code isdecimal}/{@code isdigit}/{@code isnumeric} accept.
     */
    public static boolean isAlnum(int codePoint) {
        if (Character.isLetter(codePoint)) {
            return true;
        }
        int type = Character.getType(codePoint);
        return type == Character.DECIMAL_DIGIT_NUMBER
                || type == Character.LETTER_NUMBER
                || type == Character.OTHER_NUMBER;
    }

    /** {@code str.strip()}: removes leading and trailing Python whitespace. */
    public static String strip(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isSpace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isSpace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    /** {@code str.rstrip(characters)}: removes every trailing occurrence of the given characters. */
    public static String rstrip(String value, String characters) {
        int end = value.length();
        while (end > 0 && characters.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }

    /**
     * {@code str.casefold()}. Java has no case folding, and root-locale lowercasing agrees with it
     * for every character the clinical vocabulary uses. The two differ only for a handful of
     * special-cased code points such as U+00DF, which folds to {@code "ss"} but lowercases to
     * itself.
     */
    public static String casefold(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    /** {@code str.lower()}, pinned to the root locale so a Turkish default cannot change it. */
    public static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * {@code str.splitlines(keepends=True)}.
     *
     * <p>Python splits on considerably more than {@code \n}: the vertical tab, form feed, the file
     * and group separators, NEL and the Unicode line and paragraph separators all end a line, and
     * {@code \r\n} counts as one break.
     */
    public static List<String> splitLinesKeepEnds(String value) {
        List<String> lines = new ArrayList<>();
        int length = value.length();
        int start = 0;
        int index = 0;
        while (index < length) {
            char current = value.charAt(index);
            int breakLength = lineBreakLength(value, index, current);
            if (breakLength == 0) {
                index++;
                continue;
            }
            lines.add(value.substring(start, index + breakLength));
            index += breakLength;
            start = index;
        }
        if (start < length) {
            lines.add(value.substring(start));
        }
        return lines;
    }

    private static int lineBreakLength(String value, int index, char current) {
        if (current == '\r') {
            return index + 1 < value.length() && value.charAt(index + 1) == '\n' ? 2 : 1;
        }
        return switch (current) {
            case '\n', '', '\f', '', '', '', '', ' ', ' ' ->
                    1;
            default -> 0;
        };
    }

    /** Normalises CRLF and lone CR to LF, the first step of every extraction path. */
    public static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace("\r", "\n");
    }

    /** {@code len(text)}: the number of code points, not the number of UTF-16 units. */
    public static int length(String value) {
        return value.codePointCount(0, value.length());
    }

    /** Converts a UTF-16 index - what a {@code Matcher} reports - to a Python code point index. */
    public static int codePointIndex(String value, int charIndex) {
        return value.codePointCount(0, charIndex);
    }

    /** {@code str.find(sub)} returning a code point index, or {@code -1} when absent. */
    public static int find(String value, String sub) {
        int charIndex = value.indexOf(sub);
        return charIndex < 0 ? -1 : value.codePointCount(0, charIndex);
    }

    /**
     * {@code round(value, digits)}.
     *
     * <p>Python rounds half to even against the exact value of the double. Constructing the
     * {@link BigDecimal} from the {@code double} - rather than through {@code BigDecimal.valueOf},
     * which goes via the shortest decimal representation - reproduces that, so a value such as
     * {@code 2.675} rounds down to {@code 2.67} in both languages.
     */
    public static double round(double value, int digits) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return new BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** Removes every Python whitespace code point, the effect of {@code "".join(value.split())}. */
    public static String removeWhitespace(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        value.codePoints().filter(codePoint -> !isSpace(codePoint)).forEach(builder::appendCodePoint);
        return builder.toString();
    }
}
