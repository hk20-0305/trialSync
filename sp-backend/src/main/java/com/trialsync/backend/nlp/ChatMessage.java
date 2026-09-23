package com.trialsync.backend.nlp;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One OpenAI-compatible chat turn: {@code {"role": ..., "content": ...}}.
 *
 * <p>The declaration order is the serialisation order, matching the Python dict literals that built
 * both the provider request and the {@code json.dumps(history)} block inside the prompt.
 */
public record ChatMessage(
        @JsonProperty("role") String role, @JsonProperty("content") String content) {}
