package com.trialsync.backend.dto.patient;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body of {@code POST /api/v1/patients/{patient_id}/unsupported-details}. */
public record UnsupportedDetailCreateRequest(
        @JsonProperty("category") String category,
        @JsonProperty("label") String label,
        @JsonProperty("context") String context,
        @JsonProperty("source_label") String sourceLabel) {}
