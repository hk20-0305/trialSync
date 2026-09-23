package com.trialsync.backend.domain.engine;

import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.domain.model.EvidenceReference;
import com.trialsync.backend.domain.model.Fact;
import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.domain.model.MissingRequirement;
import com.trialsync.backend.domain.model.PatientSnapshot;
import com.trialsync.backend.domain.model.ReasonCode;
import com.trialsync.backend.domain.model.ScreeningContext;
import com.trialsync.backend.domain.model.Temporality;
import com.trialsync.backend.domain.model.TruthValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates a single normalized rule expression against a patient snapshot.
 *
 * <p>Line-by-line port of the private functions in {@code trialsync.domain.engine}. Everything here
 * is pure: no database, no clock, no network, no randomness. The screening date always arrives via
 * {@link ScreeningContext}, which is what makes a re-run of a stored screening reproduce the stored
 * answer.
 */
final class RuleEvaluator {

    /** The complete DSL v1.0 operator set. Anything outside it is {@code UNSUPPORTED_RULE}. */
    static final Set<String> SUPPORTED_OPERATORS = Set.of(
            "and", "or", "not",
            "present", "absent",
            "eq", "lt", "lte", "gt", "gte", "between",
            "concept_is", "concept_in",
            "current", "within_before");

    private static final Set<String> LOGICAL_OPERATORS = Set.of("and", "or");
    private static final Set<String> TEMPORAL_OPERATORS = Set.of("current", "within_before");
    private static final Set<String> PRESENCE_OPERATORS = Set.of("present", "absent");
    private static final Set<String> NUMERIC_OPERATORS = Set.of("eq", "lt", "lte", "gt", "gte", "between");

    private static final String AGE_PATH = "demographic.age";

    private RuleEvaluator() {}

    static Outcome evaluate(Map<String, Object> expression, PatientSnapshot patient, ScreeningContext context) {
        return evaluate(expression, patient, context, EvaluationConstraints.DEFAULT);
    }

    static Outcome evaluate(
            Map<String, Object> expression,
            PatientSnapshot patient,
            ScreeningContext context,
            EvaluationConstraints constraints) {

        if (!(expression.get("op") instanceof String op) || !SUPPORTED_OPERATORS.contains(op)) {
            return Outcome.invalid("expression", "The rule operator is unsupported.", ReasonCode.UNSUPPORTED_RULE);
        }
        if (LOGICAL_OPERATORS.contains(op)) {
            return evaluateLogical(op, expression, patient, context, constraints);
        }
        if ("not".equals(op)) {
            return evaluateNot(expression, patient, context, constraints);
        }
        if (TEMPORAL_OPERATORS.contains(op)) {
            return evaluateTemporal(op, expression, patient, context, constraints);
        }
        if (PRESENCE_OPERATORS.contains(op)) {
            return evaluatePresenceOperator(op, expression, patient, context, constraints);
        }
        if (NUMERIC_OPERATORS.contains(op)) {
            return numeric(op, expression, patient, context, constraints);
        }
        if ("concept_is".equals(op)) {
            return evaluateConceptIs(expression, patient, context, constraints);
        }
        if ("concept_in".equals(op)) {
            return evaluateConceptIn(expression, patient, context, constraints);
        }
        return Outcome.invalid("expression", "The rule operator is unsupported.", ReasonCode.UNSUPPORTED_RULE);
    }

    // ---------------------------------------------------------------- operators

    private static Outcome evaluateLogical(
            String op,
            Map<String, Object> expression,
            PatientSnapshot patient,
            ScreeningContext context,
            EvaluationConstraints constraints) {

        if (!(expression.get("args") instanceof List<?> args) || args.isEmpty()) {
            return Outcome.invalid("expression.args", "Logical rules require arguments.", ReasonCode.INVALID_RULE);
        }
        if (!args.stream().allMatch(arg -> arg instanceof Map)) {
            return Outcome.invalid(
                    "expression.args", "Every logical argument must be a rule.", ReasonCode.INVALID_RULE);
        }
        List<Outcome> outcomes = new ArrayList<>(args.size());
        for (Object arg : args) {
            outcomes.add(evaluate(asExpression(arg), patient, context, constraints));
        }
        List<TruthValue> truths = outcomes.stream().map(Outcome::truth).toList();
        TruthValue truth = "and".equals(op) ? ThreeValuedLogic.and(truths) : ThreeValuedLogic.or(truths);
        return merge(outcomes, truth);
    }

