package com.trialsync.backend.service;

import java.util.List;
import java.util.Map;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.dto.concept.PatientFactCatalogEntry;
import com.trialsync.backend.dto.patient.FactValue;
import com.trialsync.backend.entity.Patient;
import com.trialsync.backend.entity.PatientFact;

/**
 * The one clinical rule the patient API enforces rather than merely reports: a pregnancy cannot be
 * asserted for a patient recorded as male.
 *
 * <p>It is enforced from both directions, because either edit can create the contradiction -
 * setting the biological sex to male while a pregnancy stands, or asserting a pregnancy on a male
 * record. Both answer the same conflict, differing only in which field is blamed, so the client can
 * highlight the input the user was editing.
 *
 * <p>A record that is <em>already</em> inconsistent is not blocked from being read or edited; that
 * case surfaces as a consistency issue on the patient response instead. The guard exists to stop a
 * single edit from introducing the contradiction, not to make existing data unreachable.
 */
public final class PatientProfileRules {

    /** The two values {@code ck_patients_biological_sex} admits, alongside null. */
    public static final String MALE = "male";

    public static final String FEMALE = "female";

    /** The catalog concept the pregnancy rules key on. */
    public static final String PREGNANCY_CONCEPT = "pregnancy";

    private PatientProfileRules() {}

    /** True for the two recorded values and for "not recorded". */
    public static boolean isSupportedSex(String sex) {
        return sex == null || MALE.equals(sex) || FEMALE.equals(sex);
    }

    /**
     * Port of {@code pregnancy_present_fact}: the patient's first active fact asserting a
     * pregnancy, or {@code null}.
     *
     * <p>Reads the patient's fact collection, which excludes voided rows, so a removed pregnancy no
     * longer blocks a sex change.
     */
    public static PatientFact presentPregnancyFact(Patient patient) {
        for (PatientFact fact : patient.getFacts()) {
            if (fact.getFactType() == FactType.CONDITION
                    && PREGNANCY_CONCEPT.equals(fact.getConcept())
                    && fact.getAssertion() == Assertion.PRESENT) {
                return fact;
            }
        }
        return null;
    }

    /**
     * Port of {@code pregnancy_sex_conflict}.
     *
     * <p>The offending fact's identifier is included when one is known, so the UI can link straight
     * to the detail that has to be reconciled.
     */
    public static ApplicationError sexConflict(String field, PatientFact fact) {
        List<Map<String, Object>> details =
                fact == null ? null : List.of(Map.of("fact_id", fact.getId().toString()));
        return new ApplicationError(
                "PATIENT_PREGNANCY_SEX_CONFLICT",
                "Pregnancy cannot be recorded as Pregnant when biological sex "
                        + "is Male. Reconcile the pregnancy status or biological sex first.",
                409,
                field,
                details);
    }

    /**
     * Port of {@code validate_pregnancy_value_for_patient}: refuses a pregnancy assertion on a male
     * record.
     *
     * <p>Only the pregnancy input kind is checked, so an unrelated condition is unaffected.
     */
    public static void validateValueForPatient(
            Patient patient, PatientFactCatalogEntry entry, FactValue value, PatientFact fact) {
        if (FactValue.PREGNANCY_STATUS.equals(entry.inputKind())
                && value.assertion() == Assertion.PRESENT
                && MALE.equals(patient.getSex())) {
            throw sexConflict("value.assertion", fact);
        }
    }
}
