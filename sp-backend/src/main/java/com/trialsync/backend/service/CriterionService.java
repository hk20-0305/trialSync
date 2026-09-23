package com.trialsync.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.dto.trial.CriterionCreateRequest;
import com.trialsync.backend.dto.trial.CriterionRead;
import com.trialsync.backend.dto.trial.GuidedCriterionCreateRequest;
import com.trialsync.backend.dto.trial.UnsupportedCriterionCreateRequest;
import com.trialsync.backend.entity.ClinicalConcept;
import com.trialsync.backend.entity.Criterion;
import com.trialsync.backend.entity.TrialVersion;
import com.trialsync.backend.repository.ClinicalConceptRepository;
import com.trialsync.backend.repository.CriterionRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of the criterion half of {@code trialsync.api.trials}, including the guided rule builder.
 *
 * <p>Three routes create a criterion and they differ in who writes the rule. The free-form route
 * takes the rule from the client, the guided route derives it from a catalog subject and an
 * operator, and the unsupported route deliberately writes none - it records that a reviewer read the
 * text and could not express it, which is what keeps the version out of approval until it is
 * resolved.
 *
 * <p>Every route is gated on the version still being a draft, so an approved version - the only kind
 * a screening can cite - can never have its criteria changed underneath a saved result.
 */
@Service
public class CriterionService {

    /** Operators that describe a numeric threshold or span rather than presence. */
    private static final Set<String> RANGE_OPERATORS = Set.of("gte", "lte", "between");

    /** {@code input_kind} of a catalog entry that carries a measured value. */
    private static final String NUMERIC_INPUT_KIND = "numeric";

    private final TrialService trialService;
    private final CriterionRepository criteria;
    private final ClinicalConceptRepository concepts;
    private final ObjectMapper objectMapper;

