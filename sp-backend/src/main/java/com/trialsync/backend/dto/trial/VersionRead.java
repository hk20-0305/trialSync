package com.trialsync.backend.dto.trial;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.trialsync.backend.entity.Criterion;
import com.trialsync.backend.entity.TrialVersion;
import com.trialsync.backend.entity.enums.VersionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Port of {@code trialsync.schemas.VersionRead}. */
@JsonPropertyOrder({
    "id",
    "trial_id",
    "version",
    "status",
    "source_text",
    "created_at",
    "updated_at",
    "criteria"
})
public record VersionRead(
        UUID id,
        @JsonProperty("trial_id") UUID trialId,
        int version,
        VersionStatus status,
        @JsonProperty("source_text") String sourceText,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        List<CriterionRead> criteria) {

    /** Reads the criteria off the entity's mapped collection. */
    public static VersionRead of(TrialVersion version) {
        return of(version, version.getCriteria());
    }

    /**
     * Builds the response from an explicit criterion list.
     *
     * <p>Needed straight after a version or criterion is created: the new rows are known to the
     * persistence context but the parent's mapped collection is a separate lazy association that
     * would either still be empty or trigger a reload, so the caller passes the rows it just wrote.
     */
    public static VersionRead of(TrialVersion version, List<Criterion> criteria) {
        return new VersionRead(
                version.getId(),
                version.getTrialId(),
                version.getVersion(),
                version.getStatus(),
                version.getSourceText(),
                version.getCreatedAt(),
                version.getUpdatedAt(),
                criteria.stream().map(CriterionRead::of).toList());
    }
}
