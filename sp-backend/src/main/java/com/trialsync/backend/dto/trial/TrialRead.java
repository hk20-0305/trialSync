package com.trialsync.backend.dto.trial;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.trialsync.backend.entity.Trial;
import com.trialsync.backend.entity.TrialVersion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Port of {@code trialsync.schemas.TrialRead}. */
@JsonPropertyOrder({
    "id",
    "registry_id",
    "title",
    "condition",
    "phase",
    "created_at",
    "updated_at",
    "versions"
})
public record TrialRead(
        UUID id,
        @JsonProperty("registry_id") String registryId,
        String title,
        String condition,
        String phase,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        List<VersionRead> versions) {

    /** Reads the versions, and each version's criteria, off the entity's mapped collections. */
    public static TrialRead of(Trial trial) {
        return of(trial, trial.getVersions());
    }

    /**
     * Builds the response from an explicit version list, for the create endpoint where the trial has
     * no versions yet and the mapped collection would otherwise be loaded for nothing.
     */
    public static TrialRead of(Trial trial, List<TrialVersion> versions) {
        return new TrialRead(
                trial.getId(),
                trial.getRegistryId(),
                trial.getTitle(),
                trial.getCondition(),
                trial.getPhase(),
                trial.getCreatedAt(),
                trial.getUpdatedAt(),
                versions.stream().map(VersionRead::of).toList());
    }
}
