package com.trialsync.backend.imports;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.domain.model.CriterionKind;
import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.dto.imports.PatientFactCandidate;
import com.trialsync.backend.dto.imports.PatientImportCandidates;
import com.trialsync.backend.dto.imports.PatientProfileCandidate;
import com.trialsync.backend.dto.imports.SourceReference;
import com.trialsync.backend.dto.imports.TrialCriterionCandidate;
import com.trialsync.backend.dto.imports.TrialImportCandidates;
import com.trialsync.backend.dto.imports.TrialProfileCandidate;
import org.springframework.stereotype.Component;

/**
 * Port of the Pydantic models in {@code trialsync.imports.schemas}: the gate every candidate
 * document passes through before it is stored, shown to a reviewer or turned into records.
 *
 * <p>Three callers depend on it and all three matter:
 *
 * <ul>
 *   <li>a reviewer's {@code PUT}, whose payload is arbitrary client JSON;
 *   <li>the Groq extractor, whose output is arbitrary model JSON;
 *   <li>approval, which re-validates what was stored before writing any patient or trial row.
 * </ul>
 *
 * <p>Validation is therefore deliberately whole-document: every field is checked and every failure
 * collected, rather than aborting at the first one, so a reviewer fixing a form sees all of its
 * problems at once. That also reproduces Pydantic's behaviour, including the detail that a
 * model-level rule such as "a numeric value needs a unit" only runs once the individual fields of
 * that model are sound.
 */
@Component
public class CandidateValidator {

    private static final List<String> FACT_TYPES =
            List.of("demographic", "condition", "medication", "observation");
    private static final List<String> ASSERTIONS = List.of("present", "absent", "unknown");
    private static final List<String> CRITERION_KINDS = List.of("inclusion", "exclusion");
    private static final List<String> PARSE_STATES =
            List.of(TrialCriterionCandidate.PARSED, TrialCriterionCandidate.NEEDS_MANUAL_RULE);
    private static final List<String> BIOLOGICAL_SEXES = List.of("male", "female");

