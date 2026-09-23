package com.trialsync.backend.dto.concept;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of {@code POST /api/v1/clinical-concepts}.
 *
 * <p>{@code fact_type} is a raw string because the catalog accepts only condition, medication and
 * observation — a narrower set than the {@code FactType} enum, which also carries
 * {@code demographic}. The service rejects anything outside the catalog's three.
 *
 * <p>The concept's key, group, input kind, allowed assertions, date requirement and display order
 * are all derived server-side from the label and fact type; callers cannot set them.
 */
/**
 * {@code extra="forbid"}: an unrecognised key is rejected rather than dropped.
 *
 * <p>Explicit, because unknown properties are tolerated globally to match the patient profile
 * models, which Python leaves permissive. A silently ignored key here would let a caller believe
 * they had set something they had not.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record ClinicalConceptCreateRequest(
        @JsonProperty("display_label") String displayLabel,
        @JsonProperty("fact_type") String factType,
        @JsonProperty("fixed_unit") String fixedUnit,
        @JsonProperty("screening_supported") Boolean screeningSupported,
        @JsonProperty("help_text") String helpText,
        @JsonProperty("terminology_system") String terminologySystem,
        @JsonProperty("terminology_code") String terminologyCode) {}
