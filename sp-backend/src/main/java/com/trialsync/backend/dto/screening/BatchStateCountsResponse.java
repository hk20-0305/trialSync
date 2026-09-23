package com.trialsync.backend.dto.screening;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Port of {@code trialsync.schemas.BatchStateCounts}: how the batch's screenings split across the
 * three overall states. Every state is always present, including zeros.
 */
public record BatchStateCountsResponse(
        @JsonProperty("potentially_eligible") int potentiallyEligible,
        @JsonProperty("likely_ineligible") int likelyIneligible,
        @JsonProperty("needs_review") int needsReview) {}