    private static Outcome evaluateNot(
            Map<String, Object> expression,
            PatientSnapshot patient,
            ScreeningContext context,
            EvaluationConstraints constraints) {

        if (!(expression.get("arg") instanceof Map<?, ?> arg)) {
            return Outcome.invalid("expression.arg", "NOT requires one rule.", ReasonCode.INVALID_RULE);
        }
        Outcome outcome = evaluate(asExpression(arg), patient, context, constraints);
        TruthValue truth = ThreeValuedLogic.not(outcome.truth());
        return new Outcome(
                truth,
                resolvedReason(truth, outcome.reason()),
                outcome.evidence(),
                outcome.rejected(),
                outcome.missing());
    }

    private static Outcome evaluateTemporal(
            String op,
            Map<String, Object> expression,
            PatientSnapshot patient,
            ScreeningContext context,
            EvaluationConstraints constraints) {

        if (!(expression.get("arg") instanceof Map<?, ?> arg)) {
            return Outcome.invalid(
                    "expression.arg", op + " requires one nested rule.", ReasonCode.INVALID_RULE);
        }
        EvaluationConstraints nested;
        if ("current".equals(op)) {
            nested = new EvaluationConstraints(true, constraints.withinDays());
        } else {
            Integer days = RuleValues.asPythonInt(expression.get("days"));
            if (days == null || days < 0) {
                return Outcome.invalid(
                        "expression.days", "A non-negative day window is required.", ReasonCode.INVALID_RULE);
            }
            nested = new EvaluationConstraints(constraints.currentOnly(), days);
        }
        return evaluate(asExpression(arg), patient, context, nested);
    }

    private static Outcome evaluatePresenceOperator(
            String op,
            Map<String, Object> expression,
            PatientSnapshot patient,
            ScreeningContext context,
            EvaluationConstraints constraints) {

        if (!(expression.get("fact") instanceof String path)) {
            return Outcome.invalid("expression.fact", "A fact path is required.", ReasonCode.INVALID_RULE);
        }
        Outcome outcome = presence(patient, path, context, constraints);
        if (!"absent".equals(op)) {
            return outcome;
        }
        TruthValue truth = ThreeValuedLogic.not(outcome.truth());
        return new Outcome(
                truth,
                resolvedReason(truth, outcome.reason()),
                outcome.evidence(),
                outcome.rejected(),
                outcome.missing());
    }

    private static Outcome evaluateConceptIs(
            Map<String, Object> expression,
            PatientSnapshot patient,
            ScreeningContext context,
            EvaluationConstraints constraints) {

        if (!(expression.get("fact_type") instanceof String factType)
                || !(expression.get("concept") instanceof String concept)) {
            return Outcome.invalid(
                    "expression", "Concept rules require fact_type and concept.", ReasonCode.INVALID_RULE);
        }
        return presence(patient, factType + "." + concept, context, constraints);
    }

    private static Outcome evaluateConceptIn(
            Map<String, Object> expression,
            PatientSnapshot patient,
            ScreeningContext context,
            EvaluationConstraints constraints) {

        if (!(expression.get("fact_type") instanceof String factType)
                || !(expression.get("concepts") instanceof List<?> concepts)
                || concepts.isEmpty()) {
            return Outcome.invalid(
                    "expression", "Concept-in requires a fact type and concepts.", ReasonCode.INVALID_RULE);
        }
        if (!concepts.stream().allMatch(concept -> concept instanceof String)) {
            return Outcome.invalid(
                    "expression.concepts", "Every concept must be text.", ReasonCode.INVALID_RULE);
        }
        List<Outcome> outcomes = new ArrayList<>(concepts.size());
        for (Object concept : concepts) {
            outcomes.add(presence(patient, factType + "." + concept, context, constraints));
        }
        return merge(outcomes, ThreeValuedLogic.or(outcomes.stream().map(Outcome::truth).toList()));
    }

