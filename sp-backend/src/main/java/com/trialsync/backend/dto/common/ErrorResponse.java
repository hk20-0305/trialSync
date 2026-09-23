package com.trialsync.backend.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * The unified error envelope: {@code {"error": {code, message, trace_id, field?, details?}}}.
 *
 * <p>The Python handler serialised with {@code exclude_none}, so {@code field} and {@code details}
 * disappear entirely when unset rather than appearing as {@code null}. That is why this type opts
 * out of the application-wide "always include" policy.
 */
public class ErrorResponse {

    private final ErrorDetail error;

    public ErrorResponse(ErrorDetail error) {
        this.error = error;
    }

    public ErrorDetail getError() {
        return error;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {

        private final String code;
        private final String message;
        private final String traceId;
        private final String field;
        private final List<Map<String, Object>> details;

        public ErrorDetail(
                String code,
                String message,
                String traceId,
                String field,
                List<Map<String, Object>> details) {
            this.code = code;
            this.message = message;
            this.traceId = traceId;
            this.field = field;
            this.details = details;
        }

        public String getCode() { return code; }

        public String getMessage() { return message; }

        @com.fasterxml.jackson.annotation.JsonProperty("trace_id")
        public String getTraceId() { return traceId; }

        public String getField() { return field; }

        public List<Map<String, Object>> getDetails() { return details; }
    }
}
