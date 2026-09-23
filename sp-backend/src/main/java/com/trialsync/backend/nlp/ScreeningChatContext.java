package com.trialsync.backend.nlp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The immutable, already-decided screening a provider is allowed to talk about.
 *
 * <p>Port of {@code trialsync.nlp.chat.ScreeningChatContext}, and of {@code _context_payload}: the
 * record serialises to exactly the object the Python code embedded in the prompt, in the same field
 * order.
 *
 * <p>This is a read-only projection. The chat layer never writes back to a screening, and the
 * counts and {@code overallState} carried here were produced by the deterministic engine before any
 * model was consulted.
 */
public record ScreeningChatContext(
        @JsonProperty("screening_id") String screeningId,
        @JsonProperty("overall_state") String overallState,
        @JsonProperty("counts") Map<String, Integer> counts,
        @JsonProperty("evaluations") List<ChatEvaluation> evaluations,
        @JsonProperty("versions") Map<String, String> versions) {

    public ScreeningChatContext {
        counts = counts == null ? Map.of() : new LinkedHashMap<>(counts);
        evaluations = evaluations == null ? List.of() : List.copyOf(evaluations);
        versions = versions == null ? Map.of() : new LinkedHashMap<>(versions);
    }

    /** {@code context.counts[name]}, treating an absent key as zero. */
    public int count(String name) {
        Integer value = counts.get(name);
        return value == null ? 0 : value;
    }
}
