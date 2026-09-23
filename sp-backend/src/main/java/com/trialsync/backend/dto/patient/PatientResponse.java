package com.trialsync.backend.dto.patient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The full patient record: profile, active facts, review items and derived consistency issues.
 *
 * <p>Nulls are retained. Python applies {@code exclude_none} only to the error envelope, so
 * {@code date_of_birth} and {@code sex} appear as {@code null} rather than being dropped.
 */
@JsonPropertyOrder({
    "id",
    "external_id",
    "display_name",
    "date_of_birth",
    "sex",
    "created_at",
    "updated_at",
    "facts",
    "unsupported_details",
    "consistency_issues"
})
public record PatientResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("external_id") String externalId,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("date_of_birth") LocalDate dateOfBirth,
        @JsonProperty("sex") String sex,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        @JsonProperty("facts") List<FactResponse> facts,
        @JsonProperty("unsupported_details") List<UnsupportedDetailResponse> unsupportedDetails,
        @JsonProperty("consistency_issues") List<PatientConsistencyIssueResponse> consistencyIssues) {}
