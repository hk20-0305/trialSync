package com.trialsync.backend.dto.screening;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Port of {@code trialsync.schemas.ScreeningCreate}.
 *
 * <p>{@code screening_date} is optional; when it is absent the service resolves today's date once
 * and passes it into the engine, which never reads a clock of its own.
 */
public record ScreeningCreateRequest(
        @JsonProperty("patient_id") @NotNull UUID patientId,
        @JsonProperty("trial_version_id") @NotNull UUID trialVersionId,
        @JsonProperty("screening_date") LocalDate screeningDate) {}
