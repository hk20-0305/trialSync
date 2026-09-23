package com.trialsync.backend.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The outcome of screening one patient snapshot against one approved trial version.
 *
 * <p>Port of {@code trialsync.domain.types.ScreeningResult}. {@code counts} is keyed by every
 * {@link CriterionResult} constant, including those with a count of zero, because Python builds it
 * by iterating the enum.
 */
public record ScreeningResult(
        String patientSnapshotId,
        String patientSnapshotVersion,
        String trialVersionId,
        String trialVersion,
        LocalDate screeningDate,
        OverallState overallState,
        List<CriterionEvaluation> evaluations,
        String engineVersion,
        String dslVersion,
        String terminologyVersion,
        String unitVersion,
        Map<CriterionResult, Integer> counts) {

    public ScreeningResult {
        evaluations = evaluations == null ? List.of() : List.copyOf(evaluations);
        counts = counts == null ? Map.of() : Map.copyOf(counts);
    }

    /** Convenience accessor used by the report and API layers; absent keys read as zero. */
    public int countOf(CriterionResult result) {
        return counts.getOrDefault(result, 0);
    }
}
