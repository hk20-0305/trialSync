package com.trialsync.backend.dto.screening;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Port of {@code trialsync.schemas.TrialVersionSummary}.
 *
 * <p>These labels are the ones copied onto the screening row at write time, which is why renaming a
 * trial afterwards does not change what a stored screening reports.
 */
public record TrialVersionSummary(
        @JsonProperty("registry_id") String registryId,
        @JsonProperty("title") String title,
        @JsonProperty("version") int version) {}
