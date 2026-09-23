package com.trialsync.backend.dto.imports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trialsync.backend.config.ApplicationError;

/**
 * Port of {@code trialsync.imports.schemas.ImportUpdateRequest}.
 *
 * <p>The body is an open {@code dict[str, Any]} on the Python side and stays an untyped tree here.
 * Only the outer shape is checked at the boundary, exactly as Pydantic did; the contents are
 * validated by the service once the document's kind is known, and a failure there is reported as
 * {@code IMPORT_REVIEW_INVALID} rather than {@code REQUEST_VALIDATION_ERROR}.
 */
public record ImportUpdateRequest(@JsonProperty("candidates") JsonNode candidates) {

    /**
     * Requires {@code candidates} to be present and to be a JSON object.
     *
     * @throws ApplicationError 422 {@code REQUEST_VALIDATION_ERROR}
     */
    public ObjectNode validated() {
        if (candidates == null || candidates.isNull()) {
            throw invalid("missing", "Field required");
        }
        if (!candidates.isObject()) {
            throw invalid("dict_type", "Input should be a valid dictionary");
        }
        return (ObjectNode) candidates;
    }

    private static ApplicationError invalid(String type, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", type);
        error.put("loc", List.of("body", "candidates"));
        error.put("msg", message);
        return new ApplicationError(
                "REQUEST_VALIDATION_ERROR",
                "The request could not be validated.",
                422,
                null,
                List.of(error));
    }
}
