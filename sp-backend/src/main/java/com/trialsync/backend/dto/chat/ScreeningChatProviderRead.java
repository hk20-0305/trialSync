package com.trialsync.backend.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Which explanation provider produced, or would produce, an answer:
 * {@code schemas.ScreeningChatProviderRead}.
 *
 * <p>Returned on the conversation so the client can tell the user whether the assistant is
 * available, and on each assistant message so an answer written by the deterministic explainer is
 * visibly distinguishable from one written by a model. {@code model} is null for the deterministic
 * and disabled providers.
 */
@JsonPropertyOrder({"enabled", "provider", "model", "prompt_version"})
public record ScreeningChatProviderRead(
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("provider") String provider,
        @JsonProperty("model") String model,
        @JsonProperty("prompt_version") String promptVersion) {}