    // ---------------------------------------------------------------- presence

    /** Port of {@code _presence}. */
    private static Outcome presence(
            PatientSnapshot patient, String path, ScreeningContext context, EvaluationConstraints constraints) {

        List<Fact> facts = matchingFacts(patient, path);
        if (facts.isEmpty()) {
            return Outcome.invalid(
                    path, "Explicit present or absent evidence is required.", ReasonCode.MISSING_FACT);
        }
        ConstraintOutcome narrowed = applyConstraints(facts, path, context, constraints);
        if (narrowed.error() != null) {
            return narrowed.error();
        }
        List<EvidenceReference> constraintRejected = narrowed.rejected();
        List<Fact> accepted = narrowed.accepted();

        List<Fact> present = withAssertion(accepted, Assertion.PRESENT);
        List<Fact> absent = withAssertion(accepted, Assertion.ABSENT);
        List<Fact> unresolved = withAssertion(accepted, Assertion.UNKNOWN);

        if (!present.isEmpty() && !absent.isEmpty()) {
            return new Outcome(
                    TruthValue.UNKNOWN,
                    ReasonCode.CONFLICTING_EVIDENCE,
                    evidenceFor(RuleValues.concat(present, absent)),
                    constraintRejected,
                    List.of(new MissingRequirement(
                            path,
                            ReasonCode.CONFLICTING_EVIDENCE,
                            "Conflicting present and absent assertions must be resolved.")));
        }
        List<EvidenceReference> rejectedWithUnresolved =
                RuleValues.uniqueEvidence(RuleValues.concat(constraintRejected, evidenceFor(unresolved)));
        if (!present.isEmpty()) {
            return new Outcome(
                    TruthValue.TRUE,
                    ReasonCode.EVALUATED_TRUE,
                    evidenceFor(present),
                    rejectedWithUnresolved,
                    List.of());
        }
        if (!absent.isEmpty()) {
            return new Outcome(
                    TruthValue.FALSE,
                    ReasonCode.EVALUATED_FALSE,
                    evidenceFor(absent),
                    rejectedWithUnresolved,
                    List.of());
        }
        return new Outcome(
                TruthValue.UNKNOWN,
                ReasonCode.MISSING_FACT,
                List.of(),
                rejectedWithUnresolved,
                List.of(new MissingRequirement(
                        path, ReasonCode.MISSING_FACT, "The recorded assertion is unknown.")));
    }

    // ---------------------------------------------------------------- numeric

