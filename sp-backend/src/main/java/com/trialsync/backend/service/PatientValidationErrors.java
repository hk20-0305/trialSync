package com.trialsync.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.trialsync.backend.config.ApplicationError;

/**
 * The errors FastAPI produced from request validation rather than from endpoint logic.
 *
 * <p>Python never wrote these by hand: Pydantic rejected the body and a single handler remapped the
 * raw error list onto a few stable codes, keyed on the failing field and the route. Because several
 * request fields here are deliberately bound loosely - {@code sex} as a string so an unsupported
 * value can be named, a fact's {@code value} as an unparsed tree so the union's per-shape rules can
 * be applied - the equivalent failures surface in the services, and this class raises the same
 * envelope the handler would have produced.
 *
 * <p>{@code details} keeps the {@code loc}/{@code msg}/{@code type} shape a client can read, with
 * the Java-side reason rather than Pydantic's wording.
 */
public final class PatientValidationErrors {

    private PatientValidationErrors() {}

    /**
     * A future date of birth. Checked before the biological sex, because Pydantic reports field
     * errors in declaration order and {@code date_of_birth} is declared first, so it is the one the
     * handler remaps when both are wrong.
     */
    public static ApplicationError dateOfBirthInFuture() {
        return new ApplicationError(
                "PATIENT_DOB_IN_FUTURE",
                "Date of birth cannot be in the future.",
                422,
                "date_of_birth",
                List.of(
                        item(
                                List.of("body", "date_of_birth"),
                                "Date of birth cannot be in the future.",
                                "patient_date_of_birth_in_future")));
    }

    /** A biological sex outside {@code male}, {@code female} and null. */
    public static ApplicationError sexInvalid() {
        return new ApplicationError(
                "PATIENT_SEX_INVALID",
                "Biological sex must be male, female, or null.",
                422,
                "sex",
                List.of(
                        item(
                                List.of("body", "sex"),
                                "Input should be 'male' or 'female'",
                                "enum")));
    }

    /**
     * Anything else wrong with a request body: a missing required field, a string outside its
     * length bounds, a value outside a closed set.
     */
    public static ApplicationError requestInvalid(String field, String message, String type) {
        return new ApplicationError(
                "REQUEST_VALIDATION_ERROR",
                "The request could not be validated.",
                422,
                null,
                List.of(item(bodyLocation(field), message, type)));
    }

    /**
     * The same envelope for a query parameter rather than a body field.
     *
     * <p>{@code loc} opens with {@code "query"}, which is how FastAPI distinguishes a bad parameter
     * from a bad body and how a client knows which part of the request to correct.
     */
    public static ApplicationError requestInvalidQuery(String parameter, String message, String type) {
        return new ApplicationError(
                "REQUEST_VALIDATION_ERROR",
                "The request could not be validated.",
                422,
                null,
                List.of(item(List.of("query", parameter), message, type)));
    }

    /**
     * A clinical detail's value that failed the union's rules.
     *
     * <p>Every validation failure on a fact route collapses to this one code, which is what the
     * Python handler did for any path containing {@code /facts}.
     */
    public static ApplicationError factValueInvalid(String field, String message, String type) {
        return new ApplicationError(
                "PATIENT_FACT_VALUE_INVALID",
                "The clinical detail value could not be validated.",
                422,
                "value",
                List.of(item(bodyLocation(field), message, type)));
    }

    /**
     * A removal request that cannot be honoured because it carries no usable reason.
     *
     * <p>A fact DELETE takes a body, and the handler mapped every validation failure on that route
     * to this code regardless of which field was at fault.
     */
    public static ApplicationError removalReasonRequired(String field, String message, String type) {
        return new ApplicationError(
                "PATIENT_FACT_REMOVAL_REASON_REQUIRED",
                "A reason is required before removing a clinical detail.",
                422,
                "reason",
                List.of(item(bodyLocation(field), message, type)));
    }

    /** "Field required", the message Pydantic uses for an absent field. */
    public static String fieldRequired() {
        return "Field required";
    }

    /** The message Pydantic uses when a string is shorter than its minimum length. */
    public static String tooShort(int minimum) {
        return "String should have at least " + minimum + " character" + (minimum == 1 ? "" : "s");
    }

    /** The message Pydantic uses when a string is longer than its maximum length. */
    public static String tooLong(int maximum) {
        return "String should have at most " + maximum + " character" + (maximum == 1 ? "" : "s");
    }

    private static List<String> bodyLocation(String field) {
        List<String> location = new ArrayList<>();
        location.add("body");
        if (field != null) {
            for (String part : field.split("\\.")) {
                location.add(part);
            }
        }
        return List.copyOf(location);
    }

    private static Map<String, Object> item(List<String> location, String message, String type) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("loc", location);
        entry.put("msg", message);
        entry.put("type", type);
        return entry;
    }
}
