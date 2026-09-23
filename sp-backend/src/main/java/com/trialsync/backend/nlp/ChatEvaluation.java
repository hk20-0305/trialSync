package com.trialsync.backend.nlp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * One stored criterion evaluation, projected for the explanation layer.
 *
 * <p>Every field is read from {@code criterion_evaluations}; nothing is derived, recomputed or
 * inferred here. This record is the <em>only</em> view of a screening a provider ever receives, and
 * it is serialised into the prompt verbatim, so the field names are the Python dict keys.
 *
 * <p>{@code evidenceIds} is the flattened list of {@code fact_id} values already present in
 * {@code evidence_json}. {@link ScreeningChatPolicy#validateAnswer} checks a model's claimed
 * evidence against exactly this list.
 */
public record ChatEvaluation(
        @JsonProperty("criterion_id") String criterionId,
        @JsonProperty("evaluation_id") String evaluationId,
        @JsonProperty("criterion_order") int criterionOrder,
        @JsonProperty("criterion_kind") String criterionKind,
        @JsonProperty("source_text") String sourceText,
        @JsonProperty("result") String result,
        @JsonProperty("reason_code") String reasonCode,
        @JsonProperty("canonical_explanation") String canonicalExplanation,
        @JsonProperty("evidence_ids") List<String> evidenceIds,
        @JsonProperty("evidence") JsonNode evidence,
        @JsonProperty("missing_information") JsonNode missingInformation) {

    public ChatEvaluation {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