    /** Port of {@code _numeric}. */
    private static Outcome numeric(
            String op,
            Map<String, Object> expression,
            PatientSnapshot patient,
            ScreeningContext context,
            EvaluationConstraints constraints) {

        if (!(expression.get("fact") instanceof String path)) {
            return Outcome.invalid(
                    "expression.fact", "A numeric fact path is required.", ReasonCode.INVALID_RULE);
        }
        if (AGE_PATH.equals(path)) {
            return age(op, expression, patient, context);
        }
        List<Fact> facts = matchingFacts(patient, path);
        if (facts.isEmpty()) {
            return Outcome.invalid(path, "A numeric observation is required.", ReasonCode.MISSING_FACT);
        }
        ConstraintOutcome narrowed = applyConstraints(facts, path, context, constraints);
        if (narrowed.error() != null) {
            return narrowed.error();
        }
        List<EvidenceReference> constraintRejected = narrowed.rejected();
        List<Fact> accepted = narrowed.accepted();

        Object expectedUnit = expression.get("unit");
        List<Fact> compatible = accepted.stream()
                .filter(fact -> fact.isNumeric()
                        && fact.assertion() == Assertion.PRESENT
                        && RuleValues.unitsMatch(fact.unit(), expectedUnit))
                .toList();
        if (compatible.isEmpty()) {
            return new Outcome(
                    TruthValue.UNKNOWN,
                    ReasonCode.INCOMPATIBLE_UNIT,
                    List.of(),
                    RuleValues.uniqueEvidence(RuleValues.concat(constraintRejected, evidenceFor(accepted))),
                    List.of(new MissingRequirement(
                            path,
                            ReasonCode.INCOMPATIBLE_UNIT,
                            "A value compatible with unit " + RuleValues.pythonStr(expectedUnit) + " is required.")));
        }

        // Python uses expression.get("selection", "latest"), so an explicit JSON null is *not* the
        // default — it falls through to the unsupported-selection branch below.
        Object selection = expression.containsKey("selection") ? expression.get("selection") : "latest";
        List<Fact> selected = compatible;
        if ("latest".equals(selection)) {
            List<Fact> dated = compatible.stream().filter(fact -> fact.effectiveDate() != null).toList();
            if (!dated.isEmpty()) {
                LocalDate latest = dated.stream().map(Fact::effectiveDate).max(LocalDate::compareTo).orElseThrow();
                selected = dated.stream().filter(fact -> latest.equals(fact.effectiveDate())).toList();
            } else if (compatible.size() > 1) {
                return Outcome.invalid(
                        path,
                        "Effective dates are required to select the latest value.",
                        ReasonCode.CONFLICTING_EVIDENCE);
            }
        } else if (!"any".equals(selection)) {
            return Outcome.invalid(
                    path,
                    "Selection " + RuleValues.pythonStr(selection) + " is unsupported.",
                    ReasonCode.UNSUPPORTED_RULE);
        }

        List<Boolean> comparisons = new ArrayList<>(selected.size());
        for (Fact fact : selected) {
            comparisons.add(compare(op, fact.numericValue(), expression));
        }
        if (comparisons.contains(null)) {
            return Outcome.invalid(path, "The numeric rule is invalid.", ReasonCode.INVALID_RULE);
        }
        if ("latest".equals(selection)
                && RuleValues.hasConflictingDecimals(selected.stream().map(Fact::numericValue).toList())) {
            return new Outcome(
                    TruthValue.UNKNOWN,
                    ReasonCode.CONFLICTING_EVIDENCE,
                    evidenceFor(selected),
                    List.of(),
                    List.of(new MissingRequirement(
                            path, ReasonCode.CONFLICTING_EVIDENCE, "Latest values conflict.")));
        }
        boolean satisfied = comparisons.contains(Boolean.TRUE);
        List<Fact> finalSelection = selected;
        return new Outcome(
                satisfied ? TruthValue.TRUE : TruthValue.FALSE,
                satisfied ? ReasonCode.EVALUATED_TRUE : ReasonCode.EVALUATED_FALSE,
                evidenceFor(selected),
                RuleValues.uniqueEvidence(RuleValues.concat(
                        constraintRejected,
                        evidenceFor(accepted.stream().filter(fact -> !finalSelection.contains(fact)).toList()))),
                List.of());
    }

    /** Port of the {@code demographic.age} branch of {@code _numeric}. */
    private static Outcome age(
            String op, Map<String, Object> expression, PatientSnapshot patient, ScreeningContext context) {

        BigDecimal years = ageInYears(patient, context);
        if (years == null) {
            return Outcome.invalid(
                    "date_of_birth", "Date of birth is required to calculate age.", ReasonCode.MISSING_FACT);
        }
        if (!RuleValues.unitsMatch("year", expression.get("unit"))) {
            return Outcome.invalid(
                    AGE_PATH, "The age rule requires a compatible year unit.", ReasonCode.INCOMPATIBLE_UNIT);
        }
        Boolean satisfied = compare(op, years, expression);
        if (satisfied == null) {
            return Outcome.invalid(AGE_PATH, "The numeric rule is invalid.", ReasonCode.INVALID_RULE);
        }
        return new Outcome(
                satisfied ? TruthValue.TRUE : TruthValue.FALSE,
                satisfied ? ReasonCode.EVALUATED_TRUE : ReasonCode.EVALUATED_FALSE,
                List.of(new EvidenceReference(
                        "date_of_birth", "Patient snapshot", years.toString(), "year", context.screeningDate())),
                List.of(),
                List.of());
    }

