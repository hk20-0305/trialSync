package com.trialsync.backend.imports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trialsync.backend.dto.imports.TrialCriterionCandidate;
import com.trialsync.backend.entity.enums.DocumentKind;
import com.trialsync.backend.nlp.CandidateExtraction;
import com.trialsync.backend.nlp.CandidateParser;
import org.springframework.stereotype.Component;

/**
 * Port of the candidate half of {@code trialsync.imports.parser}: the deterministic reader that
 * turns extracted document text into reviewable patient facts or trial criteria.
 *
 * <p>This is the baseline extractor. It always runs when no external provider is configured, and it
 * is also the fallback when one is configured but unreachable, so its output has to be stable and
 * its regexes have to behave exactly as Python's did. The candidates it produces are deliberately
 * conservative: anything it cannot confidently normalise is still emitted, marked for the reviewer
 * with a warning rather than dropped.
 *
 * <p>Candidates are built as mutable {@link ObjectNode}s rather than records because the API layer
 * mutates them in place - stamping span identifiers onto sources, replacing warning lists after
 * catalog review - and because doing so preserves Python's key insertion order verbatim in the
 * stored {@code candidates_json}.
 *
 * <p>It also satisfies {@link CandidateParser}, which is how the extraction providers reach both
 * this parser and the candidate contract without depending on the PDF and OCR machinery.
 */
@Component
public class RuleBasedCandidateParser implements CandidateParser {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /**
     * Python's {@code re.IGNORECASE | re.MULTILINE} over a {@code str}.
     *
     * <p>{@code UNICODE_CASE} and {@code UNICODE_CHARACTER_CLASS} restore the Unicode-aware
     * behaviour Python's {@code str} patterns have by default - without the latter Java's
     * {@code \s} and {@code \d} would only cover ASCII. {@code UNIX_LINES} narrows {@code ^} to
     * line feeds only, matching Python; extracted text has already had CR normalised away.
     */
    private static final int MULTILINE_FLAGS =
            Pattern.CASE_INSENSITIVE
                    | Pattern.UNICODE_CASE
                    | Pattern.UNICODE_CHARACTER_CLASS
                    | Pattern.MULTILINE
                    | Pattern.UNIX_LINES;

    private static final int INLINE_FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Pattern PATIENT_NAME =
            Pattern.compile("^(?:patient\\s+name|name)\\s*:\\s*(?<value>[^\\n]+)", MULTILINE_FLAGS);
    private static final Pattern PATIENT_DOB =
            Pattern.compile(
                    "^(?:date\\s+of\\s+birth|dob)\\s*:\\s*(?<value>\\d{4}-\\d{2}-\\d{2})",
                    MULTILINE_FLAGS);
    private static final Pattern PATIENT_SEX =
            Pattern.compile("^sex\\s*:\\s*(?<value>[^\\n]+)", MULTILINE_FLAGS);

    /**
     * Deliberately not anchored: laboratory values appear mid-sentence ("...with HbA1c 8.1% on
     * admission...") far more often than on a line of their own.
     */
    private static final Pattern OBSERVATION =
            Pattern.compile(
                    "(?<concept>HbA1c|eGFR|BMI|creatinine)\\s*[:=]?\\s*"
                            + "(?<value>\\d+(?:\\.\\d+)?)\\s*"
                            + "(?<unit>%|mg/dL|mL/min/1\\.73m2|kg/m2)",
                    INLINE_FLAGS);

    private static final Pattern SIMPLE_FACT =
            Pattern.compile(
                    "^(?<label>condition|diagnosis|medication)\\s*:\\s*(?<value>[^\\n]+)",
                    MULTILINE_FLAGS);

    private static final Pattern TRIAL_TITLE =
            Pattern.compile("^(?:trial\\s+title|title)\\s*:\\s*(?<value>[^\\n]+)", MULTILINE_FLAGS);
    private static final Pattern TRIAL_CONDITION =
            Pattern.compile("^condition\\s*:\\s*(?<value>[^\\n]+)", MULTILINE_FLAGS);
    private static final Pattern TRIAL_PHASE =
            Pattern.compile("^phase\\s*:\\s*(?<value>[^\\n]+)", MULTILINE_FLAGS);

    private static final Set<String> INCLUSION_HEADERS =
            Set.of("inclusion", "inclusion criteria", "eligibility criteria - inclusion");
    private static final Set<String> EXCLUSION_HEADERS =
            Set.of("exclusion", "exclusion criteria", "eligibility criteria - exclusion");

