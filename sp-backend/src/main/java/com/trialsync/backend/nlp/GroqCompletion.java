package com.trialsync.backend.nlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A validated structured completion: the parsed JSON object the model returned plus the token
 * counts, when the provider reported them.
 *
 * <p>Port of {@code trialsync.nlp.groq.GroqCompletion}. The raw provider body is deliberately not
 * retained - the Python client logged no payloads and neither does this one.
 */
public record GroqCompletion(ObjectNode payload, Integer inputTokens, Integer outputTokens) {

    /** The model's structured answer. Always a JSON object; the client rejects anything else. */
    public JsonNode asJson() {
        return payload;
    }
}