    public CriterionService(
            TrialService trialService,
            CriterionRepository criteria,
            ClinicalConceptRepository concepts,
            ObjectMapper objectMapper) {
        this.trialService = trialService;
        this.criteria = criteria;
        this.concepts = concepts;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------- endpoints

    /**
     * Port of {@code create_criterion()}: the free-form route, where the caller supplies both the
     * text and the position.
     */
    @Transactional
    public CriterionRead createCriterion(
            UUID trialId, UUID versionId, CriterionCreateRequest payload) {
        requireDraftVersion(trialId, versionId);
        Criterion criterion =
                new Criterion(
                        versionId, payload.getKind(), payload.getOrder(), payload.getSourceText());
        criterion.setNormalizedRule(writeRule(payload.getNormalizedRule()));
        criterion.setRequired(payload.getRequired());
        try {
            criterion = criteria.saveAndFlush(criterion);
        } catch (DataIntegrityViolationException exception) {
            throw orderConflict();
        }
        return CriterionRead.of(criterion);
    }

    /**
     * Port of {@code create_guided_criterion()}: the rule builder writes both the sentence and the
     * machine-readable rule, so the two can never drift apart.
     *
     * <p>The position is appended after the current highest, which is why no order conflict is caught
     * here - the value is derived rather than supplied.
     */
    @Transactional
    public CriterionRead createGuidedCriterion(
            UUID trialId, UUID versionId, GuidedCriterionCreateRequest payload) {
        requireDraftVersion(trialId, versionId);
        GuidedValues values = guidedCriterionValues(payload);
        Criterion criterion =
                new Criterion(versionId, payload.getKind(), nextOrder(versionId), values.sourceText());
        criterion.setNormalizedRule(writeRule(values.rule()));
        criterion.setRequired(true);
        criterion = criteria.saveAndFlush(criterion);
        return CriterionRead.of(criterion);
    }

    /**
     * Port of {@code create_unsupported_criterion()}: records the criterion with no rule at all.
     *
     * <p>{@code category} is validated by the request model and then ignored, exactly as in Python -
     * the stored row keeps only the kind and the normalised text.
     */
    @Transactional
    public CriterionRead createUnsupportedCriterion(
            UUID trialId, UUID versionId, UnsupportedCriterionCreateRequest payload) {
        requireDraftVersion(trialId, versionId);
        Criterion criterion =
                new Criterion(
                        versionId, payload.getKind(), nextOrder(versionId), payload.getSourceText());
        criterion.setNormalizedRule(null);
        criterion.setRequired(true);
        criterion = criteria.saveAndFlush(criterion);
        return CriterionRead.of(criterion);
    }

    /** Port of {@code update_criterion()}: replaces every field, including the position. */
    @Transactional
    public CriterionRead updateCriterion(
            UUID trialId, UUID versionId, UUID criterionId, CriterionCreateRequest payload) {
        requireDraftVersion(trialId, versionId);
        Criterion criterion = ownedCriterion(versionId, criterionId);
        criterion.setKind(payload.getKind());
        criterion.setOrder(payload.getOrder());
        criterion.setSourceText(payload.getSourceText());
        criterion.setNormalizedRule(writeRule(payload.getNormalizedRule()));
        criterion.setRequired(payload.getRequired());
        try {
            criteria.flush();
        } catch (DataIntegrityViolationException exception) {
            throw orderConflict();
        }
        return CriterionRead.of(criterion);
    }

    /**
     * Port of {@code update_guided_criterion()}: rebuilds the text and rule from a new selection.
     *
     * <p>The position is left alone, so re-editing a criterion keeps it where the reviewer put it.
     */
    @Transactional
    public CriterionRead updateGuidedCriterion(
            UUID trialId, UUID versionId, UUID criterionId, GuidedCriterionCreateRequest payload) {
        requireDraftVersion(trialId, versionId);
        Criterion criterion = ownedCriterion(versionId, criterionId);
        GuidedValues values = guidedCriterionValues(payload);
        criterion.setKind(payload.getKind());
        criterion.setSourceText(values.sourceText());
        criterion.setNormalizedRule(writeRule(values.rule()));
        criterion.setRequired(true);
        criteria.flush();
        return CriterionRead.of(criterion);
    }

    /** Port of {@code delete_criterion()}. */
    @Transactional
    public void deleteCriterion(UUID trialId, UUID versionId, UUID criterionId) {
        requireDraftVersion(trialId, versionId);
        Criterion criterion = ownedCriterion(versionId, criterionId);
        criteria.delete(criterion);
        criteria.flush();
    }

    // ---------------------------------------------------------- rule building

    /** The two halves the guided builder produces for a criterion. */
    private record GuidedValues(String sourceText, Map<String, Object> rule) {}

    /**
     * Port of {@code guided_criterion_values()}.
     *
     * <p>Age and biological sex are handled before the catalog is consulted because they are derived
     * from the patient's demographics rather than recorded as facts, so there is no catalog row to
     * look up. Everything else resolves to an active catalog entry, and the shape of the rule follows
     * from that entry's {@code input_kind}: a status concept can only be asserted present or absent,
     * while a measured observation takes a threshold or a span with the entry's own unit.
     *
     * <p>The returned map is ordered, and the insertion order matches the Python literal, so the JSON
     * stored in {@code normalized_rule} is key-for-key what the Python service wrote.
     */
    private GuidedValues guidedCriterionValues(GuidedCriterionCreateRequest payload) {
        String subjectKey = payload.getSubjectKey();
        String operator = payload.getOperator();

        if ("age".equals(subjectKey)) {
            if (!RANGE_OPERATORS.contains(operator)) {
                throw criterionValueError(
                        "Age supports minimum, maximum, or range criteria.", "operator");
            }
            if ("between".equals(operator)) {
                BigDecimal minimum = payload.getMinimum();
                BigDecimal maximum = payload.getMaximum();
                if (minimum == null || maximum == null || minimum.compareTo(maximum) > 0) {
                    throw criterionValueError(
                            "Enter an age range with the minimum at or below the maximum.", "minimum");
                }
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("op", "between");
                rule.put("fact", "demographic.age");
                rule.put("min", jsonNumber(minimum));
                rule.put("max", jsonNumber(maximum));
                rule.put("unit", "year");
                return new GuidedValues(
                        "Age between "
                                + displayNumber(minimum)
                                + " and "
                                + displayNumber(maximum)
                                + " years",
                        rule);
            }
            if (payload.getValue() == null) {
                throw criterionValueError("Enter an age value.", "value");
            }
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("op", operator);
            rule.put("fact", "demographic.age");
            rule.put("value", jsonNumber(payload.getValue()));
            rule.put("unit", "year");
            return new GuidedValues(
                    "Age "
                            + ("gte".equals(operator) ? "at least" : "at most")
                            + " "
                            + displayNumber(payload.getValue())
                            + " years",
                    rule);
        }

        if ("biological_sex".equals(subjectKey)) {
            if (!"is".equals(operator) || payload.getBiologicalSex() == null) {
                throw criterionValueError(
                        "Choose Male or Female for the biological-sex criterion.", "biological_sex");
            }
            String sex = payload.getBiologicalSex();
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("op", "concept_is");
            rule.put("fact_type", "demographic");
            rule.put("concept", sex);
            return new GuidedValues("Biological sex is " + capitalize(sex), rule);
        }

        ClinicalConcept entry = concepts.findByKeyAndActiveTrue(subjectKey).orElse(null);
        if (entry == null) {
            throw criterionValueError("Choose a supported criterion from the catalog.", "subject_key");
        }
        if (!entry.isScreeningSupported()) {
            throw criterionValueError(
                    entry.getDisplayLabel()
                            + " is available for patient records but not trial screening.",
                    "subject_key");
        }
        String fact = entry.getFactType().value() + "." + entry.getConcept();

        if (!NUMERIC_INPUT_KIND.equals(entry.getInputKind())) {
            if (!"present".equals(operator) && !"absent".equals(operator)) {
                throw criterionValueError(
                        entry.getDisplayLabel() + " supports present or absent criteria.", "operator");
            }
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("op", operator);
            rule.put("fact", fact);
            return new GuidedValues(
                    entry.getDisplayLabel()
                            + " "
                            + ("present".equals(operator) ? "is present" : "is absent"),
                    rule);
        }

        if (entry.getFactType() != FactType.OBSERVATION) {
            throw criterionValueError("Numeric criteria must use an observation.", "subject_key");
        }
        if (!RANGE_OPERATORS.contains(operator)) {
            throw criterionValueError(
                    entry.getDisplayLabel() + " supports minimum, maximum, or range criteria.",
                    "operator");
        }
        String unit = requiredUnit(entry);

        if ("between".equals(operator)) {
            BigDecimal minimum = payload.getMinimum();
            BigDecimal maximum = payload.getMaximum();
            if (minimum == null || maximum == null || minimum.compareTo(maximum) > 0) {
                throw criterionValueError(
                        "Enter a range with the minimum at or below the maximum.", "minimum");
            }
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("op", "between");
            rule.put("fact", fact);
            rule.put("min", jsonNumber(minimum));
            rule.put("max", jsonNumber(maximum));
            rule.put("unit", unit);
            rule.put("selection", "latest");
            return new GuidedValues(
                    entry.getDisplayLabel()
                            + " between "
                            + displayNumber(minimum)
                            + " and "
                            + displayNumber(maximum)
                            + " "
                            + unit,
                    rule);
        }
        if (payload.getValue() == null) {
            throw criterionValueError("Enter a numeric threshold.", "value");
        }
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("op", operator);
        rule.put("fact", fact);
        rule.put("value", jsonNumber(payload.getValue()));
        rule.put("unit", unit);
        rule.put("selection", "latest");
        return new GuidedValues(
                entry.getDisplayLabel()
                        + " "
                        + ("gte".equals(operator) ? "at least" : "at most")
                        + " "
                        + displayNumber(payload.getValue())
                        + " "
                        + unit,
                rule);
    }

    /**
     * The unit a numeric rule is expressed in.
     *
     * <p>Python read {@code entry.fixed_unit or entry.allowed_units[0]}. The catalog adapter never
     * populates {@code allowed_units}, and the entry contract requires a numeric entry to carry a
     * {@code fixed_unit}, so the fallback was unreachable and a numeric entry without a unit raised
     * an {@code IndexError} - a 500. The same unreachable state is reported the same way rather than
     * being papered over with a guessed unit, which would silently change what a rule means.
     */
    private String requiredUnit(ClinicalConcept entry) {
        String unit = entry.getFixedUnit();
        if (unit == null || unit.isEmpty()) {
            throw new IllegalStateException(
                    "Catalog entry '" + entry.getKey() + "' is numeric but carries no fixed unit");
        }
        return unit;
    }

    /**
     * Port of {@code display_number()}, kept character-for-character.
     *
     * <p>Python stripped trailing zeros from the plain decimal text and then a trailing point, which
     * is only correct for a value that has a fractional part: a whole number such as {@code 100}
     * renders as {@code "1"} and {@code 0} renders as the empty string. That is a real defect, but it
     * only reaches the human-readable {@code source_text} - the rule itself is built from
     * {@link #jsonNumber} and stays exact - and reproducing it keeps text already stored by the
     * Python service consistent with text written from here. Fixing it belongs in a change that also
     * rewrites the affected rows.
     */
    static String displayNumber(BigDecimal value) {
        String text = value.toPlainString();
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') {
            end--;
        }
        while (end > 0 && text.charAt(end - 1) == '.') {
            end--;
        }
        return text.substring(0, end);
    }

