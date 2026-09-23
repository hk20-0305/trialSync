package com.trialsync.backend.imports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Collects field-level validation failures in the shape Pydantic's
 * {@code ValidationError.errors(include_url=False)} produces, so the {@code details} array of an
 * {@code IMPORT_REVIEW_INVALID} response keeps telling a reviewer's client which field went wrong
 * and why.
 *
 * <p>Every error is a map with the keys {@code type}, {@code loc}, {@code msg}, {@code input} and,
 * where Pydantic supplies one, {@code ctx} - in that order, because {@code dict(item)} preserved
 * it. {@code loc} is the path from the document root, mixing field names and list indices exactly as
 * the tuple did.
 *
 * <p>The structural and constraint messages are reproduced verbatim. The messages for
 * <em>parse</em> failures - a malformed UUID, an unparseable date - carry Pydantic's stable prefix
 * but not its parser-internal suffix, which describes the offending byte position and is not
 * reconstructable outside pydantic-core. The {@code type} discriminator, which is what clients
 * branch on, is correct in both cases.
 */
final class CandidateErrors {

    private final List<Map<String, Object>> errors = new ArrayList<>();

    void add(String type, List<Object> loc, String message, JsonNode input) {
        add(type, loc, message, input, null);
    }

    void add(
            String type,
            List<Object> loc,
            String message,
            JsonNode input,
            Map<String, Object> context) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", type);
        error.put("loc", List.copyOf(loc));
        error.put("msg", message);
        error.put("input", input);
        if (context != null) {
            error.put("ctx", context);
        }
        errors.add(error);
    }

    boolean isEmpty() {
        return errors.isEmpty();
    }

    List<Map<String, Object>> toList() {
        return List.copyOf(errors);
    }

    /** Throws when anything was collected; a no-op otherwise. */
    void raiseIfAny() {
        if (!errors.isEmpty()) {
            throw new ImportCandidateValidationException(toList());
        }
    }

    static List<Object> path(List<Object> parent, Object segment) {
        List<Object> location = new ArrayList<>(parent.size() + 1);
        location.addAll(parent);
        location.add(segment);
        return location;
    }

    static Map<String, Object> context(String key, Object value) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(key, value);
        return context;
    }

    // ---------------------------------------------------------------- readers

    /**
     * Pydantic treats an absent key and an explicit {@code null} differently for a required field:
     * the first is {@code missing}, the second is a type error against the field's own type.
     */
    static JsonNode field(ObjectNode parent, String name) {
        return parent.get(name);
    }

    static void missing(CandidateErrors errors, List<Object> loc, JsonNode parent) {
        errors.add("missing", loc, "Field required", parent);
    }

    /**
     * {@code str} with optional bounds. Lengths are counted in code points, as {@code len()} does,
     * so a name of emoji is measured the same way in both runtimes.
     */
    static String string(
            CandidateErrors errors,
            List<Object> loc,
            JsonNode node,
            int minLength,
            int maxLength) {
        if (!node.isTextual()) {
            errors.add("string_type", loc, "Input should be a valid string", node);
            return null;
        }
        String value = node.asText();
        int length = PythonText.length(value);
        if (minLength > 0 && length < minLength) {
            errors.add(
                    "string_too_short",
                    loc,
                    "String should have at least "
                            + minLength
                            + (minLength == 1 ? " character" : " characters"),
                    node,
                    context("min_length", minLength));
            return null;
        }
        if (maxLength > 0 && length > maxLength) {
            errors.add(
                    "string_too_long",
                    loc,
                    "String should have at most "
                            + maxLength
                            + (maxLength == 1 ? " character" : " characters"),
                    node,
                    context("max_length", maxLength));
            return null;
        }
        return value;
    }

    /** {@code int} with an optional lower bound, in Pydantic's lax mode. */
    static Integer integer(CandidateErrors errors, List<Object> loc, JsonNode node, Integer atLeast) {
        Integer value = null;
        if (node.isIntegralNumber() && node.canConvertToInt() && !node.isBoolean()) {
            value = node.intValue();
        } else if (node.isFloatingPointNumber()) {
            double raw = node.doubleValue();
            if (raw == Math.rint(raw) && !Double.isInfinite(raw)) {
                value = (int) raw;
            } else {
                errors.add(
                        "int_from_float",
                        loc,
                        "Input should be a valid integer, got a number with a fractional part",
                        node);
                return null;
            }
        } else if (node.isTextual()) {
            try {
                value = Integer.valueOf(node.asText().trim());
            } catch (NumberFormatException exception) {
                errors.add(
                        "int_parsing",
                        loc,
                        "Input should be a valid integer, unable to parse string as an integer",
                        node);
                return null;
            }
        } else {
            errors.add(
                    "int_type",
                    loc,
                    "Input should be a valid integer",
                    node);
            return null;
        }
        if (atLeast != null && value < atLeast) {
            errors.add(
                    "greater_than_equal",
                    loc,
                    "Input should be greater than or equal to " + atLeast,
                    node,
                    context("ge", atLeast));
            return null;
        }
        return value;
    }

    /** {@code bool} in Pydantic's lax mode, including the string and 0/1 spellings it accepts. */
    static Boolean bool(CandidateErrors errors, List<Object> loc, JsonNode node) {
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            int value = node.intValue();
            if (value == 0 || value == 1) {
                return value == 1;
            }
            errors.add("bool_parsing", loc, "Input should be a valid boolean, unable to interpret input", node);
            return null;
        }
        if (node.isTextual()) {
            String value = PythonText.lower(node.asText().trim());
            return switch (value) {
                case "true", "t", "yes", "y", "on", "1" -> Boolean.TRUE;
                case "false", "f", "no", "n", "off", "0" -> Boolean.FALSE;
                default -> {
                    errors.add(
                            "bool_parsing",
                            loc,
                            "Input should be a valid boolean, unable to interpret input",
                            node);
                    yield null;
                }
            };
        }
        errors.add("bool_type", loc, "Input should be a valid boolean", node);
        return null;
    }

    static UUID uuid(CandidateErrors errors, List<Object> loc, JsonNode node) {
        if (!node.isTextual()) {
            errors.add(
                    "uuid_type",
                    loc,
                    "UUID input should be a string, bytes or UUID object",
                    node);
            return null;
        }
        String raw = node.asText();
        String candidate =
                PythonText.lower(raw).startsWith("urn:uuid:") ? raw.substring("urn:uuid:".length()) : raw;
        try {
            UUID parsed = UUID.fromString(candidate);
            // `UUID.fromString` is lenient about short groups where Python's parser is not.
            if (!parsed.toString().equalsIgnoreCase(candidate)) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            errors.add("uuid_parsing", loc, "Input should be a valid UUID", node);
            return null;
        }
    }

    static LocalDate date(CandidateErrors errors, List<Object> loc, JsonNode node) {
        if (!node.isTextual()) {
            errors.add("date_type", loc, "Input should be a valid date", node);
            return null;
        }
        try {
            return LocalDate.parse(node.asText());
        } catch (DateTimeParseException exception) {
            errors.add(
                    "date_from_datetime_parsing",
                    loc,
                    "Input should be a valid date or datetime",
                    node);
            return null;
        }
    }

    /**
     * {@code Decimal | None}.
     *
     * <p>A JSON number is converted through its shortest decimal representation, which is what
     * Pydantic's {@code Decimal(str(value))} does, so {@code 7.4} becomes {@code Decimal("7.4")}
     * rather than the exact binary expansion.
     */
    static BigDecimal decimal(CandidateErrors errors, List<Object> loc, JsonNode node) {
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText().trim());
            } catch (NumberFormatException exception) {
                errors.add("decimal_parsing", loc, "Input should be a valid decimal", node);
                return null;
            }
        }
        if (node.isNumber() && !node.isBoolean()) {
            return node.isFloatingPointNumber()
                    ? BigDecimal.valueOf(node.doubleValue())
                    : node.decimalValue();
        }
        errors.add("decimal_type", loc, "Input should be a valid decimal", node);
        return null;
    }

    /** {@code Enum} and {@code Literal}: one message, one {@code expected} context entry. */
    static String choice(
            CandidateErrors errors,
            List<Object> loc,
            JsonNode node,
            List<String> permitted,
            String type) {
        if (node.isTextual()) {
            String value = node.asText();
            if (permitted.contains(value)) {
                return value;
            }
        }
        String expected = expected(permitted);
        errors.add(type, loc, "Input should be " + expected, node, context("expected", expected));
        return null;
    }

    /** Pydantic renders a choice list as {@code 'a', 'b' or 'c'}. */
    static String expected(List<String> permitted) {
        List<String> quoted = permitted.stream().map(value -> "'" + value + "'").toList();
        if (quoted.size() == 1) {
            return quoted.get(0);
        }
        return String.join(", ", quoted.subList(0, quoted.size() - 1))
                + " or "
                + quoted.get(quoted.size() - 1);
    }

    /** The {@code max_length} bound on a list field, checked after its items were validated. */
    static void listTooLong(
            CandidateErrors errors, List<Object> loc, JsonNode node, int maxLength, int actual) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("field_type", "List");
        context.put("max_length", maxLength);
        context.put("actual_length", actual);
        errors.add(
                "too_long",
                loc,
                "List should have at most "
                        + maxLength
                        + " items after validation, not "
                        + actual,
                node,
                context);
    }

    /** A {@code ValueError} raised by a model validator. */
    static void valueError(CandidateErrors errors, List<Object> loc, String message, JsonNode input) {
        errors.add(
                "value_error",
                loc,
                "Value error, " + message,
                input,
                context("error", message));
    }
}
