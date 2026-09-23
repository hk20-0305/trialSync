package com.trialsync.backend.dto.screening;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Port of {@code trialsync.schemas.ScreeningRead}: one stored screening with its full audit trail. */
public record ScreeningResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("batch_id") UUID batchId,
        @JsonProperty("patient_snapshot_id") UUID patientSnapshotId,
        @JsonProperty("trial_version_id") UUID trialVersionId,
        @JsonProperty("patient_snapshot") PatientSnapshotSummary patientSnapshot,
        @JsonProperty("trial_version") TrialVersionSummary trialVersion,
        @JsonProperty("overall_state") String overallState,
        @JsonProperty("screening_date") LocalDate screeningDate,
        @JsonProperty("engine_version") String engineVersion,
        @JsonProperty("dsl_version") String dslVersion,
        @JsonProperty("terminology_version") String terminologyVersion,
        @JsonProperty("unit_version") String unitVersion,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("counts") ScreeningCountsResponse counts,
        @JsonProperty("evaluations") List<CriterionEvaluationResponse> evaluations) {}
