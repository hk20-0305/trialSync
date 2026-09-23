package com.trialsync.backend.dto.imports;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Port of {@code trialsync.imports.schemas.TrialProfileCandidate}. */
public record TrialProfileCandidate(
        @JsonProperty("title") String title,
        @JsonProperty("condition") String condition,
        @JsonProperty("phase") String phase) {}
