package com.trialsync.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes what the engine would have needed in order to reach a decision.
 *
 * <p>Port of {@code trialsync.domain.types.MissingRequirement}. The {@code fact} member is the rule
 * path (or a pseudo-path such as {@code expression.args}) rather than a fact identifier.
 */
public record MissingRequirement(
        @JsonProperty("fact") String fact,
        @JsonProperty("reason") ReasonCode reason,
        @JsonProperty("detail") String detail) {}
