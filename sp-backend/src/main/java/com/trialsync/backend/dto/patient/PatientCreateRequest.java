package com.trialsync.backend.dto.patient;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of {@code POST /api/v1/patients}.
 *
 * <p>{@code sex} is carried as a raw string rather than an enum so an unsupported value reaches the
 * service, which answers with {@code PATIENT_SEX_INVALID}. Binding it to an enum would either bind
 * silently to {@code null} or surface a Jackson parse failure, and neither reproduces Python's
 * validation-error routing faithfully.
 *
 * <p>{@code confirm_duplicate_name} is boxed so an omitted value is distinguishable from an
 * explicit {@code false}; both mean "not confirmed", matching the Pydantic default.
 */
public record PatientCreateRequest(
        @JsonProperty("external_id") String externalId,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("date_of_birth") LocalDate dateOfBirth,
        @JsonProperty("sex") String sex,
        @JsonProperty("confirm_duplicate_name") Boolean confirmDuplicateName) {}
