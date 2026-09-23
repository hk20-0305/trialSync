package com.trialsync.backend.research.rag.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload containing retrieved eligibility criteria.
 */
public record TrialCriteriaRetrieveResponse(
        @JsonProperty("trial_version_id") UUID trialVersionId,
        @JsonProperty("query") String query,
        @JsonProperty("results_count") int resultsCount,
        @JsonProperty("criteria") List<RetrievedCriterionDto> criteria
) {}
