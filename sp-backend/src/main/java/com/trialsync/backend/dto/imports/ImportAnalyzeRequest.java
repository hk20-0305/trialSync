package com.trialsync.backend.dto.imports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.entity.enums.DocumentKind;
import com.trialsync.backend.entity.enums.DocumentSourceType;

/**
 * Port of {@code trialsync.imports.schemas.ImportAnalyzeRequest}.
 *
 * <p>The bounds and the cross-field rule are checked in {@link #validated()} rather than through
 * bean-validation annotations. Pydantic reported every problem with the same envelope - a 422
 * carrying {@code REQUEST_VALIDATION_ERROR} and a list of error objects - and doing the checks in
 * one place keeps that envelope identical whether the failure is a length bound, a missing field or
 * the model-level rule.
 *
 * <p>The one deliberate difference from Pydantic is that the offending {@code input} value is not
 * echoed back in the detail objects. Pydantic includes it, which for this endpoint would mirror an
 * entire clinical document into an error response.
 */
public record ImportAnalyzeRequest(
        @JsonProperty("kind") DocumentKind kind,
        @JsonProperty("source_type") DocumentSourceType sourceType,
        @JsonProperty("text") String text,
        @JsonProperty("content_base64") String contentBase64,
        @JsonProperty("filename") String filename,
        @JsonProperty("mime_type") String mimeType) {

    private static final int MAX_TEXT_LENGTH = 1_000_000;
    private static final int MAX_CONTENT_LENGTH = 8_000_000;
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final int MAX_MIME_TYPE_LENGTH = 100;

    /**
     * Applies every constraint the Pydantic model declared, in field order, then the
     * {@code validate_source} model validator.
     *
     * @return this request, so callers can chain the check
     * @throws ApplicationError 422 {@code REQUEST_VALIDATION_ERROR} listing each violation
     */
    public ImportAnalyzeRequest validated() {
        List<Map<String, Object>> errors = new ArrayList<>();
        if (kind == null) {
            errors.add(missing("kind"));
        }
        if (sourceType == null) {
            errors.add(missing("source_type"));
        }
        tooLong(errors, "text", text, MAX_TEXT_LENGTH);
        tooLong(errors, "content_base64", contentBase64, MAX_CONTENT_LENGTH);
        tooLong(errors, "filename", filename, MAX_FILENAME_LENGTH);
        tooLong(errors, "mime_type", mimeType, MAX_MIME_TYPE_LENGTH);

        if (errors.isEmpty()) {
            // The model validator only runs once the fields themselves are valid, as in Pydantic.
            if (sourceType == DocumentSourceType.text && text == null) {
                errors.add(modelError("Pasted text is required for a text import."));
            }
            if (sourceType == DocumentSourceType.pdf && contentBase64 == null) {
                errors.add(modelError("PDF content is required for a PDF import."));
            }
        }
        if (!errors.isEmpty()) {
            throw new ApplicationError(
                    "REQUEST_VALIDATION_ERROR",
                    "The request could not be validated.",
                    422,
                    null,
                    errors);
        }
        return this;
    }

    private static void tooLong(
            List<Map<String, Object>> errors, String field, String value, int maxLength) {
        if (value == null || value.codePointCount(0, value.length()) <= maxLength) {
            return;
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "string_too_long");
        error.put("loc", List.of("body", field));
        error.put("msg", "String should have at most " + maxLength + " characters");
        error.put("ctx", Map.of("max_length", maxLength));
        errors.add(error);
    }

    private static Map<String, Object> missing(String field) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "missing");
        error.put("loc", List.of("body", field));
        error.put("msg", "Field required");
        return error;
    }

    private static Map<String, Object> modelError(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "value_error");
        error.put("loc", List.of("body"));
        error.put("msg", "Value error, " + message);
        return error;
    }
}
