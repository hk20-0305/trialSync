package com.trialsync.backend.dto.patient;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Body of {@code PATCH /api/v1/patients/{patient_id}/facts/{fact_id}}.
 *
 * <p>The whole value is replaced rather than merged, so there is no supplied-field tracking here;
 * the staleness guard is the fact's own {@code updated_at}, not the patient's.
 */
/**
 * {@code extra="forbid"}: an unrecognised key is rejected rather than dropped.
 *
 * <p>Explicit, because unknown properties are tolerated globally to match the patient profile
 * models, which Python leaves permissive. A silently ignored key here would let a caller believe
 * they had set something they had not.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PatientFactUpdateRequest(
        @JsonProperty("value") JsonNode value,
        @JsonProperty("source_label") String sourceLabel,
        @JsonProperty("expected_fact_updated_at") OffsetDateTime expectedFactUpdatedAt) {}
