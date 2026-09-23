package com.trialsync.backend.nlp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A pointer from a sentence of explanation back to one stored criterion evaluation.
 *
 * <p>Port of {@code trialsync.nlp.chat.Citation}. A citation is a claim of provenance, not evidence
 * in itself: {@link ScreeningChatPolicy#validateAnswer} re-resolves every field against the stored
 * evaluations and drops anything that does not match, so a model cannot mint one.
 *
 * <p>The identifiers are plain strings here, matching the Python model. They become {@code UUID} at
 * the API boundary, where only citations that survived validation are ever serialised.
 */
public record Citation(
        @JsonProperty("criterion_id") String criterionId,
        @JsonProperty("evaluation_id") String evaluationId,
        @JsonProperty("evidence_ids") List<String> evidenceIds,
        @JsonProperty("label") String label) {

    public Citation {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }

    /** {@code citation.model_copy(update={"label": ...})}. */
    public Citation withLabel(String replacement) {
        return new Citation(criterionId, evaluationId, evidenceIds, replacement);
    }
}
