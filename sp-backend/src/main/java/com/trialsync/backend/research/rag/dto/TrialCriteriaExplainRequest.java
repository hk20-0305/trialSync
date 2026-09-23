package com.trialsync.backend.research.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for generating structured criteria explanation via LangChain4j and Gemini.
 */
public record TrialCriteriaExplainRequest(
        @NotBlank(message = "Query text is required")
        @JsonProperty("query") String query,
        @JsonProperty("patient_context") String patientContext,
        @JsonProperty("top_k") Integer topK
) {}