    private static final int MAX_FACTS = 100;
    private static final int MAX_CRITERIA = 200;
    private static final int MAX_WARNINGS = 10;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CandidateValidator(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * {@code PatientImportCandidates.model_validate(candidates)}.
     *
     * @throws ImportCandidateValidationException with one entry per failed field
     */
    public PatientImportCandidates validatePatient(JsonNode raw) {
        CandidateErrors errors = new CandidateErrors();
        ObjectNode root = requireModel(errors, List.of(), raw, "PatientImportCandidates");

        PatientProfileCandidate profile = null;
        JsonNode profileNode = root == null ? null : root.get("profile");
        if (root != null) {
            if (profileNode == null) {
                CandidateErrors.missing(errors, List.of("profile"), root);
            } else {
                profile = patientProfile(errors, List.of("profile"), profileNode);
            }
        }

        List<PatientFactCandidate> facts = new ArrayList<>();
        JsonNode factsNode = root == null ? null : root.get("facts");
        if (factsNode != null && !factsNode.isNull()) {
            if (!factsNode.isArray()) {
                errors.add("list_type", List.of("facts"), "Input should be a valid list", factsNode);
            } else {
                int index = 0;
                for (JsonNode item : factsNode) {
                    PatientFactCandidate fact =
                            patientFact(errors, List.of("facts", index), item);
                    if (fact != null) {
                        facts.add(fact);
                    }
                    index++;
                }
                if (factsNode.size() > MAX_FACTS) {
                    CandidateErrors.listTooLong(
                            errors, List.of("facts"), factsNode, MAX_FACTS, factsNode.size());
                }
            }
        }

        errors.raiseIfAny();
        return new PatientImportCandidates(profile, List.copyOf(facts));
    }

    /** {@code TrialImportCandidates.model_validate(candidates)}. */
    public TrialImportCandidates validateTrial(JsonNode raw) {
        CandidateErrors errors = new CandidateErrors();
        ObjectNode root = requireModel(errors, List.of(), raw, "TrialImportCandidates");

        TrialProfileCandidate profile = null;
        if (root != null) {
            JsonNode profileNode = root.get("profile");
            if (profileNode == null) {
                CandidateErrors.missing(errors, List.of("profile"), root);
            } else {
                profile = trialProfile(errors, List.of("profile"), profileNode);
            }
        }

        List<TrialCriterionCandidate> criteria = new ArrayList<>();
        JsonNode criteriaNode = root == null ? null : root.get("criteria");
        if (criteriaNode != null && !criteriaNode.isNull()) {
            if (!criteriaNode.isArray()) {
                errors.add(
                        "list_type", List.of("criteria"), "Input should be a valid list", criteriaNode);
            } else {
                int index = 0;
                for (JsonNode item : criteriaNode) {
                    TrialCriterionCandidate criterion =
                            trialCriterion(errors, List.of("criteria", index), item);
                    if (criterion != null) {
                        criteria.add(criterion);
                    }
                    index++;
                }
                if (criteriaNode.size() > MAX_CRITERIA) {
                    CandidateErrors.listTooLong(
                            errors,
                            List.of("criteria"),
                            criteriaNode,
                            MAX_CRITERIA,
                            criteriaNode.size());
                }
            }
        }

        errors.raiseIfAny();
        return new TrialImportCandidates(profile, List.copyOf(criteria));
    }

    /**
     * {@code model_dump(mode="json")}.
     *
     * <p>Record component order is the Pydantic field order, so the stored JSON keeps the key
     * ordering the React reviewer and the existing corpus already have. Note that this is where a
     * reviewed candidate's {@code source} settles into {@code span_id, page, start, end, text}, which
     * is not the order the raw parser emitted.
     */
    public ObjectNode toJson(Object validated) {
        return objectMapper.valueToTree(validated);
    }

    // ------------------------------------------------------------------ patient

    private PatientProfileCandidate patientProfile(
            CandidateErrors errors, List<Object> loc, JsonNode raw) {
        ObjectNode node = requireModel(errors, loc, raw, "PatientProfileCandidate");
        if (node == null) {
            return null;
        }

        String displayName = null;
        JsonNode displayNameNode = node.get("display_name");
        if (displayNameNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "display_name"), node);
        } else {
            displayName =
                    CandidateErrors.string(
                            errors,
                            CandidateErrors.path(loc, "display_name"),
                            displayNameNode,
                            1,
                            120);
        }

