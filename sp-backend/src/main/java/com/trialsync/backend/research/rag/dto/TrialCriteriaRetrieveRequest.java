package com.trialsync.backend.research.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for retrieving eligibility criteria scoped to a trial version.
 */
public record TrialCriteriaRetrieveRequest(
        @NotBlank(message = "Query text is required")
        @JsonProperty("query") String query,
        @JsonProperty("top_k") Integer topK
) {}
