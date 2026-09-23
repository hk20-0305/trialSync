package com.trialsync.backend.dto.patient;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A review item for a clinical detail the controlled catalog does not cover.
 *
 * <p>Python's {@code UnsupportedDetailRead} inherits from {@code UnsupportedDetailCreate}, so the
 * four authored fields serialize before the four persistence fields.
 */
@JsonPropertyOrder({
    "category",
    "label",
    "context",
    "source_label",
    "id",
    "patient_id",
    "created_at",
    "updated_at"
})
public record UnsupportedDetailResponse(
        @JsonProperty("category") String category,
        @JsonProperty("label") String label,
        @JsonProperty("context") String context,
        @JsonProperty("source_label") String sourceLabel,
        @JsonProperty("id") UUID id,
        @JsonProperty("patient_id") UUID patientId,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt) {}
