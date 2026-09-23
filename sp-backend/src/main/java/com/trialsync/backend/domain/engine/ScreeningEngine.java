package com.trialsync.backend.domain.engine;

import com.trialsync.backend.domain.model.ApprovedTrialVersion;
import com.trialsync.backend.domain.model.Criterion;
import com.trialsync.backend.domain.model.CriterionEvaluation;
import com.trialsync.backend.domain.model.CriterionKind;
import com.trialsync.backend.domain.model.CriterionResult;
import com.trialsync.backend.domain.model.OverallState;
import com.trialsync.backend.domain.model.PatientSnapshot;
import com.trialsync.backend.domain.model.ReasonCode;
import com.trialsync.backend.domain.model.ScreeningContext;
import com.trialsync.backend.domain.model.ScreeningResult;
import com.trialsync.backend.domain.model.TruthValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The deterministic screening engine — the only component permitted to decide eligibility.
 *
 * <p>Port of {@code trialsync.domain.engine.screen}. It touches no database, no HTTP client, no
 * language model, no clock and no random source, so screening the same snapshot against the same
 * approved trial version always yields the same answer. Nothing in the NLP, terminology, chat or
 * report layers may alter a result produced here; those layers may only describe it.
 */
public final class ScreeningEngine {

    /** The only rule DSL this engine understands. Anything else fails closed as unknown. */
    public static final String SUPPORTED_DSL_VERSION = "1.0";

    private ScreeningEngine() {}

    /** Evaluates every criterion of an approved trial version against one patient snapshot. */
    public static ScreeningResult screen(
            PatientSnapshot patient, ApprovedTrialVersion trial, ScreeningContext context) {

        List<Criterion> ordered = new ArrayList<>(trial.criteria());
        ordered.sort(Comparator.comparingInt(Criterion::order).thenComparing(Criterion::id));

        List<CriterionEvaluation> evaluations = new ArrayList<>(ordered.size());
        for (Criterion criterion : ordered) {
            evaluations.add(evaluateCriterion(criterion, patient, trial, context));
        }

        return new ScreeningResult(
                patient.id(),
                patient.version(),
                trial.id(),
                trial.version(),
                context.screeningDate(),
                overallState(evaluations),
                evaluations,
                context.engineVersion(),
                trial.dslVersion(),
                context.terminologyVersion(),
                context.unitVersion(),
                counts(evaluations));
    }

    /**
     * Port of the overall-state precedence in {@code screen}. Only required criteria influence the
     * verdict: one required failure is decisive, unanimous required passes are decisive, and
     * anything else is deferred to a human.
     */
    private static OverallState overallState(List<CriterionEvaluation> evaluations) {
        List<CriterionEvaluation> required =
                evaluations.stream().filter(CriterionEvaluation::required).toList();
        if (required.stream().anyMatch(item -> item.result() == CriterionResult.FAIL)) {
            return OverallState.LIKELY_INELIGIBLE;
        }
        if (!required.isEmpty()
                && required.stream().allMatch(item -> item.result() == CriterionResult.PASS)) {
            return OverallState.POTENTIALLY_ELIGIBLE;
        }
        return OverallState.NEEDS_REVIEW;
    }

    /** Counts every result constant, including zeros, in enum declaration order as Python does. */
    private static Map<CriterionResult, Integer> counts(List<CriterionEvaluation> evaluations) {
        Map<CriterionResult, Integer> counts = new LinkedHashMap<>();
        for (CriterionResult result : CriterionResult.values()) {
            int total = 0;
            for (CriterionEvaluation evaluation : evaluations) {
                if (evaluation.result() == result) {
                    total++;
                }
            }
            counts.put(result, total);
        }
        return counts;
    }

    /** Port of {@code _evaluate_criterion}. An unsupported DSL version short-circuits to unknown. */
    private static CriterionEvaluation evaluateCriterion(
            Criterion criterion,
            PatientSnapshot patient,
            ApprovedTrialVersion trial,
            ScreeningContext context) {

        Outcome outcome = SUPPORTED_DSL_VERSION.equals(trial.dslVersion())
                ? RuleEvaluator.evaluate(criterion.expression(), patient, context)
                : Outcome.invalid(
                        "dsl_version",
                        "DSL version " + trial.dslVersion() + " is unsupported.",
                        ReasonCode.UNSUPPORTED_RULE);

        CriterionResult result = criterionResult(criterion.kind(), outcome.truth());
        return new CriterionEvaluation(
                criterion.id(),
                criterion.kind(),
                criterion.order(),
                criterion.sourceText(),
                criterion.required(),
                outcome.truth(),
                result,
                outcome.reason(),
                explanation(criterion, result, outcome),
                outcome.evidence(),
                outcome.rejected(),
                outcome.missing());
    }

    /**
     * Port of {@code _criterion_result}: exclusion criteria invert the truth value, because a
     * satisfied exclusion is a failure to qualify.
     */
    private static CriterionResult criterionResult(CriterionKind kind, TruthValue truth) {
        if (truth == TruthValue.UNKNOWN) {
            return CriterionResult.UNKNOWN;
        }
        if (kind == CriterionKind.INCLUSION) {
            return truth == TruthValue.TRUE ? CriterionResult.PASS : CriterionResult.FAIL;
        }
        return truth == TruthValue.TRUE ? CriterionResult.FAIL : CriterionResult.PASS;
    }

    /**
     * Port of {@code _explanation}. The quotation marks are the typographic characters U+201C and
     * U+201D, and the verb is the result value plus "ed" — both are asserted by the Python tests and
     * are rendered verbatim into the PDF report, so they are contract, not cosmetics.
     */
    private static String explanation(Criterion criterion, CriterionResult result, Outcome outcome) {
        if (result == CriterionResult.UNKNOWN) {
            String needed = String.join(
                    "; ", outcome.missing().stream().map(item -> item.detail()).toList());
            String detail = needed.isEmpty() ? "Manual review is required." : needed;
            return "“" + criterion.sourceText() + "” is unknown. " + detail;
        }
        String basis = outcome.evidence().isEmpty() ? "" : " using the recorded evidence";
        return "“" + criterion.sourceText() + "” " + result.value() + "ed" + basis + ".";
    }
}