    /** Leading bullet or numbering, stripped before a line becomes a criterion. */
    private static final Pattern BULLET_PREFIX = Pattern.compile("^(?:[-*•]|\\d+[.)])\\s*");

    private static final Pattern BULLET_ONLY = Pattern.compile("^(?:[-*•]|\\d+[.)])");

    private static final Pattern AGE_BETWEEN =
            Pattern.compile(
                    "age\\s+(?:between\\s+)?(\\d+)\\s*(?:to|-|–)\\s*(\\d+)\\s*(?:years?)?",
                    INLINE_FLAGS);
    private static final Pattern AGE_MIN =
            Pattern.compile(
                    "age\\s+(\\d+)\\s*(?:years?)?\\s*(?:or older|and older|minimum)", INLINE_FLAGS);
    private static final Pattern NUMERIC_RULE =
            Pattern.compile(
                    "(HbA1c|eGFR|BMI|creatinine)\\s*(?:is\\s+)?"
                            + "(less than or equal to|greater than or equal to|no more than|"
                            + "no less than|at most|at least|less than|greater than|"
                            + "<=|>=|<|>|≤|≥)\\s*"
                            + "(\\d+(?:\\.\\d+)?)\\s*([^\\s,;]+)?",
                    INLINE_FLAGS);

    private static final Map<String, String> OPERATIONS = operations();

