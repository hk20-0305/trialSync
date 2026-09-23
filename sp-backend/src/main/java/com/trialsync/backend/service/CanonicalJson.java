package com.trialsync.backend.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Byte-for-byte reproduction of CPython's {@code json.dumps(value, sort_keys=True,
 * separators=(",", ":"))}.
 *
 * <p>This exists because the patient snapshot's {@code content_hash} is a SHA-256 over exactly those
 * bytes. The hash decides whether an unchanged patient reuses its snapshot, it is stored as the
 * snapshot version, and it is printed on the audit report - so a single byte of serializer drift
 * would silently fork every snapshot identity between the Python service and this one. Jackson is
 * deliberately not used here: its defaults differ in key ordering, in non-ASCII handling, and in how
 * it renders {@link BigDecimal}, and any future change to the shared {@code ObjectMapper}
 * configuration would change hashes retroactively.
 *
 * <p>The rules being reproduced, all of them observable in {@code json/encoder.py}:
 *
 * <ul>
 *   <li><b>Separators.</b> {@code ","} between items and {@code ":"} between key and value, with no
 *       whitespace anywhere.
 *   <li><b>Key order.</b> {@code sort_keys=True} sorts by the key string. Python compares strings by
 *       Unicode code point, so {@link #PYTHON_STRING_ORDER} compares code points rather than using
 *       {@link String#compareTo}, which compares UTF-16 code units and disagrees for supplementary
 *       characters.
 *   <li><b>Escaping.</b> {@code ensure_ascii=True} is the default, so everything outside the
 *       printable ASCII range {@code U+0020}-{@code U+007E} is escaped as {@code \\uXXXX} with
 *       lower-case hex, apart from the short forms {@code \\b \\t \\n \\f \\r}. Only {@code "} and
 *       {@code \\} are escaped inside that range - notably {@code /} is left alone. Java strings are
 *       already UTF-16, so a supplementary character is escaped as its two surrogates, which is what
 *       Python's encoder emits as well.
 *   <li><b>Null.</b> {@code None} renders as {@code null}.
 * </ul>
 *
 * <p>The snapshot payload only ever contains strings, nulls, maps and lists: numeric fact values are
 * pre-formatted with {@code str(Decimal)} before they reach the encoder. Numbers and booleans are
 * still handled, for the exact integral types whose Java rendering is identical to Python's, so that
 * a caller cannot silently get a wrong hash from a value this encoder was never designed for.
 */
public final class CanonicalJson {

    /**
     * Orders strings the way Python's {@code <} does: by Unicode code point.
     *
     * <p>For the ASCII keys in the snapshot payload this is identical to {@link String#compareTo},
     * but it is written out in full so a future non-ASCII key cannot quietly change a hash.
     */
    public static final Comparator<String> PYTHON_STRING_ORDER = CanonicalJson::compareByCodePoint;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private CanonicalJson() {}

    /** Serializes {@code value} to the canonical form and returns it as a string. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder(256);
        writeValue(out, value);
        return out.toString();
    }

    /** Port of Python's {@code str1 < str2} for the sort keys used by the snapshot payload. */
    public static int compareByCodePoint(String left, String right) {
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length() && rightIndex < right.length()) {
            int leftPoint = left.codePointAt(leftIndex);
            int rightPoint = right.codePointAt(rightIndex);
            if (leftPoint != rightPoint) {
                return Integer.compare(leftPoint, rightPoint);
            }
            leftIndex += Character.charCount(leftPoint);
            rightIndex += Character.charCount(rightPoint);
        }
        return Integer.compare(left.length() - leftIndex, right.length() - rightIndex);
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(out, text);
        } else if (value instanceof Map<?, ?> map) {
            writeObject(out, map);
        } else if (value instanceof Collection<?> items) {
            writeArray(out, items);
        } else if (value instanceof Boolean flag) {
            out.append(flag ? "true" : "false");
        } else if (value instanceof BigDecimal number) {
            // str(Decimal) and BigDecimal.toString() agree on scale, sign and exponent form.
            out.append(number.toString());
        } else if (value instanceof Integer || value instanceof Long || value instanceof BigInteger) {
            out.append(value.toString());
        } else {
            throw new IllegalArgumentException(
                    "Canonical JSON cannot encode "
                            + value.getClass().getName()
                            + "; Python's encoder would render it differently.");
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map) {
        List<String> keys = new ArrayList<>(map.size());
        for (Object key : map.keySet()) {
            if (!(key instanceof String text)) {
                throw new IllegalArgumentException(
                        "Canonical JSON requires string keys; got "
                                + (key == null ? "null" : key.getClass().getName()));
            }
            keys.add(text);
        }
        keys.sort(PYTHON_STRING_ORDER);
        out.append('{');
        boolean first = true;
        for (String key : keys) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(out, key);
            out.append(':');
            writeValue(out, map.get(key));
        }
        out.append('}');
    }

    private static void writeArray(StringBuilder out, Collection<?> items) {
        out.append('[');
        boolean first = true;
        for (Object item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(out, item);
        }
        out.append(']');
    }

    /** Port of {@code json.encoder.py_encode_basestring_ascii}. */
    private static void writeString(StringBuilder out, String text) {
        out.append('"');
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character >= 0x20 && character <= 0x7E) {
                        out.append(character);
                    } else {
                        out.append("\\u")
                                .append(HEX[(character >> 12) & 0xF])
                                .append(HEX[(character >> 8) & 0xF])
                                .append(HEX[(character >> 4) & 0xF])
                                .append(HEX[character & 0xF]);
                    }
                }
            }
        }
        out.append('"');
    }
}
