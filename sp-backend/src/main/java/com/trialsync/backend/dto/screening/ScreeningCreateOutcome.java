package com.trialsync.backend.dto.screening;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * What {@code POST /api/v1/screenings} did.
 *
 * <p>{@code created} separates the two answers the endpoint can give: {@code true} means the engine
 * ran and a new screening row was written (HTTP 201), {@code false} means this patient and trial
 * were already screened and the existing screening is returned untouched (HTTP 200). The id is the
 * only difference that matters to the client, which navigates straight to it either way.
 */
public record ScreeningCreateOutcome(
        @JsonProperty("screening_id") UUID screeningId, boolean created) {}
