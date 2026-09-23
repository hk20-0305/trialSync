package com.trialsync.backend.research.rag.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single retrieved eligibility criterion from LangChain4j vector search.
 */
public record RetrievedCriterionDto(
        @JsonProperty("criterion_id") UUID criterionId,
        @JsonProperty("kind") String kind,
        @JsonProperty("order") int order,
        @JsonProperty("source_text") String sourceText,
        @JsonProperty("provenance") String provenance,
        @JsonProperty("similarity_score") double similarityScore,
        @JsonProperty("structured_rule_summary") String structuredRuleSummary
) {}