    /** Port of {@code _age}: whole years, decremented when the birthday has not yet occurred. */
    private static BigDecimal ageInYears(PatientSnapshot patient, ScreeningContext context) {
        LocalDate born = patient.dateOfBirth();
        LocalDate screening = context.screeningDate();
        if (born == null || born.isAfter(screening)) {
            return null;
        }
        int years = screening.getYear() - born.getYear();
        boolean beforeBirthday = screening.getMonthValue() < born.getMonthValue()
                || (screening.getMonthValue() == born.getMonthValue()
                        && screening.getDayOfMonth() < born.getDayOfMonth());
        if (beforeBirthday) {
            years -= 1;
        }
        return BigDecimal.valueOf(years);
    }

    /** Port of {@code _compare}. Returns {@code null} when the rule itself is malformed. */
    private static Boolean compare(String op, BigDecimal value, Map<String, Object> expression) {
        if ("between".equals(op)) {
            BigDecimal minimum = RuleValues.toDecimal(expression.get("min"));
            BigDecimal maximum = RuleValues.toDecimal(expression.get("max"));
            if (minimum == null || maximum == null || minimum.compareTo(maximum) > 0) {
                return null;
            }
            return minimum.compareTo(value) <= 0 && value.compareTo(maximum) <= 0;
        }
        BigDecimal target = RuleValues.toDecimal(expression.get("value"));
        if (target == null) {
            return null;
        }
        int comparison = value.compareTo(target);
        return switch (op) {
            case "eq" -> comparison == 0;
            case "lt" -> comparison < 0;
            case "lte" -> comparison <= 0;
            case "gt" -> comparison > 0;
            case "gte" -> comparison >= 0;
            default -> null;
        };
    }

    // ---------------------------------------------------------------- fact selection

    /** Port of {@code _fact_parts}: split {@code "<fact_type>.<concept>"}, or null if malformed. */
    private static FactPath factPath(String path) {
        int separator = path.indexOf('.');
        if (separator < 0) {
            return null;
        }
        FactType factType = FactType.fromValue(path.substring(0, separator));
        if (factType == null) {
            return null;
        }
        return new FactPath(factType, path.substring(separator + 1).strip().toLowerCase(java.util.Locale.ROOT));
    }

    /** Port of {@code _matching_facts}. Facts about someone other than the patient never match. */
    private static List<Fact> matchingFacts(PatientSnapshot patient, String path) {
        FactPath parts = factPath(path);
        if (parts == null) {
            return List.of();
        }
        return patient.facts().stream()
                .filter(fact -> fact.factType() == parts.factType()
                        && fact.concept().strip().toLowerCase(java.util.Locale.ROOT).equals(parts.concept())
                        && Fact.PATIENT_EXPERIENCER.equals(fact.experiencer()))
                .toList();
    }

    /**
     * Port of {@code _apply_constraints}. Applies, in order: the hard rule that evidence dated after
     * the screening date is unusable, then {@code current} narrowing, then the recency window.
     */
    private static ConstraintOutcome applyConstraints(
            List<Fact> facts, String path, ScreeningContext context, EvaluationConstraints constraints) {

        LocalDate screening = context.screeningDate();
        List<Fact> accepted = facts.stream()
                .filter(fact -> fact.effectiveDate() == null || !fact.effectiveDate().isAfter(screening))
                .toList();
        List<EvidenceReference> rejected = new ArrayList<>(evidenceFor(facts.stream()
                .filter(fact -> fact.effectiveDate() != null && fact.effectiveDate().isAfter(screening))
                .toList()));

        if (accepted.isEmpty()) {
            return ConstraintOutcome.failure(
                    rejected,
                    new Outcome(
                            TruthValue.UNKNOWN,
                            ReasonCode.MISSING_FACT,
                            List.of(),
                            rejected,
                            List.of(new MissingRequirement(
                                    path,
                                    ReasonCode.MISSING_FACT,
                                    "Evidence recorded after the screening date cannot be used."))));
        }

        if (constraints.currentOnly()) {
            List<Fact> current =
                    accepted.stream().filter(fact -> fact.temporality() == Temporality.CURRENT).toList();
            rejected.addAll(evidenceFor(
                    accepted.stream().filter(fact -> fact.temporality() != Temporality.CURRENT).toList()));
            if (current.isEmpty()) {
                return ConstraintOutcome.failure(
                        rejected,
                        new Outcome(
                                TruthValue.UNKNOWN,
                                ReasonCode.MISSING_FACT,
                                List.of(),
                                rejected,
                                List.of(new MissingRequirement(
                                        path, ReasonCode.MISSING_FACT, "A current patient fact is required."))));
            }
            accepted = current;
        }

        Integer withinDays = constraints.withinDays();
        if (withinDays != null) {
            List<Fact> recent = accepted.stream().filter(fact -> isWithin(fact, screening, withinDays)).toList();
            rejected.addAll(evidenceFor(
                    accepted.stream().filter(fact -> !isWithin(fact, screening, withinDays)).toList()));
            if (recent.isEmpty()) {
                return ConstraintOutcome.failure(
                        rejected,
                        new Outcome(
                                TruthValue.UNKNOWN,
                                ReasonCode.STALE_EVIDENCE,
                                List.of(),
                                rejected,
                                List.of(new MissingRequirement(
                                        path,
                                        ReasonCode.STALE_EVIDENCE,
                                        "A value within " + withinDays + " days before screening is required."))));
            }
            accepted = recent;
        }
        return ConstraintOutcome.success(accepted, RuleValues.uniqueEvidence(rejected));
    }

