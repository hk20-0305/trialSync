package com.trialsync.backend.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The small text and timestamp conversions the patient-data contract depends on.
 *
 * <p>Each one reproduces a specific Python expression byte for byte; they live together because
 * getting any of them subtly wrong changes a stored key, a duplicate-detection outcome or an audit
 * payload rather than failing loudly.
 */
public final class PatientDataFormats {

    /** Matches {@code re.sub(r"[^a-z0-9]+", "_", ...)} from {@code catalog_key()}. */
    private static final Pattern NON_KEY_CHARACTERS = Pattern.compile("[^a-z0-9]+");

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    /** {@code datetime.isoformat()} prints microseconds only when they are non-zero. */
    private static final DateTimeFormatter WITH_MICROSECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx", Locale.ROOT);

    private static final DateTimeFormatter WITHOUT_MICROSECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx", Locale.ROOT);

    private PatientDataFormats() {}

    /**
     * Collapses internal whitespace and trims, as {@code " ".join(value.split())} does.
     *
     * <p>Used where the caller keeps an empty result and lets a length rule reject it, which is how
     * a fact's removal reason is normalized.
     */
    public static String collapse(String value) {
        if (value == null) {
            return null;
        }
        return WHITESPACE_RUN.matcher(value.strip()).replaceAll(" ");
    }

    /**
     * Collapses whitespace and turns an empty result into {@code null}, as the Pydantic validators
     * that end in {@code return normalized or None} do.
     */
    public static String collapseToNull(String value) {
        String collapsed = collapse(value);
        return collapsed == null || collapsed.isEmpty() ? null : collapsed;
    }

    /**
     * Derives a catalog concept key from an admin-entered label.
     *
     * <p>Port of {@code re.sub(r"[^a-z0-9]+", "_", label.lower()).strip("_")[:80]}. The order
     * matters: underscores are stripped before the length cap, so a label that is truncated cannot
     * end up with a trailing separator. "C-reactive protein" becomes "c_reactive_protein".
     */
    public static String catalogKey(String label) {
        if (label == null) {
            return "";
        }
        String replaced = NON_KEY_CHARACTERS.matcher(label.toLowerCase(Locale.ROOT)).replaceAll("_");
        int start = 0;
        int end = replaced.length();
        while (start < end && replaced.charAt(start) == '_') {
            start++;
        }
        while (end > start && replaced.charAt(end - 1) == '_') {
            end--;
        }
        String stripped = replaced.substring(start, end);
        return stripped.length() <= 80 ? stripped : stripped.substring(0, 80);
    }

    /**
     * Renders a timestamp the way {@code datetime.isoformat()} does, for embedding inside audit
     * payloads and error details.
     *
     * <p>Two differences from {@link OffsetDateTime#toString()} matter: a zero offset is written as
     * {@code +00:00} rather than {@code Z}, and a non-zero sub-second part is always six digits
     * rather than being trimmed of trailing zeros.
     */
    public static String isoformat(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return (value.getNano() == 0 ? WITHOUT_MICROSECONDS : WITH_MICROSECONDS).format(value);
    }
}
