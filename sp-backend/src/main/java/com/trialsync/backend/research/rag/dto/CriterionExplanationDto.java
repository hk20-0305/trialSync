package com.trialsync.backend.research.rag.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Explanation item for a single criterion, including citation and provenance validity.
 */
public record CriterionExplanationDto(
        @JsonProperty("criterion_id") UUID criterionId,
        @JsonProperty("kind") String kind,
        @JsonProperty("source_text") String sourceText,
        @JsonProperty("citation") String citation,
        @JsonProperty("explanation") String explanation,
        @JsonProperty("provenance_valid") boolean provenanceValid
) {}