    /**
     * Port of {@code json_number()}: a value with no fractional part is stored as a JSON integer and
     * anything else as a float, so the rule reads back as the same Python literal.
     */
    static Object jsonNumber(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() <= 0
                ? (Object) stripped.toBigIntegerExact()
                : (Object) value.doubleValue();
    }

    /** {@code str.capitalize()}: first character upper, remainder lower. */
    private static String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT)
                + value.substring(1).toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- helpers

    private TrialVersion requireDraftVersion(UUID trialId, UUID versionId) {
        TrialVersion version = trialService.ownedVersion(trialId, versionId);
        trialService.requireDraft(version);
        return version;
    }

    private Criterion ownedCriterion(UUID versionId, UUID criterionId) {
        return criteria
                .findByIdAndTrialVersionId(criterionId, versionId)
                .orElseThrow(
                        () -> ApplicationError.notFound("CRITERION_NOT_FOUND", "Criterion was not found."));
    }

    /** Appends after the current highest position; an empty version starts at 1. */
    private int nextOrder(UUID versionId) {
        Integer highest = criteria.findMaxOrder(versionId);
        return (highest == null ? 0 : highest) + 1;
    }

    private String writeRule(Map<String, Object> rule) {
        if (rule == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(rule);
        } catch (JsonProcessingException exception) {
            throw criterionValueError("The rule could not be stored.", "normalized_rule");
        }
    }

    private ApplicationError criterionValueError(String message, String field) {
        return ApplicationError.unprocessable("TRIAL_CRITERION_VALUE_INVALID", message, field);
    }

    private ApplicationError orderConflict() {
        return new ApplicationError(
                "CRITERION_ORDER_EXISTS", "Criterion order must be unique within a version.", 409, "order");
    }
}
