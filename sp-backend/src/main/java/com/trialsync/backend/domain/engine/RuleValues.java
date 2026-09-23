package com.trialsync.backend.domain.engine;

import com.trialsync.backend.domain.model.EvidenceReference;
import com.trialsync.backend.domain.model.MissingRequirement;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Value-level helpers shared by the rule evaluator.
 *
 * <p>Several of these exist purely to reproduce Python semantics that Java does not share by
 * default — string formatting of {@code None}, numeric coercion that rejects booleans, and
 * scale-insensitive decimal equality.
 */
final class RuleValues {

    /**
     * Unit synonyms accepted by the engine, copied verbatim from {@code UNIT_ALIASES}. Note that
     * the micro-symbol variant of eGFR normalizes onto the ASCII spelling.
     */
    private static final Map<String, String> UNIT_ALIASES = Map.of(
            "%", "%",
            "percent", "%",
            "year", "year",
            "years", "year",
            "ml/min/1.73m2", "ml/min/1.73m2",
            "ml/min/1.73m²", "ml/min/1.73m2");

    private RuleValues() {}

    /**
     * Reproduces Python's {@code str(value)} for the values that can appear in a rule expression.
     *
     * <p>This matters because two engine messages interpolate raw expression members, so a missing
     * unit has to render as the literal text {@code None} rather than {@code null}.
     */
    static String pythonStr(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof Boolean flag) {
            return flag ? "True" : "False";
        }
        return String.valueOf(value);
    }

    /**
     * Port of {@code _decimal}. Booleans and nulls are rejected outright — Python excludes
     * {@code bool} explicitly even though it is a subclass of {@code int}.
     */
    static BigDecimal toDecimal(Object value) {
        if (value == null || value instanceof Boolean) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * True when the value is an integer in Python's sense: {@code isinstance(x, int)} excluding
     * {@code bool}. A JSON {@code 30.0} is a float in Python and must therefore be rejected.
     */
    static Integer asPythonInt(Object value) {
        if (value instanceof Boolean) {
            return null;
        }
        if (value instanceof Integer number) {
            return number;
        }
        if (value instanceof Long number) {
            return number.intValue();
        }
        if (value instanceof Short number) {
            return number.intValue();
        }
        if (value instanceof java.math.BigInteger number) {
            return number.intValue();
        }
        return null;
    }

    /** Port of {@code _unit_key}: strip, lower-case, and drop internal spaces. */
    static String unitKey(String value) {
        return value.strip().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    /**
     * Port of {@code _units_match}. A non-string expectation (including an absent unit) only
     * matches a fact that carries no unit at all.
     */
    static boolean unitsMatch(String actual, Object expected) {
        if (!(expected instanceof String expectedUnit)) {
            return actual == null;
        }
        if (actual == null) {
            return false;
        }
        return canonicalUnit(actual).equals(canonicalUnit(expectedUnit));
    }

    private static String canonicalUnit(String value) {
        String key = unitKey(value);
        return UNIT_ALIASES.getOrDefault(key, key);
    }

    /**
     * Port of {@code _unique_evidence}: de-duplicate by fact id, keeping first-appearance order but
     * the last value seen. {@link LinkedHashMap#put} preserves the original slot on re-insert,
     * which is exactly what a Python dict comprehension does.
     */
    static List<EvidenceReference> uniqueEvidence(List<EvidenceReference> items) {
        Map<String, EvidenceReference> unique = new LinkedHashMap<>();
        for (EvidenceReference item : items) {
            unique.put(item.factId(), item);
        }
        return List.copyOf(unique.values());
    }

    /**
     * Port of {@code _unique_missing}: de-duplicate on the whole {@code (fact, reason, detail)}
     * triple. {@link MissingRequirement} is a record over precisely those three members, so record
     * equality is the triple and a {@link LinkedHashSet} suffices.
     */
    static List<MissingRequirement> uniqueMissing(List<MissingRequirement> items) {
        return List.copyOf(new LinkedHashSet<>(items));
    }

    /** Concatenates two lists into a fresh mutable list, the equivalent of Python's tuple {@code +}. */
    static <T> List<T> concat(List<T> first, List<T> second) {
        List<T> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return combined;
    }

    /**
     * Counts numerically distinct decimals, mirroring Python's {@code len({...})} over a set of
     * {@code Decimal}. {@code BigDecimal.equals} compares scale as well as value, so {@code 7.2}
     * and {@code 7.20} would wrongly count as two; {@code compareTo} is the correct comparison.
     */
    static boolean hasConflictingDecimals(List<BigDecimal> values) {
        for (int index = 1; index < values.size(); index++) {
            if (values.get(0).compareTo(values.get(index)) != 0) {
                return true;
            }
        }
        return false;
    }
}
