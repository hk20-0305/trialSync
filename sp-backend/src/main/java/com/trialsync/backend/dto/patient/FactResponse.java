package com.trialsync.backend.dto.patient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.domain.model.FactType;

/**
 * One recorded clinical detail, serialized exactly like Python's {@code FactRead}.
 *
 * <p>{@code FactRead} inherits from {@code FactCreate}, so the eight authored fields serialize
 * before the six persistence fields. That order is pinned here rather than left to the record's
 * component order.
 *
 * <p>{@code value_numeric} is a JSON <em>string</em>, not a number: Pydantic serializes
 * {@code Decimal} as a string to avoid precision loss, and the column is {@code numeric(18, 6)}, so
 * a stored 7.4 is echoed as {@code "7.400000"}. The web client types this field as
 * {@code string | null} and parses it with {@code Number()}.
 */
@JsonPropertyOrder({
    "fact_type",
    "concept",
    "value_numeric",
    "value_text",
    "unit",
    "assertion",
    "effective_date",
    "source_label",
    "id",
    "patient_id",
    "created_at",
    "updated_at",
    "voided_at",
    "void_reason"
})
public record FactResponse(
        @JsonProperty("fact_type") FactType factType,
        @JsonProperty("concept") String concept,
        @JsonProperty("value_numeric") String valueNumeric,
        @JsonProperty("value_text") String valueText,
        @JsonProperty("unit") String unit,
        @JsonProperty("assertion") Assertion assertion,
        @JsonProperty("effective_date") LocalDate effectiveDate,
        @JsonProperty("source_label") String sourceLabel,
        @JsonProperty("id") UUID id,
        @JsonProperty("patient_id") UUID patientId,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        @JsonProperty("voided_at") OffsetDateTime voidedAt,
        @JsonProperty("void_reason") String voidReason) {}