    private static boolean isWithin(Fact fact, LocalDate screening, int withinDays) {
        if (fact.effectiveDate() == null) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(fact.effectiveDate(), screening);
        return days >= 0 && days <= withinDays;
    }

    // ---------------------------------------------------------------- shared helpers

    /** Port of {@code _merge}: union the audit trails, then derive the reason from the truth value. */
    private static Outcome merge(List<Outcome> outcomes, TruthValue truth) {
        List<EvidenceReference> evidence = new ArrayList<>();
        List<EvidenceReference> rejected = new ArrayList<>();
        List<MissingRequirement> missing = new ArrayList<>();
        for (Outcome outcome : outcomes) {
            evidence.addAll(outcome.evidence());
            rejected.addAll(outcome.rejected());
            missing.addAll(outcome.missing());
        }
        ReasonCode reason;
        if (truth == TruthValue.TRUE) {
            reason = ReasonCode.EVALUATED_TRUE;
        } else if (truth == TruthValue.FALSE) {
            reason = ReasonCode.EVALUATED_FALSE;
        } else {
            reason = outcomes.stream()
                    .filter(outcome -> outcome.truth() == TruthValue.UNKNOWN)
                    .map(Outcome::reason)
                    .findFirst()
                    .orElse(ReasonCode.MISSING_FACT);
        }
        return new Outcome(
                truth,
                reason,
                RuleValues.uniqueEvidence(evidence),
                RuleValues.uniqueEvidence(rejected),
                RuleValues.uniqueMissing(missing));
    }

    /** A negated outcome keeps the child's reason only while the result stays unknown. */
    private static ReasonCode resolvedReason(TruthValue truth, ReasonCode fallback) {
        if (truth == TruthValue.TRUE) {
            return ReasonCode.EVALUATED_TRUE;
        }
        if (truth == TruthValue.FALSE) {
            return ReasonCode.EVALUATED_FALSE;
        }
        return fallback;
    }

    private static List<Fact> withAssertion(List<Fact> facts, Assertion assertion) {
        return facts.stream().filter(fact -> fact.assertion() == assertion).toList();
    }

    /** Port of {@code _evidence}, lifted over a list. */
    private static List<EvidenceReference> evidenceFor(List<Fact> facts) {
        return facts.stream()
                .map(fact -> new EvidenceReference(
                        fact.id(), fact.sourceLabel(), fact.evidenceValue(), fact.unit(), fact.effectiveDate()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asExpression(Object value) {
        return (Map<String, Object>) value;
    }

    private record FactPath(FactType factType, String concept) {}

    /** The three-way return of {@code _apply_constraints}, made explicit. */
    private record ConstraintOutcome(List<Fact> accepted, List<EvidenceReference> rejected, Outcome error) {

        static ConstraintOutcome success(List<Fact> accepted, List<EvidenceReference> rejected) {
            return new ConstraintOutcome(accepted, rejected, null);
        }

        static ConstraintOutcome failure(List<EvidenceReference> rejected, Outcome error) {
            return new ConstraintOutcome(List.of(), List.copyOf(rejected), error);
        }
    }
}
