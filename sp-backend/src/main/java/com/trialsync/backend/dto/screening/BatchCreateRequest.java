package com.trialsync.backend.dto.screening;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port of {@code trialsync.schemas.BatchCreate}.
 *
 * <p>The bounds are the Pydantic ones: at most 500 patients or snapshots, between 1 and 100 trial
 * versions, and a label of 1 to 120 characters when present. They are deliberately larger than the
 * configured batch limits, which are checked in the service so the caller gets the dedicated
 * {@code BATCH_LIMIT_EXCEEDED} code rather than a generic validation envelope.
 *
 * <p>Absent lists become empty lists here, exactly as Pydantic's {@code default_factory=list} does,
 * so the exclusive-or check below sees the same values Python saw.
 */
public record BatchCreateRequest(
        @JsonProperty("patient_ids") @Size(max = 500) List<UUID> patientIds,
        @JsonProperty("patient_snapshot_ids") @Size(max = 500) List<UUID> patientSnapshotIds,
        @JsonProperty("trial_version_ids") @NotNull @Size(min = 1, max = 100)
                List<UUID> trialVersionIds,
        @JsonProperty("label") @Size(min = 1, max = 120) String label,
        @JsonProperty("screening_date") LocalDate screeningDate) {

    public BatchCreateRequest {
        patientIds = patientIds == null ? List.of() : List.copyOf(patientIds);
        patientSnapshotIds = patientSnapshotIds == null ? List.of() : List.copyOf(patientSnapshotIds);
    }

    /**
     * Port of the {@code require_one_patient_source} model validator: {@code bool(patient_ids) ==
     * bool(patient_snapshot_ids)} is rejected, so supplying both or neither fails the same way.
     */
    @AssertTrue(message = "Provide exactly one of patient_ids or patient_snapshot_ids.")
    public boolean isExactlyOnePatientSource() {
        return patientIds.isEmpty() != patientSnapshotIds.isEmpty();
    }
}
