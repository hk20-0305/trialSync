package com.trialsync.backend.dto.imports;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Port of {@code trialsync.imports.schemas.TrialImportCandidates}. */
public record TrialImportCandidates(
        @JsonProperty("profile") TrialProfileCandidate profile,
        @JsonProperty("criteria") List<TrialCriterionCandidate> criteria) {}
