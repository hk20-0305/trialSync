package com.trialsync.backend.dto.screening;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port of {@code trialsync.schemas.PatientSnapshotSummary}.
 *
 * <p>Every member is read from the frozen snapshot row, not from the live patient: a screening that
 * ran last month keeps showing the display name and facts as they were when it ran.
 *
 * <p>{@code facts} is echoed verbatim from {@code facts_json}, so the fact objects carry the same
 * keys, the same ordering (sorted by fact id) and the same string-encoded numeric values that the
 * canonical hash was computed over.
 */
public record PatientSnapshotSummary(
        @JsonProperty("id") UUID id,
        @JsonProperty("external_id") String externalId,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("date_of_birth") LocalDate dateOfBirth,
        @JsonProperty("sex") String sex,
        @JsonProperty("facts") List<Map<String, Object>> facts) {

    public PatientSnapshotSummary {
        facts = facts == null ? List.of() : List.copyOf(facts);
    }
}
