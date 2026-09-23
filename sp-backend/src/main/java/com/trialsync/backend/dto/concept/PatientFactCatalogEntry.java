package com.trialsync.backend.dto.concept;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.trialsync.backend.domain.model.FactType;

/**
 * One backend-owned clinical-detail definition, as both forms and the fact endpoints see it.
 *
 * <p>This is the read contract, not the storage row: Python adapts a {@code ClinicalConcept} into a
 * {@code PatientFactCatalogEntry} and then uses that same object to validate incoming facts, so the
 * entry doubles as the catalog's validation view. The concept's terminology coding is deliberately
 * absent — it belongs to catalog administration, not to the entry contract.
 *
 * <p>{@code allowed_units} is always empty. The contract reserves it for concepts that will accept
 * several units, but every numeric concept today pins exactly one {@code fixed_unit}.
 */
@JsonPropertyOrder({
    "key",
    "fact_type",
    "concept",
    "display_label",
    "group",
    "input_kind",
    "allowed_assertions",
    "fixed_unit",
    "allowed_units",
    "effective_date_required",
    "screening_supported",
    "help_text",
    "display_order"
})
public record PatientFactCatalogEntry(
        @JsonProperty("key") String key,
        @JsonProperty("fact_type") FactType factType,
        @JsonProperty("concept") String concept,
        @JsonProperty("display_label") String displayLabel,
        @JsonProperty("group") String group,
        @JsonProperty("input_kind") String inputKind,
        @JsonProperty("allowed_assertions") List<String> allowedAssertions,
        @JsonProperty("fixed_unit") String fixedUnit,
        @JsonProperty("allowed_units") List<String> allowedUnits,
        @JsonProperty("effective_date_required") boolean effectiveDateRequired,
        @JsonProperty("screening_supported") boolean screeningSupported,
        @JsonProperty("help_text") String helpText,
        @JsonProperty("display_order") int displayOrder) {}
