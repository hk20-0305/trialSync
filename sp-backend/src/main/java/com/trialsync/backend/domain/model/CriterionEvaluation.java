package com.trialsync.backend.domain.model;

import java.util.List;

/**
 * The full audit record for one criterion: what was decided, why, and on what evidence.
 *
 * <p>Port of {@code trialsync.domain.types.CriterionEvaluation}.
 */
public record CriterionEvaluation(
        String criterionId,
        CriterionKind criterionKind,
        int criterionOrder,
        String sourceText,
        boolean required,
        TruthValue truth,
        CriterionResult result,
        ReasonCode reasonCode,
        String explanation,
        List<EvidenceReference> evidence,
        List<EvidenceReference> rejectedEvidence,
        List<MissingRequirement> missing) {

    public CriterionEvaluation {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        rejectedEvidence = rejectedEvidence == null ? List.of() : List.copyOf(rejectedEvidence);
        missing = missing == null ? List.of() : List.copyOf(missing);
    }
}
