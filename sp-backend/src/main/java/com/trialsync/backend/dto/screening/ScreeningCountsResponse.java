package com.trialsync.backend.dto.screening;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Port of {@code trialsync.schemas.ScreeningCounts}: how many criteria passed, failed, or could not
 * be decided. Counted from the stored evaluations, never recomputed by re-running the engine.
 */
public record ScreeningCountsResponse(
        @JsonProperty("pass_count") int passCount,
        @JsonProperty("fail_count") int failCount,
        @JsonProperty("unknown_count") int unknownCount) {}