        LocalDate dateOfBirth = null;
        JsonNode dateOfBirthNode = node.get("date_of_birth");
        if (dateOfBirthNode != null && !dateOfBirthNode.isNull()) {
            List<Object> dobLoc = CandidateErrors.path(loc, "date_of_birth");
            dateOfBirth = CandidateErrors.date(errors, dobLoc, dateOfBirthNode);
            // `reject_future_date_of_birth`: a synthetic person cannot be born tomorrow, and a
            // typo here would silently distort every age-based eligibility rule.
            if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now(clock))) {
                errors.add(
                        "patient_date_of_birth_in_future",
                        dobLoc,
                        "Date of birth cannot be in the future.",
                        dateOfBirthNode);
                dateOfBirth = null;
            }
        }

        String sex = null;
        JsonNode sexNode = node.get("sex");
        if (sexNode != null && !sexNode.isNull()) {
            // `normalize_recognized_legacy_sex`: "Male" and " female " are accepted and folded,
            // anything else is left alone so the enum rejects it with its own message.
            JsonNode normalized = sexNode;
            if (sexNode.isTextual()) {
                String trimmed = PythonText.lower(PythonText.strip(sexNode.asText()));
                if (BIOLOGICAL_SEXES.contains(trimmed)) {
                    normalized = objectMapper.getNodeFactory().textNode(trimmed);
                }
            }
            sex =
                    CandidateErrors.choice(
                            errors,
                            CandidateErrors.path(loc, "sex"),
                            normalized,
                            BIOLOGICAL_SEXES,
                            "enum");
        }

        return new PatientProfileCandidate(displayName, dateOfBirth, sex);
    }

    private PatientFactCandidate patientFact(
            CandidateErrors errors, List<Object> loc, JsonNode raw) {
        ObjectNode node = requireModel(errors, loc, raw, "PatientFactCandidate");
        if (node == null) {
            return null;
        }
        int before = errors.toList().size();

        UUID candidateId = null;
        JsonNode candidateIdNode = node.get("candidate_id");
        if (candidateIdNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "candidate_id"), node);
        } else {
            candidateId =
                    CandidateErrors.uuid(
                            errors, CandidateErrors.path(loc, "candidate_id"), candidateIdNode);
        }

        boolean selected = true;
        JsonNode selectedNode = node.get("selected");
        if (selectedNode != null) {
            Boolean parsed =
                    CandidateErrors.bool(errors, CandidateErrors.path(loc, "selected"), selectedNode);
            selected = parsed == null || parsed;
        }

        FactType factType = null;
        JsonNode factTypeNode = node.get("fact_type");
        if (factTypeNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "fact_type"), node);
        } else {
            String value =
                    CandidateErrors.choice(
                            errors,
                            CandidateErrors.path(loc, "fact_type"),
                            factTypeNode,
                            FACT_TYPES,
                            "enum");
            factType = value == null ? null : FactType.fromValue(value);
        }

        String concept = null;
        JsonNode conceptNode = node.get("concept");
        if (conceptNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "concept"), node);
        } else {
            concept =
                    CandidateErrors.string(
                            errors, CandidateErrors.path(loc, "concept"), conceptNode, 1, 160);
        }

        BigDecimal valueNumeric = null;
        JsonNode valueNumericNode = node.get("value_numeric");
        if (valueNumericNode != null && !valueNumericNode.isNull()) {
            valueNumeric =
                    CandidateErrors.decimal(
                            errors, CandidateErrors.path(loc, "value_numeric"), valueNumericNode);
        }

        String valueText = optionalString(errors, loc, node, "value_text", 500);
        String unit = optionalString(errors, loc, node, "unit", 40);

        Assertion assertion = Assertion.PRESENT;
        JsonNode assertionNode = node.get("assertion");
        if (assertionNode != null) {
            String value =
                    CandidateErrors.choice(
                            errors,
                            CandidateErrors.path(loc, "assertion"),
                            assertionNode,
                            ASSERTIONS,
                            "enum");
            assertion = value == null ? null : Assertion.fromValue(value);
        }

        LocalDate effectiveDate = null;
        JsonNode effectiveDateNode = node.get("effective_date");
        if (effectiveDateNode != null && !effectiveDateNode.isNull()) {
            effectiveDate =
                    CandidateErrors.date(
                            errors, CandidateErrors.path(loc, "effective_date"), effectiveDateNode);
        }

        SourceReference source = null;
        JsonNode sourceNode = node.get("source");
        if (sourceNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "source"), node);
        } else {
            source = source(errors, CandidateErrors.path(loc, "source"), sourceNode);
        }

        List<String> warnings = warnings(errors, loc, node);

        // `numeric_requires_unit`, and like every Pydantic after-validator it only runs on an
        // otherwise valid model.
        if (errors.toList().size() == before
                && valueNumeric != null
                && (unit == null || unit.isEmpty())) {
            CandidateErrors.valueError(
                    errors, loc, "Numeric fact candidates require a unit.", node);
            return null;
        }
        if (errors.toList().size() != before) {
            return null;
        }
        return new PatientFactCandidate(
                candidateId,
                selected,
                factType,
                concept,
                valueNumeric,
                valueText,
                unit,
                assertion,
                effectiveDate,
                source,
                warnings);
    }

    // -------------------------------------------------------------------- trial

    private TrialProfileCandidate trialProfile(
            CandidateErrors errors, List<Object> loc, JsonNode raw) {
        ObjectNode node = requireModel(errors, loc, raw, "TrialProfileCandidate");
        if (node == null) {
            return null;
        }

        String title = null;
        JsonNode titleNode = node.get("title");
        if (titleNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "title"), node);
        } else {
            title =
                    CandidateErrors.string(
                            errors, CandidateErrors.path(loc, "title"), titleNode, 1, 240);
        }

        String condition = null;
        JsonNode conditionNode = node.get("condition");
        if (conditionNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "condition"), node);
        } else {
            condition =
                    CandidateErrors.string(
                            errors, CandidateErrors.path(loc, "condition"), conditionNode, 1, 160);
        }

        String phase = optionalString(errors, loc, node, "phase", 40);
        return new TrialProfileCandidate(title, condition, phase);
    }

    private TrialCriterionCandidate trialCriterion(
            CandidateErrors errors, List<Object> loc, JsonNode raw) {
        ObjectNode node = requireModel(errors, loc, raw, "TrialCriterionCandidate");
        if (node == null) {
            return null;
        }
        int before = errors.toList().size();

        UUID candidateId = null;
        JsonNode candidateIdNode = node.get("candidate_id");
        if (candidateIdNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "candidate_id"), node);
        } else {
            candidateId =
                    CandidateErrors.uuid(
                            errors, CandidateErrors.path(loc, "candidate_id"), candidateIdNode);
        }

        boolean selected = true;
        JsonNode selectedNode = node.get("selected");
        if (selectedNode != null) {
            Boolean parsed =
                    CandidateErrors.bool(errors, CandidateErrors.path(loc, "selected"), selectedNode);
            selected = parsed == null || parsed;
        }

        CriterionKind kind = null;
        JsonNode kindNode = node.get("kind");
        if (kindNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "kind"), node);
        } else {
            String value =
                    CandidateErrors.choice(
                            errors,
                            CandidateErrors.path(loc, "kind"),
                            kindNode,
                            CRITERION_KINDS,
                            "enum");
            kind = value == null ? null : CriterionKind.fromValue(value);
        }

        int order = 0;
        JsonNode orderNode = node.get("order");
        if (orderNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "order"), node);
        } else {
            Integer parsed =
                    CandidateErrors.integer(
                            errors, CandidateErrors.path(loc, "order"), orderNode, 1);
            order = parsed == null ? 0 : parsed;
        }

        String sourceText = null;
        JsonNode sourceTextNode = node.get("source_text");
        if (sourceTextNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "source_text"), node);
        } else {
            sourceText =
                    CandidateErrors.string(
                            errors,
                            CandidateErrors.path(loc, "source_text"),
                            sourceTextNode,
                            1,
                            10_000);
        }

        ObjectNode normalizedRule = null;
        JsonNode normalizedRuleNode = node.get("normalized_rule");
        if (normalizedRuleNode != null && !normalizedRuleNode.isNull()) {
            if (normalizedRuleNode.isObject()) {
                normalizedRule = (ObjectNode) normalizedRuleNode;
            } else {
                errors.add(
                        "dict_type",
                        CandidateErrors.path(loc, "normalized_rule"),
                        "Input should be a valid dictionary",
                        normalizedRuleNode);
            }
        }

        String parseState = null;
        JsonNode parseStateNode = node.get("parse_state");
        if (parseStateNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "parse_state"), node);
        } else {
            parseState =
                    CandidateErrors.choice(
                            errors,
                            CandidateErrors.path(loc, "parse_state"),
                            parseStateNode,
                            PARSE_STATES,
                            "literal_error");
        }

        SourceReference source = null;
        JsonNode sourceNode = node.get("source");
        if (sourceNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "source"), node);
        } else {
            source = source(errors, CandidateErrors.path(loc, "source"), sourceNode);
        }

        List<String> warnings = warnings(errors, loc, node);

        if (errors.toList().size() != before) {
            return null;
        }
        return new TrialCriterionCandidate(
                candidateId,
                selected,
                kind,
                order,
                sourceText,
                normalizedRule,
                parseState,
                source,
                warnings);
    }

    // ------------------------------------------------------------------- shared

    private SourceReference source(CandidateErrors errors, List<Object> loc, JsonNode raw) {
        ObjectNode node = requireModel(errors, loc, raw, "SourceReference");
        if (node == null) {
            return null;
        }

        UUID spanId = null;
        JsonNode spanIdNode = node.get("span_id");
        if (spanIdNode != null && !spanIdNode.isNull()) {
            spanId = CandidateErrors.uuid(errors, CandidateErrors.path(loc, "span_id"), spanIdNode);
        }

        int page = 0;
        JsonNode pageNode = node.get("page");
        if (pageNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "page"), node);
        } else {
            Integer parsed =
                    CandidateErrors.integer(errors, CandidateErrors.path(loc, "page"), pageNode, 1);
            page = parsed == null ? 0 : parsed;
        }

        int start = 0;
        JsonNode startNode = node.get("start");
        if (startNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "start"), node);
        } else {
            Integer parsed =
                    CandidateErrors.integer(errors, CandidateErrors.path(loc, "start"), startNode, 0);
            start = parsed == null ? 0 : parsed;
        }

        int end = 0;
        JsonNode endNode = node.get("end");
        if (endNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "end"), node);
        } else {
            Integer parsed =
                    CandidateErrors.integer(errors, CandidateErrors.path(loc, "end"), endNode, 0);
            end = parsed == null ? 0 : parsed;
        }

        String text = null;
        JsonNode textNode = node.get("text");
        if (textNode == null) {
            CandidateErrors.missing(errors, CandidateErrors.path(loc, "text"), node);
        } else {
            text =
                    CandidateErrors.string(
                            errors, CandidateErrors.path(loc, "text"), textNode, 1, 2_000);
        }

        return new SourceReference(spanId, page, start, end, text);
    }

    /** {@code warnings: list[str] = Field(default_factory=list, max_length=10)}. */
    private List<String> warnings(CandidateErrors errors, List<Object> loc, ObjectNode node) {
        JsonNode warningsNode = node.get("warnings");
        if (warningsNode == null || warningsNode.isNull()) {
            return List.of();
        }
        List<Object> warningsLoc = CandidateErrors.path(loc, "warnings");
        if (!warningsNode.isArray()) {
            errors.add("list_type", warningsLoc, "Input should be a valid list", warningsNode);
            return List.of();
        }
        List<String> values = new ArrayList<>();
        int index = 0;
        for (JsonNode item : warningsNode) {
            String value =
                    CandidateErrors.string(
                            errors, CandidateErrors.path(warningsLoc, index), item, 0, 0);
            if (value != null) {
                values.add(value);
            }
            index++;
        }
        if (warningsNode.size() > MAX_WARNINGS) {
            CandidateErrors.listTooLong(
                    errors, warningsLoc, warningsNode, MAX_WARNINGS, warningsNode.size());
        }
        return List.copyOf(values);
    }

    private String optionalString(
            CandidateErrors errors, List<Object> loc, ObjectNode node, String name, int maxLength) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        return CandidateErrors.string(errors, CandidateErrors.path(loc, name), value, 0, maxLength);
    }

    private static ObjectNode requireModel(
            CandidateErrors errors, List<Object> loc, JsonNode raw, String model) {
        if (raw == null || !raw.isObject()) {
            errors.add(
                    "model_type",
                    loc,
                    "Input should be a valid dictionary or instance of " + model,
                    raw == null
                            ? com.fasterxml.jackson.databind.node.NullNode.getInstance()
                            : raw);
            return null;
        }
        return (ObjectNode) raw;
    }
}
