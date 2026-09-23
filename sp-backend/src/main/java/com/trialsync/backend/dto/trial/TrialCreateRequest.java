package com.trialsync.backend.dto.trial;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Port of {@code trialsync.schemas.TrialCreate}.
 *
 * <p>{@code registry_id} is optional: when it is absent the service mints a synthetic identifier,
 * which is why the bound is {@code 1..64} rather than {@code @NotNull} - an empty string is still
 * rejected, but a missing key is not.
 */
public record TrialCreateRequest(
        @JsonProperty("registry_id") @Size(min = 1, max = 64) String registryId,
        @NotNull @Size(min = 1, max = 240) String title,
        @NotNull @Size(min = 1, max = 160) String condition,
        @Size(max = 40) String phase) {}
