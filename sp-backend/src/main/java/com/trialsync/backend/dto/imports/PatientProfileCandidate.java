package com.trialsync.backend.dto.imports;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Port of {@code trialsync.imports.schemas.PatientProfileCandidate}.
 *
 * <p>{@code sex} is the {@code BiologicalSex} enum in Python and is carried here as the validated
 * string {@code "male"} or {@code "female"}, matching {@code patients.sex}, which is a plain
 * {@code varchar} column rather than a database enum. The validator applies the same
 * {@code normalize_recognized_legacy_sex} rule - trim, lowercase, accept only those two words - and
 * rejects a date of birth in the future.
 */
public record PatientProfileCandidate(
        @JsonProperty("display_name") String displayName,
        @JsonProperty("date_of_birth") LocalDate dateOfBirth,
        @JsonProperty("sex") String sex) {}
