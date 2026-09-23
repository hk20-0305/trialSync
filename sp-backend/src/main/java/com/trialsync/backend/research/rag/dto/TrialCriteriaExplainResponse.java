package com.trialsync.backend.research.rag.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload containing structured eligibility criteria explanation from Gemini,
 * validated provenance, and explicit disclaimer that deterministic screening remains the sole authority.
 */
public record TrialCriteriaExplainResponse(
        @JsonProperty("run_id") UUID runId,
        @JsonProperty("trial_version_id") UUID trialVersionId,
        @JsonProperty("query") String query,
        @JsonProperty("model") String model,
        @JsonProperty("status") String status,
        @JsonProperty("insufficient_evidence") boolean insufficientEvidence,
        @JsonProperty("summary") String summary,
        @JsonProperty("explanations") List<CriterionExplanationDto> explanations,
        @JsonProperty("provenance_valid") boolean provenanceValid,
        @JsonProperty("disclaimer") String disclaimer
) {
    public static final String DEFAULT_DISCLAIMER =
            "The deterministic screening engine remains the ONLY authority for actual eligibility decisions. "
            + "This RAG explanation is generated for research and criteria comprehension only and cannot alter patient eligibility.";
}
