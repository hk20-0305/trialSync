package com.trialsync.backend.dto.patient;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A non-blocking inconsistency the UI surfaces on the patient record.
 *
 * <p>Derived on read from the patient's active facts; never persisted.
 */
@JsonPropertyOrder({"code", "severity", "message", "field", "fact_id"})
public record PatientConsistencyIssueResponse(
        @JsonProperty("code") String code,
        @JsonProperty("severity") String severity,
        @JsonProperty("message") String message,
        @JsonProperty("field") String field,
        @JsonProperty("fact_id") UUID factId) {}
