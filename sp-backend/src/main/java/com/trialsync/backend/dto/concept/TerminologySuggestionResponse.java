package com.trialsync.backend.dto.concept;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Response of {@code GET /api/v1/clinical-concepts/suggestions}.
 *
 * <p>An unreachable or unconfigured terminology server is not an error: the lookup answers 200 with
 * an empty {@code suggestions} list and an explanatory line in {@code unavailable_sources}, so the
 * admin can still author the concept by hand.
 */
@JsonPropertyOrder({"query", "suggestions", "unavailable_sources"})
public record TerminologySuggestionResponse(
        @JsonProperty("query") String query,
        @JsonProperty("suggestions") List<TerminologySuggestion> suggestions,
        @JsonProperty("unavailable_sources") List<String> unavailableSources) {}
