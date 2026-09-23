package com.trialsync.backend.dto.trial;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.domain.model.CriterionKind;
import com.trialsync.backend.entity.Criterion;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Port of {@code trialsync.schemas.CriterionRead}. The property order mirrors the Pydantic model,
 * where the inherited {@code CriterionCreate} fields are emitted before the read-only ones.
 *
 * <p>The entity holds {@code normalized_rule} as the raw text of a {@code json} column. It is parsed
 * into a tree here rather than being written through verbatim, because the column text keeps
 * whatever spacing and escaping the writer used - rows written by the Python service carry
 * {@code json.dumps} defaults - while the response has to be the compact re-serialisation FastAPI
 * produced. Parsing and letting Jackson write the tree normalises that, and an object node preserves
 * key order so the rule reads back exactly as it was stored.
 */
@JsonPropertyOrder({
    "kind",
    "order",
    "source_text",
    "normalized_rule",
    "required",
    "id",
    "trial_version_id",
    "created_at",
    "updated_at"
})
public record CriterionRead(
        CriterionKind kind,
        int order,
        @JsonProperty("source_text") String sourceText,
        @JsonProperty("normalized_rule") JsonNode normalizedRule,
        boolean required,
        UUID id,
        @JsonProperty("trial_version_id") UUID trialVersionId,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt) {

    private static final ObjectMapper RULE_READER = new ObjectMapper();

    public static CriterionRead of(Criterion criterion) {
        return new CriterionRead(
                criterion.getKind(),
                criterion.getOrder(),
                criterion.getSourceText(),
                parseRule(criterion.getNormalizedRule()),
                criterion.isRequired(),
                criterion.getId(),
                criterion.getTrialVersionId(),
                criterion.getCreatedAt(),
                criterion.getUpdatedAt());
    }

    private static JsonNode parseRule(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            return RULE_READER.readTree(stored);
        } catch (JsonProcessingException exception) {
            // The column is typed json, so PostgreSQL already rejected anything unparseable on the
            // way in; reaching here means the row was written outside the schema's guarantees.
            throw new IllegalStateException("Stored criterion rule is not valid JSON", exception);
        }
    }
}
