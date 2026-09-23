package com.trialsync.backend.config;

import java.util.List;
import java.util.Map;

/**
 * Port of {@code trialsync.api.errors.ApplicationError}: a domain failure carrying the HTTP status,
 * a stable machine-readable code, and optional field/detail context for the error envelope.
 */
public class ApplicationError extends RuntimeException {

    private final String code;
    private final int statusCode;
    private final String field;
    private final List<Map<String, Object>> details;

    public ApplicationError(String code, String message, int statusCode) {
        this(code, message, statusCode, null, null);
    }

    public ApplicationError(String code, String message, int statusCode, String field) {
        this(code, message, statusCode, field, null);
    }

    public ApplicationError(
            String code,
            String message,
            int statusCode,
            String field,
            List<Map<String, Object>> details) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
        this.field = field;
        this.details = details;
    }

    public String getCode() { return code; }

    public int getStatusCode() { return statusCode; }

    public String getField() { return field; }

    public List<Map<String, Object>> getDetails() { return details; }

    public static ApplicationError notFound(String code, String message) {
        return new ApplicationError(code, message, 404);
    }

    public static ApplicationError conflict(String code, String message) {
        return new ApplicationError(code, message, 409);
    }

    public static ApplicationError unprocessable(String code, String message, String field) {
        return new ApplicationError(code, message, 422, field);
    }
}
