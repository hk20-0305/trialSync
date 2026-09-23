package com.trialsync.backend.dto.screening;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.trialsync.backend.domain.model.CriterionKind;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port of {@code trialsync.schemas.CriterionEvaluationRead}.
 *
 * <p>The three evidence collections are handed back exactly as the engine emitted them and the
 * service stored them - parsed from the {@code evidence_json}, {@code rejected_evidence_json} and
 * {@code missing_information_json} columns without re-deriving anything. Nothing in this layer may
 * add, drop or rewrite an entry: a criterion's audit trail is only ever produced by the engine.
 */
public record CriterionEvaluationResponse(
        @JsonProperty("id") UUID id,
        @JsonProperty("criterion_id") UUID criterionId,
        @JsonProperty("criterion_order") int criterionOrder,
        @JsonProperty("criterion_kind") CriterionKind criterionKind,
        @JsonProperty("result") String result,
        @JsonProperty("truth") String truth,
        @JsonProperty("reason_code") String reasonCode,
        @JsonProperty("criterion_source_text") String criterionSourceText,
        @JsonProperty("canonical_explanation") String canonicalExplanation,
        @JsonProperty("evidence") List<Map<String, Object>> evidence,
        @JsonProperty("rejected_evidence") List<Map<String, Object>> rejectedEvidence,
        @JsonProperty("missing_information") List<Map<String, Object>> missingInformation) {}
