package com.trialsync.backend.dto.screening;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Port of {@code trialsync.schemas.ScreeningBatchRead}.
 *
 * <p>{@code pair_count} is the number of pairs the batch was created with, while {@code screenings}
 * is what was actually stored; {@code unknown_criterion_count} totals the unknown criteria across
 * every screening in the batch, which is the review queue the UI sorts by.
 */
public record ScreeningBatchResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("label") String label,
        @JsonProperty("pair_count") int pairCount,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("state_counts") BatchStateCountsResponse stateCounts,
        @JsonProperty("unknown_criterion_count") int unknownCriterionCount,
        @JsonProperty("screenings") List<BatchPairResponse> screenings) {}
