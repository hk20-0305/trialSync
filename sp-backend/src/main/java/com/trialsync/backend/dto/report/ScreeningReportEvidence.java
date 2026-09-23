package com.trialsync.backend.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Port of {@code trialsync.reports.assembler.ScreeningReportEvidence}: one evidence record preserved
 * from an immutable criterion evaluation.
 *
 * <p>{@code value} is deliberately untyped. Python declares it {@code Any} and the PDF renderer
 * formats whatever it finds, so a structured value survives into the document instead of being
 * coerced to a string here.
 */
public record ScreeningReportEvidence(
        @JsonProperty("fact_id") String factId,
        @JsonProperty("source_label") String sourceLabel,
        @JsonProperty("value") Object value,
        @JsonProperty("unit") String unit,
        @JsonProperty("effective_date") String effectiveDate) {}
