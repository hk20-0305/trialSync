package com.trialsync.backend.dto.patient;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Body of {@code POST /api/v1/patients/{patient_id}/facts}.
 *
 * <p>{@code value} stays an unparsed tree so the service can apply the discriminated union's rules
 * itself: the three shapes declare {@code extra="forbid"} and disagree on which fields are
 * required, and Jackson's polymorphic support cannot reject unknown keys per-subtype while the
 * global {@code FAIL_ON_UNKNOWN_PROPERTIES} stays off for the patient models, which Python leaves
 * permissive.
 */
/**
 * {@code extra="forbid"}: an unrecognised key is rejected rather than dropped.
 *
 * <p>Explicit, because unknown properties are tolerated globally to match the patient profile
 * models, which Python leaves permissive. A silently ignored key here would let a caller believe
 * they had set something they had not.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record PatientFactCreateRequest(
        @JsonProperty("catalog_key") String catalogKey,
        @JsonProperty("value") JsonNode value,
        @JsonProperty("source_label") String sourceLabel,
        @JsonProperty("expected_patient_updated_at") OffsetDateTime expectedPatientUpdatedAt) {}
