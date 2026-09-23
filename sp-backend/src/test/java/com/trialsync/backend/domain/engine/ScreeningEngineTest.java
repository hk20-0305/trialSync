package com.trialsync.backend.domain.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trialsync.backend.domain.model.ApprovedTrialVersion;
import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.domain.model.Criterion;
import com.trialsync.backend.domain.model.CriterionEvaluation;
import com.trialsync.backend.domain.model.CriterionKind;
import com.trialsync.backend.domain.model.CriterionResult;
import com.trialsync.backend.domain.model.Fact;
import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.domain.model.OverallState;
import com.trialsync.backend.domain.model.PatientSnapshot;
import com.trialsync.backend.domain.model.ReasonCode;
import com.trialsync.backend.domain.model.ScreeningContext;
import com.trialsync.backend.domain.model.ScreeningResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScreeningEngineTest {

    private static final LocalDate SCREENING_DATE = LocalDate.of(2026, 7, 15);
    private static final ScreeningContext CONTEXT = new ScreeningContext(SCREENING_DATE, "test-engine", "local-1", "units-1");

    private static Criterion criterion(
            Map<String, Object> expression, CriterionKind kind, String criterionId, int order, boolean required) {
        return new Criterion(criterionId, kind, order, "Synthetic rule " + criterionId, expression, required);
    }

    private static Criterion criterion(Map<String, Object> expression) {
        return criterion(expression, CriterionKind.INCLUSION, "criterion-1", 1, true);
    }

    private static ScreeningResult evaluate(PatientSnapshot patient, Criterion... criteria) {
        ApprovedTrialVersion trial = new ApprovedTrialVersion("trial-version-1", "1", List.of(criteria));
        return ScreeningEngine.screen(patient, trial, CONTEXT);
    }

    private static final Criterion AGE_RULE = criterion(
            Map.of("op", "between", "fact", "demographic.age", "min", 18, "max", 75, "unit", "year"));

    @ParameterizedTest
    @CsvSource({
        "2008-07-15, PASS",
        "2008-07-16, FAIL",
        "1951-07-15, PASS",
        "1950-07-15, FAIL",
        "1980-02-29, PASS"
    })
    void testAgeBoundariesUseCompletedYears(String dobStr, CriterionResult expected) {
        LocalDate dob = LocalDate.parse(dobStr);
        PatientSnapshot patient = new PatientSnapshot("snapshot-1", "1", dob, List.of());
        ScreeningResult result = evaluate(patient, AGE_RULE);

        assertEquals(1, result.evaluations().size());
        assertEquals(expected, result.evaluations().getFirst().result());
    }

    @Test
    void testMissingDobIsUnknownWithRequirement() {
        PatientSnapshot patient = new PatientSnapshot("snapshot-1", "1", null, List.of());
        ScreeningResult result = evaluate(patient, AGE_RULE);

        CriterionEvaluation evaluation = result.evaluations().getFirst();
        assertEquals(CriterionResult.UNKNOWN, evaluation.result());
        assertEquals(ReasonCode.MISSING_FACT, evaluation.reasonCode());
        assertEquals("date_of_birth", evaluation.missing().getFirst().fact());
        assertEquals(OverallState.NEEDS_REVIEW, result.overallState());
    }

    @ParameterizedTest
    @CsvSource({
        "6.5, PASS",
        "8.0, PASS",
        "8.1, FAIL"
    })
    void testHba1cNumericRange(String valueStr, CriterionResult expected) {
        Criterion rule = criterion(Map.of(
                "op", "between",
                "fact", "observation.hba1c",
                "min", "6.5",
                "max", "8.0",
                "unit", "%",
                "selection", "latest"));

        Fact hba1cFact = Fact.builder("hba1c-1", FactType.OBSERVATION, "hba1c")
                .numericValue(new BigDecimal(valueStr))
                .unit("percent")
                .effectiveDate(SCREENING_DATE.minusDays(5))
                .build();

        PatientSnapshot patient = new PatientSnapshot("snapshot-1", "1", null, List.of(hba1cFact));
        ScreeningResult result = evaluate(patient, rule);

        assertEquals(expected, result.evaluations().getFirst().result());
        assertEquals("hba1c-1", result.evaluations().getFirst().evidence().getFirst().factId());
    }

    @ParameterizedTest
    @CsvSource({
        "28, 10, FAIL, EVALUATED_TRUE",
        "72, 10, PASS, EVALUATED_FALSE",
        "72, 240, UNKNOWN, STALE_EVIDENCE"
    })
    void testEgfrExclusionRequiresRecentEvidence(
            String valueStr, int ageDays, CriterionResult expected, ReasonCode reason) {
        Criterion rule = criterion(
                Map.of(
                        "op", "within_before",
                        "days", 30,
                        "arg", Map.of(
                                "op", "lt",
                                "fact", "observation.egfr",
                                "value", 30,
                                "unit", "mL/min/1.73m2",
                                "selection", "latest")),
                CriterionKind.EXCLUSION,
                "criterion-1",
                1,
                true);

        Fact egfrFact = Fact.builder("egfr-1", FactType.OBSERVATION, "egfr")
                .numericValue(new BigDecimal(valueStr))
                .unit("mL/min/1.73m²")
                .effectiveDate(SCREENING_DATE.minusDays(ageDays))
                .build();

        PatientSnapshot patient = new PatientSnapshot("snapshot-1", "1", null, List.of(egfrFact));
        ScreeningResult result = evaluate(patient, rule);

        assertEquals(expected, result.evaluations().getFirst().result());
        assertEquals(reason, result.evaluations().getFirst().reasonCode());
    }

    @Test
    void testNoEgfrIsUnknownNotExclusionPass() {
        Criterion rule = criterion(
                Map.of(
                        "op", "lt",
                        "fact", "observation.egfr",
                        "value", 30,
                        "unit", "mL/min/1.73m2"),
                CriterionKind.EXCLUSION,
                "criterion-1",
                1,
                true);

        PatientSnapshot patient = new PatientSnapshot("snapshot-1", "1", null, List.of());
        assertEquals(CriterionResult.UNKNOWN, evaluate(patient, rule).evaluations().getFirst().result());
    }

    @Test
    void testExplicitDiagnosisAbsenceDiffersFromMissingInformation() {
        Criterion rule = criterion(Map.of("op", "present", "fact", "condition.type2_diabetes"));

        Fact absentFact = Fact.builder("dx-neg", FactType.CONDITION, "type2_diabetes")
                .assertion(Assertion.ABSENT)
                .build();
        PatientSnapshot absentPatient = new PatientSnapshot("snapshot-1", "1", null, List.of(absentFact));
        PatientSnapshot emptyPatient = new PatientSnapshot("snapshot-2", "1", null, List.of());

        assertEquals(CriterionResult.FAIL, evaluate(absentPatient, rule).evaluations().getFirst().result());
        assertEquals(CriterionResult.UNKNOWN, evaluate(emptyPatient, rule).evaluations().getFirst().result());
    }

    @Test
    void testType1DoesNotSatisfyType2Concept() {
        Criterion rule = criterion(Map.of("op", "present", "fact", "condition.type2_diabetes"));
        Fact type1Fact = Fact.builder("dx-1", FactType.CONDITION, "type1_diabetes").build();
        PatientSnapshot patient = new PatientSnapshot("snapshot-1", "1", null, List.of(type1Fact));

        assertEquals(CriterionResult.UNKNOWN, evaluate(patient, rule).evaluations().getFirst().result());
    }

    @Test
    void testExclusionConversionRequiresExplicitNegativeEvidence() {
        Criterion rule = criterion(
                Map.of("op", "present", "fact", "condition.pregnancy"),
                CriterionKind.EXCLUSION,
                "criterion-1",
                1,
                true);

        Fact triggeredFact = Fact.builder("preg", FactType.CONDITION, "pregnancy").build();
        Fact negativeFact = Fact.builder("not-preg", FactType.CONDITION, "pregnancy")
                .assertion(Assertion.ABSENT)
                .build();

        PatientSnapshot triggered = new PatientSnapshot("s1", "1", null, List.of(triggeredFact));
        PatientSnapshot negative = new PatientSnapshot("s2", "1", null, List.of(negativeFact));
        PatientSnapshot missing = new PatientSnapshot("s3", "1", null, List.of());

        assertEquals(CriterionResult.FAIL, evaluate(triggered, rule).evaluations().getFirst().result());
        assertEquals(CriterionResult.PASS, evaluate(negative, rule).evaluations().getFirst().result());
        assertEquals(CriterionResult.UNKNOWN, evaluate(missing, rule).evaluations().getFirst().result());
    }

    @Test
    void testOverallStateDetermination() {
        // Any required fail -> likely_ineligible
        Criterion passCrit = criterion(Map.of("op", "present", "fact", "condition.c1"), CriterionKind.INCLUSION, "c1", 1, true);
        Criterion failCrit = criterion(Map.of("op", "present", "fact", "condition.c2"), CriterionKind.INCLUSION, "c2", 2, true);

        Fact f1 = Fact.builder("f1", FactType.CONDITION, "c1").build();
        Fact f2 = Fact.builder("f2", FactType.CONDITION, "c2").assertion(Assertion.ABSENT).build();

        PatientSnapshot p = new PatientSnapshot("s", "1", null, List.of(f1, f2));
        ScreeningResult res = evaluate(p, passCrit, failCrit);

        assertEquals(OverallState.LIKELY_INELIGIBLE, res.overallState());

        // All required pass -> potentially_eligible
        Fact f2Present = Fact.builder("f2", FactType.CONDITION, "c2").assertion(Assertion.PRESENT).build();
        PatientSnapshot pEligible = new PatientSnapshot("s", "1", null, List.of(f1, f2Present));
        ScreeningResult resEligible = evaluate(pEligible, passCrit, failCrit);

        assertEquals(OverallState.POTENTIALLY_ELIGIBLE, resEligible.overallState());

        // Unknown required -> needs_review
        PatientSnapshot pReview = new PatientSnapshot("s", "1", null, List.of(f1));
        ScreeningResult resReview = evaluate(pReview, passCrit, failCrit);

        assertEquals(OverallState.NEEDS_REVIEW, resReview.overallState());
    }
}
