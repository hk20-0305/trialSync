package com.trialsync.backend.dto.concept;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** One candidate coding returned by an external terminology server. */
@JsonPropertyOrder({"source", "code", "display_label", "detail", "fixed_unit", "score"})
public record TerminologySuggestion(
        @JsonProperty("source") String source,
        @JsonProperty("code") String code,
        @JsonProperty("display_label") String displayLabel,
        @JsonProperty("detail") String detail,
        @JsonProperty("fixed_unit") String fixedUnit,
        @JsonProperty("score") Double score) {}
