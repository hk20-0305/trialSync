package com.trialsync.backend.dto.imports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.domain.model.FactType;

/**
 * Port of {@code trialsync.imports.schemas.PatientFactCandidate}: one reviewable clinical detail.
 *
 * <p>{@code value_numeric} is a {@code Decimal} in Python, and Pydantic's JSON mode renders a
 * {@code Decimal} as a <em>string</em> rather than a number - {@code "8.2"}, not {@code 8.2}. The
 * {@link ToStringSerializer} keeps that, so the React reviewer sees the same type it does today and
 * a value such as {@code 8.20} does not lose its trailing zero on the way through.
 */
public record PatientFactCandidate(
        @JsonProperty("candidate_id") UUID candidateId,
        @JsonProperty("selected") boolean selected,
        @JsonProperty("fact_type") FactType factType,
        @JsonProperty("concept") String concept,
        @JsonProperty("value_numeric") @JsonSerialize(using = ToStringSerializer.class)
                BigDecimal valueNumeric,
        @JsonProperty("value_text") String valueText,
        @JsonProperty("unit") String unit,
        @JsonProperty("assertion") Assertion assertion,
        @JsonProperty("effective_date") LocalDate effectiveDate,
        @JsonProperty("source") SourceReference source,
        @JsonProperty("warnings") List<String> warnings) {

    /** Returns a copy carrying a different warning list, leaving every other field untouched. */
    public PatientFactCandidate withWarnings(List<String> replacement) {
        return new PatientFactCandidate(
                candidateId,
                selected,
                factType,
                concept,
                valueNumeric,
                valueText,
                unit,
                assertion,
                effectiveDate,
                source,
                replacement);
    }
}
