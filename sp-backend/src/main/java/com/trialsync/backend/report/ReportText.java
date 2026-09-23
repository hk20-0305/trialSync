package com.trialsync.backend.report;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Turns stored values into printable text. Port of {@code _safe_text}.
 *
 * <p>One deliberate difference: reportlab parses its paragraph text as a small XML dialect, so the
 * Python helper runs {@code xml.sax.saxutils.escape} and rewrites newlines as {@code <br/>}. PDFBox
 * draws literal strings, so escaping here would print {@code &amp;} where the PDF should read
 * {@code &}. The escaping is therefore dropped and newlines are kept as newlines, which
 * {@link ParagraphFlowable} renders as hard line breaks. The visible text is identical either way -
 * that is the point of the substitution, not an approximation of it.
 */
public final class ReportText {

    /** The em dash the template prints for absent values. */
    public static final String EMPTY = "—";

    private ReportText() {}

    /** Port of {@code _safe_text(value)}. */
    public static String safeText(Object value) {
        if (value == null || (value instanceof String text && text.isEmpty())) {
            return EMPTY;
        }
        if (value instanceof Map<?, ?> || value instanceof Collection<?> || value instanceof Object[]) {
            return pythonJson(value);
        }
        return String.valueOf(value);
    }

    /**
     * Port of {@code json.dumps(value, ensure_ascii=False, sort_keys=True, default=str)}, used when a
     * stored evidence value is itself structured.
     *
     * <p>The default separators are kept - {@code ", "} and {@code ": "} - because this string is
     * read by a human, not hashed. It is the opposite choice from
     * {@code com.trialsync.backend.service.CanonicalJson}, which is compact precisely because its
     * output feeds a digest.
     */
    public static String pythonJson(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(out, text);
        } else if (value instanceof Boolean flag) {
            out.append(flag ? "true" : "false");
        } else if (value instanceof Map<?, ?> map) {
            writeObject(out, map);
        } else if (value instanceof Collection<?> collection) {
            writeArray(out, collection);
        } else if (value instanceof Object[] array) {
            writeArray(out, List.of(array));
        } else if (value instanceof Number number) {
            out.append(number);
        } else {
            // Python's default=str: anything unserialisable is printed via its string form.
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map) {
        List<Map.Entry<?, ?>> entries = new ArrayList<>(map.size());
        entries.addAll(map.entrySet());
        entries.sort(
                (left, right) ->
                        com.trialsync.backend.service.CanonicalJson.compareByCodePoint(
                                String.valueOf(left.getKey()), String.valueOf(right.getKey())));
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : entries) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            writeString(out, String.valueOf(entry.getKey()));
            out.append(": ");
            writeValue(out, entry.getValue());
        }
        out.append('}');
    }

    private static void writeArray(StringBuilder out, Collection<?> values) {
        out.append('[');
        boolean first = true;
        for (Object item : values) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            writeValue(out, item);
        }
        out.append(']');
    }

    /** {@code ensure_ascii=False}: only quotes, backslashes and control characters are escaped. */
    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) {
                        out.append(String.format("\\u%04x", (int) character));
                    } else {
                        out.append(character);
                    }
                }
            }
        }
        out.append('"');
    }
}
