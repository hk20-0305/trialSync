package com.trialsync.backend.dto.patient;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of {@code DELETE /api/v1/patients/{patient_id}/facts/{fact_id}}.
 *
 * <p>Removal is a soft void and always demands a reason, so this DELETE carries a body. Any
 * validation failure on it — a missing body, a missing reason, a missing staleness guard — answers
 * with {@code PATIENT_FACT_REMOVAL_REASON_REQUIRED}, because Python's handler maps every request
 * validation error on a fact DELETE to that one code.
 */
/**
 * {@code extra="forbid"}: an unrecognised key is rejected rather than dropped.
 *
 * <p>Explicit, because unknown properties are tolerated globally to match the patient profile
 * models, which Python leaves permissive. A silently ignored key here would let a caller believe
 * they had set something they had not.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PatientFactVoidRequest(
        @JsonProperty("reason") String reason,
        @JsonProperty("expected_fact_updated_at") OffsetDateTime expectedFactUpdatedAt) {}
