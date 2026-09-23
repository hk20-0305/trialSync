package com.trialsync.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * A single piece of stored evidence that a criterion evaluation relied on.
 *
 * <p>Port of {@code trialsync.domain.types.EvidenceReference}. Evidence is only ever produced from
 * facts that are already persisted on the patient snapshot; nothing in the AI layer may fabricate
 * one.
 */
public record EvidenceReference(
        @JsonProperty("fact_id") String factId,
        @JsonProperty("source_label") String sourceLabel,
        @JsonProperty("value") String value,
        @JsonProperty("unit") String unit,
        @JsonProperty("effective_date") LocalDate effectiveDate) {

    public EvidenceReference(String factId, String sourceLabel, String value) {
        this(factId, sourceLabel, value, null, null);
    }
}
