package com.trialsync.backend.dto.concept;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.trialsync.backend.domain.model.FactType;

/**
 * The administrative view of a catalog concept, including retired ones.
 *
 * <p>{@code allowed_assertions_json} keeps its storage-flavoured name because that is the wire name
 * Python exposes, and it serializes as a JSON array even though the column holds the document as
 * text.
 */
@JsonPropertyOrder({
    "id",
    "key",
    "fact_type",
    "concept",
    "display_label",
    "concept_group",
    "input_kind",
    "allowed_assertions_json",
    "fixed_unit",
    "effective_date_required",
    "screening_supported",
    "help_text",
    "terminology_system",
    "terminology_code",
    "display_order",
    "active",
    "created_at",
    "updated_at"
})
public record ClinicalConceptResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("key") String key,
        @JsonProperty("fact_type") FactType factType,
        @JsonProperty("concept") String concept,
        @JsonProperty("display_label") String displayLabel,
        @JsonProperty("concept_group") String conceptGroup,
        @JsonProperty("input_kind") String inputKind,
        @JsonProperty("allowed_assertions_json") List<String> allowedAssertionsJson,
        @JsonProperty("fixed_unit") String fixedUnit,
        @JsonProperty("effective_date_required") boolean effectiveDateRequired,
        @JsonProperty("screening_supported") boolean screeningSupported,
        @JsonProperty("help_text") String helpText,
        @JsonProperty("terminology_system") String terminologySystem,
        @JsonProperty("terminology_code") String terminologyCode,
        @JsonProperty("display_order") int displayOrder,
        @JsonProperty("active") boolean active,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt) {}