    private static Map<String, String> operations() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("<", "lt");
        map.put("less than", "lt");
        map.put("<=", "lte");
        map.put("≤", "lte");
        map.put("less than or equal to", "lte");
        map.put("no more than", "lte");
        map.put("at most", "lte");
        map.put(">", "gt");
        map.put("greater than", "gt");
        map.put(">=", "gte");
        map.put("≥", "gte");
        map.put("greater than or equal to", "gte");
        map.put("no less than", "gte");
        map.put("at least", "gte");
        return Map.copyOf(map);
    }

    /** Raw parser output: the candidate tree plus the document-level warnings it accumulated. */
    private final CandidateValidator validator;

    public RuleBasedCandidateParser(CandidateValidator validator) {
        this.validator = validator;
    }

    /**
     * {@code PatientImportCandidates.model_validate(candidates).model_dump(mode="json")}, or the
     * trial equivalent, chosen by document kind.
     */
    @Override
    public ObjectNode validateCandidates(DocumentKind kind, ObjectNode candidates) {
        return kind == DocumentKind.patient
                ? validator.toJson(validator.validatePatient(candidates))
                : validator.toJson(validator.validateTrial(candidates));
    }

    /** Port of {@code extract_patient_candidates}. */
    @Override
    public CandidateExtraction extractPatientCandidates(ExtractedInput extracted) {
        String text = extracted.text();
        List<ExtractedPage> pages = extracted.pages();
        List<String> warnings = new ArrayList<>();

        Matcher name = search(PATIENT_NAME, text);
        Matcher dob = search(PATIENT_DOB, text);
        Matcher sex = search(PATIENT_SEX, text);

        ObjectNode profile = NODES.objectNode();
        profile.put(
                "display_name",
                name != null ? PythonText.strip(name.group("value")) : "Imported synthetic patient");
        if (dob != null) {
            profile.put("date_of_birth", dob.group("value"));
        } else {
            profile.putNull("date_of_birth");
        }
        if (sex != null) {
            profile.put("sex", PythonText.strip(sex.group("value")));
        } else {
            profile.putNull("sex");
        }

        List<ObjectNode> facts = new ArrayList<>();
        // Insertion ordered so the conflict warnings come out in the order the concepts first
        // appeared in the document, as Python's dict iteration does.
        Map<String, Set<String>> seenValues = new LinkedHashMap<>();

        Matcher observation = OBSERVATION.matcher(text);
        while (observation.find()) {
            String concept = observation.group("concept");
            String value = observation.group("value");
            seenValues.computeIfAbsent(PythonText.lower(concept), key -> new LinkedHashSet<>())
                    .add(value);
            facts.add(
                    patientFact(
                            "observation",
                            concept,
                            observation,
                            text,
                            pages,
                            value,
                            null,
                            observation.group("unit")));
        }

        Matcher simple = SIMPLE_FACT.matcher(text);
        while (simple.find()) {
            String label = PythonText.lower(simple.group("label"));
            String value = PythonText.strip(simple.group("value"));
            facts.add(
                    patientFact(
                            "medication".equals(label) ? "medication" : "condition",
                            value,
                            simple,
                            text,
                            pages,
                            null,
                            "Present",
                            null));
        }

        // Two different readings of the same measurement cannot both be right, and picking one
        // silently would be a clinical decision. Every fact for the concept is flagged instead so
        // the reviewer resolves it.
        for (Map.Entry<String, Set<String>> entry : seenValues.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            String warning =
                    "Conflicting extracted values for "
                            + entry.getKey()
                            + "; choose the accepted candidate.";
            warnings.add(warning);
            for (ObjectNode fact : facts) {
                if (PythonText.lower(fact.path("concept").asText()).equals(entry.getKey())) {
                    ArrayNode replacement = NODES.arrayNode();
                    replacement.add(warning);
                    fact.set("warnings", replacement);
                }
            }
        }
        if (facts.isEmpty()) {
            warnings.add("No structured facts were recognized; add or edit facts during review.");
        }

        ObjectNode candidates = NODES.objectNode();
        candidates.set("profile", profile);
        ArrayNode factArray = candidates.putArray("facts");
        facts.forEach(factArray::add);
        return new CandidateExtraction(candidates, warnings);
    }

    /** Port of {@code _patient_fact}; key order is the Pydantic field order. */
    private ObjectNode patientFact(
            String factType,
            String concept,
            Matcher match,
            String text,
            List<ExtractedPage> pages,
            String valueNumeric,
            String valueText,
            String unit) {
        ObjectNode fact = NODES.objectNode();
        fact.put("candidate_id", UUID.randomUUID().toString());
        fact.put("selected", true);
        fact.put("fact_type", factType);
        fact.put("concept", PythonText.strip(concept));
        // Carried as a string all the way to the client: a decimal reading such as 7.40 must not
        // lose its trailing digit to a float round trip before the reviewer has seen it.
        putNullable(fact, "value_numeric", valueNumeric);
        putNullable(fact, "value_text", valueText);
        putNullable(fact, "unit", unit);
        fact.put("assertion", "present");
        fact.putNull("effective_date");
        fact.set(
                "source",
                source(
                        pages,
                        PythonText.codePointIndex(text, match.start()),
                        PythonText.codePointIndex(text, match.end()),
                        match.group(0)));
        fact.putArray("warnings");
        return fact;
    }

    /** Port of {@code extract_trial_candidates}. */
    @Override
    public CandidateExtraction extractTrialCandidates(ExtractedInput extracted) {
        String text = extracted.text();
        List<ExtractedPage> pages = extracted.pages();
        List<String> warnings = new ArrayList<>();

        Matcher title = search(TRIAL_TITLE, text);
        Matcher condition = search(TRIAL_CONDITION, text);
        Matcher phase = search(TRIAL_PHASE, text);

        ObjectNode profile = NODES.objectNode();
        profile.put(
                "title",
                title != null ? PythonText.strip(title.group("value")) : "Imported synthetic trial");
        profile.put(
                "condition",
                condition != null
                        ? PythonText.strip(condition.group("value"))
                        : "Synthetic condition");
        if (phase != null) {
            profile.put("phase", PythonText.strip(phase.group("value")));
        } else {
            profile.putNull("phase");
        }

        List<ObjectNode> criteria = new ArrayList<>();
        String kind = null;
        int cursor = 0;
        boolean needsManualRule = false;

        for (String line : PythonText.splitLinesKeepEnds(text)) {
            String stripped = PythonText.strip(line);
            String lower = PythonText.rstrip(PythonText.lower(stripped), ":");
            if (INCLUSION_HEADERS.contains(lower)) {
                kind = "inclusion";
            } else if (EXCLUSION_HEADERS.contains(lower)) {
                kind = "exclusion";
            } else if (kind != null && !stripped.isEmpty()) {
                String criterionText =
                        PythonText.strip(BULLET_PREFIX.matcher(stripped).replaceFirst(""));
                // A bare prose line under a heading is narrative, not a list item. Only bulleted or
                // numbered lines become criteria, otherwise every sentence of an eligibility
                // preamble would arrive in review as something to approve.
                if (!criterionText.isEmpty()
                        && criterionText.equals(stripped)
                        && !BULLET_ONLY.matcher(stripped).lookingAt()) {
                    cursor += PythonText.length(line);
                    continue;
                }
                int start = cursor + PythonText.find(line, criterionText);
                ObjectNode rule = trialRule(criterionText);
                String parseState =
                        rule != null
                                ? TrialCriterionCandidate.PARSED
                                : TrialCriterionCandidate.NEEDS_MANUAL_RULE;

                ObjectNode criterion = NODES.objectNode();
                criterion.put("candidate_id", UUID.randomUUID().toString());
                criterion.put("selected", true);
                criterion.put("kind", kind);
                criterion.put("order", criteria.size() + 1);
                criterion.put("source_text", criterionText);
                if (rule != null) {
                    criterion.set("normalized_rule", rule);
                } else {
                    criterion.putNull("normalized_rule");
                    needsManualRule = true;
                }
                criterion.put("parse_state", parseState);
                criterion.set(
                        "source",
                        source(pages, start, start + PythonText.length(criterionText), criterionText));
                ArrayNode criterionWarnings = criterion.putArray("warnings");
                if (rule == null) {
                    criterionWarnings.add("This criterion needs manual rule entry before approval.");
                }
                criteria.add(criterion);
            }
            cursor += PythonText.length(line);
        }

        if (criteria.isEmpty()) {
            warnings.add("No inclusion or exclusion list items were recognized.");
        } else if (needsManualRule) {
            warnings.add("Some criteria require manual rule entry and cannot be approved as-is.");
        }

        ObjectNode candidates = NODES.objectNode();
        candidates.set("profile", profile);
        ArrayNode criterionArray = candidates.putArray("criteria");
        criteria.forEach(criterionArray::add);
        return new CandidateExtraction(candidates, warnings);
    }

    /**
     * Port of {@code _trial_rule}: recognises the three eligibility shapes the deterministic parser
     * can normalise. Anything else returns {@code null} and the criterion is routed to manual entry
     * rather than guessed at.
     */
    private ObjectNode trialRule(String text) {
        Matcher ageBetween = AGE_BETWEEN.matcher(text);
        if (ageBetween.find()) {
            ObjectNode rule = NODES.objectNode();
            rule.put("op", "between");
            rule.put("fact", "demographic.age");
            rule.put("min", Integer.parseInt(ageBetween.group(1)));
            rule.put("max", Integer.parseInt(ageBetween.group(2)));
            rule.put("unit", "year");
            return rule;
        }
        Matcher ageMin = AGE_MIN.matcher(text);
        if (ageMin.find()) {
            ObjectNode rule = NODES.objectNode();
            rule.put("op", "gte");
            rule.put("fact", "demographic.age");
            rule.put("value", Integer.parseInt(ageMin.group(1)));
            rule.put("unit", "year");
            return rule;
        }
        Matcher numeric = NUMERIC_RULE.matcher(text);
        if (numeric.find()) {
            String concept = PythonText.lower(numeric.group(1));
            // Percent is the only unit safe to assume: HbA1c is reported that way universally,
            // whereas an eGFR or creatinine value with no unit in the source is ambiguous and is
            // left for a human.
            String unit = numeric.group(4);
            if (unit == null) {
                unit = "hba1c".equals(concept) ? "%" : null;
            }
            if (unit != null && !unit.isEmpty()) {
                ObjectNode rule = NODES.objectNode();
                rule.put("op", OPERATIONS.get(PythonText.lower(numeric.group(2))));
                rule.put("fact", "observation." + concept);
                rule.put("value", Double.parseDouble(numeric.group(3)));
                rule.put("unit", unit);
                return rule;
            }
        }
        return null;
    }

    /**
     * Port of {@code _source}: converts a document-wide offset pair into a page number and offsets
     * local to that page, which is what the reviewer's highlight and the persisted span both use.
     *
     * <p>The page whose range contains the start wins; a match that straddles a page boundary keeps
     * the page it began on and simply reports an end past that page's length, exactly as Python
     * does. A start beyond every page falls back to the first page rather than failing.
     */
    static ObjectNode source(List<ExtractedPage> pages, int start, int end, String text) {
        ExtractedPage page = pages.get(0);
        for (ExtractedPage candidate : pages) {
            if (candidate.startOffset() <= start && start <= candidate.endOffset()) {
                page = candidate;
                break;
            }
        }
        ObjectNode source = NODES.objectNode();
        source.put("page", page.page());
        source.put("start", start - page.startOffset());
        source.put("end", end - page.startOffset());
        source.put("text", PythonText.strip(text));
        return source;
    }

    private static Matcher search(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher : null;
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }
}
