package com.trialsync.backend.dto.chat;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A citation attached to an assistant answer: {@code schemas.ScreeningChatCitationRead}.
 *
 * <p>Every citation points at a stored criterion evaluation and, optionally, at specific evidence
 * rows within it. The identifiers are typed as UUIDs precisely so an unresolvable reference cannot
 * be serialised, and the {@code label} the client displays is re-read from the stored criterion
 * source text on the way out rather than taken from whatever produced the answer.
 */
@JsonPropertyOrder({"criterion_id", "evaluation_id", "evidence_ids", "label"})
public record ScreeningChatCitationRead(
        @JsonProperty("criterion_id") UUID criterionId,
        @JsonProperty("evaluation_id") UUID evaluationId,
        @JsonProperty("evidence_ids") List<String> evidenceIds,
        @JsonProperty("label") String label) {

    public ScreeningChatCitationRead {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
