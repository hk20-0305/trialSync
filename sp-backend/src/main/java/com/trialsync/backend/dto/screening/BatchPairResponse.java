package com.trialsync.backend.dto.screening;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Port of {@code trialsync.schemas.BatchPairRead}: one patient/trial cell of a batch grid. */
public record BatchPairResponse(
        @JsonProperty("patient_snapshot_id") UUID patientSnapshotId,
        @JsonProperty("trial_version_id") UUID trialVersionId,
        @JsonProperty("patient_snapshot") PatientSnapshotSummary patientSnapshot,
        @JsonProperty("trial_version") TrialVersionSummary trialVersion,
        @JsonProperty("screening_id") UUID screeningId,
        @JsonProperty("overall_state") String overallState,
        @JsonProperty("counts") ScreeningCountsResponse counts) {}
