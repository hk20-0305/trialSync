package com.trialsync.backend.dto.imports;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trialsync.backend.domain.model.CriterionKind;

/**
 * Port of {@code trialsync.imports.schemas.TrialCriterionCandidate}.
 *
 * <p>{@code normalized_rule} is an open {@code dict[str, Any]} in Python and stays an untyped object
 * node here: the reviewer may hand-write a rule the deterministic parser could not produce, and the
 * screening engine is what finally decides whether the shape is one it can evaluate.
 *
 * <p>{@code parse_state} is a Pydantic {@code Literal["parsed", "needs_manual_rule"]}. It is carried
 * as a string with the same two admissible values, checked by the candidate validator; approval
 * additionally refuses any selected criterion that is not {@code parsed} with a rule attached.
 */
public record TrialCriterionCandidate(
        @JsonProperty("candidate_id") UUID candidateId,
        @JsonProperty("selected") boolean selected,
        @JsonProperty("kind") CriterionKind kind,
        @JsonProperty("order") int order,
        @JsonProperty("source_text") String sourceText,
        @JsonProperty("normalized_rule") ObjectNode normalizedRule,
        @JsonProperty("parse_state") String parseState,
        @JsonProperty("source") SourceReference source,
        @JsonProperty("warnings") List<String> warnings) {

    public static final String PARSED = "parsed";
    public static final String NEEDS_MANUAL_RULE = "needs_manual_rule";
}
