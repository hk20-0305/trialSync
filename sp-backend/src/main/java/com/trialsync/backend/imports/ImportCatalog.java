package com.trialsync.backend.imports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.dto.imports.PatientFactCandidate;
import com.trialsync.backend.entity.ClinicalConcept;
import com.trialsync.backend.repository.ClinicalConceptRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The clinical catalog as the import reviewer sees it: a lookup from a free-text concept to a
 * catalog entry, and the list of reasons a candidate could not be accepted as a first-class fact.
 *
 * <p>An imported document says whatever the document said - "Type II Diabetes Mellitus", "Metformin
 * Hydrochloride", "HbA1c 8.1 %". Approval, on the other hand, may only write facts that the catalog
 * defines, because those are the concepts the screening engine can reason about. This class is the
 * bridge, and its deliberate design point is that a concept it cannot place is <em>not</em> dropped
 * and <em>not</em> invented into the catalog: it is recorded as a review-only unsupported detail,
 * still attached to the patient and still traceable to its page.
 *
 * <p>Matching is done on a compacted form of the concept - case folded, non-alphanumerics removed -
 * against the entry's key, canonical concept and display label, and then through a small table of
 * hand-curated aliases. Nothing here does fuzzy matching: a near-miss is a reviewer's decision.
 */
@Component
public class ImportCatalog {

    /**
     * Port of {@code _IMPORT_CONCEPT_ALIASES}: the spellings clinical documents actually use,
     * mapped onto catalog concepts.
     *
     * <p>Every entry is an exact synonym, not an approximation. "Gestation" is pregnancy;
     * "metformin hydrochloride" is metformin in a different salt form but the same catalog concept.
     * Anything less certain than that is left out on purpose, so the reviewer decides.
     */
    private static final Map<String, String> ALIASES = aliases();

    private static Map<String, String> aliases() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("condition|type1diabetesmellitus", "type1_diabetes");
        map.put("condition|type1diabetes", "type1_diabetes");
        map.put("condition|typeidiabetes", "type1_diabetes");
        map.put("condition|type2diabetesmellitus", "type2_diabetes");
        map.put("condition|type2diabetes", "type2_diabetes");
        map.put("condition|typeiidiabetes", "type2_diabetes");
        map.put("condition|typeiidiabetesmellitus", "type2_diabetes");
        map.put("condition|highbloodpressure", "hypertension");
        map.put("condition|highbp", "hypertension");
        map.put("condition|reactiveairwaydisease", "asthma");
        map.put("condition|gestation", "pregnancy");
        map.put("medication|metforminhydrochloride", "metformin");
        map.put("medication|atorvastatincalcium", "atorvastatin");
        map.put("medication|insulintherapy", "insulin");
        map.put("medication|semaglutideinjection", "semaglutide");
        return Map.copyOf(map);
    }

    /** {@code PatientFactInputKind.numeric}. */
    private static final String NUMERIC = "numeric";

    private final ClinicalConceptRepository repository;
    private final ObjectMapper objectMapper;

    public ImportCatalog(ClinicalConceptRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** One catalog entry, reduced to what import review needs from it. */
    public record Entry(
            String key,
            com.trialsync.backend.domain.model.FactType factType,
            String concept,
            String displayLabel,
            String inputKind,
            Set<Assertion> allowedAssertions,
            String fixedUnit,
            boolean effectiveDateRequired) {

        boolean isNumeric() {
            return NUMERIC.equals(inputKind);
        }
    }

    /**
     * A resolved snapshot of the active catalog: {@code _catalog_index(active_catalog_entries(...))}.
     *
     * <p>Taken once per request and reused, so a concept cannot resolve one way while annotating and
     * another way while approving the same import.
     */
    public static final class Index {

        private final Map<String, Entry> byCompactLabel;

        private Index(Map<String, Entry> byCompactLabel) {
            this.byCompactLabel = byCompactLabel;
        }

        /**
         * Port of {@code _matched_catalog_entry}: direct match first, then the alias table.
         *
         * @return the entry, or {@code null} when the concept is outside the catalog
         */
        public Entry match(String factType, String concept) {
            String compact = compactConcept(concept);
            Entry direct = byCompactLabel.get(factType + "|" + compact);
            if (direct != null) {
                return direct;
            }
            String alias = ALIASES.get(factType + "|" + compact);
            return alias == null ? null : byCompactLabel.get(factType + "|" + compactConcept(alias));
        }
    }

    /** Loads the active catalog and indexes it under every label an entry answers to. */
    @Transactional(readOnly = true)
    public Index index() {
        Map<String, Entry> index = new LinkedHashMap<>();
        for (ClinicalConcept record :
                repository.findByActiveTrueOrderByConceptGroupAscDisplayOrderAsc()) {
            Entry entry = toEntry(record);
            for (String label : List.of(entry.key(), entry.concept(), entry.displayLabel())) {
                // Later entries overwrite earlier ones under a shared label, matching Python's
                // plain dict assignment.
                index.put(entry.factType().value() + "|" + compactConcept(label), entry);
            }
        }
        return new Index(Map.copyOf(index));
    }

    private Entry toEntry(ClinicalConcept record) {
        Set<Assertion> allowed = new LinkedHashSet<>();
        try {
            JsonNode parsed = objectMapper.readTree(record.getAllowedAssertionsJson());
            for (JsonNode value : parsed) {
                Assertion assertion = Assertion.fromValue(value.asText());
                if (assertion != null) {
                    allowed.add(assertion);
                }
            }
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "clinical concept " + record.getKey() + " has an unreadable assertion list",
                    exception);
        }
        return new Entry(
                record.getKey(),
                record.getFactType(),
                record.getConcept(),
                record.getDisplayLabel(),
                record.getInputKind(),
                Set.copyOf(allowed),
                record.getFixedUnit(),
                record.isEffectiveDateRequired());
    }

    /**
     * Port of {@code _catalog_issues}: everything standing between this candidate and a first-class
     * fact, in the order Python appended them and with duplicates removed.
     *
     * <p>An empty result means the candidate can be written as a {@code patient_facts} row. A
     * non-empty one means it becomes an unsupported detail instead, with these strings as its
     * explanation - which is why the wording is part of the contract and not just a message.
     */
    public static List<String> issues(PatientFactCandidate fact, Entry entry) {
        if (entry == null) {
            return List.of(
                    "This concept is not in the active clinical catalog; it will be retained "
                            + "as a review-only detail.");
        }
        List<String> issues = new ArrayList<>();
        if (entry.isNumeric()) {
            if (fact.assertion() == Assertion.PRESENT && fact.valueNumeric() == null) {
                issues.add("A present numeric observation needs a measured value.");
            }
            if (fact.unit() != null
                    && !fact.unit().isEmpty()
                    && entry.fixedUnit() != null
                    && !entry.fixedUnit().isEmpty()
                    && !unitKey(fact.unit()).equals(unitKey(entry.fixedUnit()))) {
                issues.add("The catalog requires the unit " + entry.fixedUnit() + ".");
            }
            if (entry.effectiveDateRequired() && fact.effectiveDate() == null) {
                issues.add("Add an effective date before approving this observation.");
            }
        } else if (fact.valueNumeric() != null) {
            issues.add("Numeric values are not accepted for this status detail.");
        }
        if (!entry.allowedAssertions().contains(fact.assertion())) {
            issues.add("The selected assertion is not supported by this catalog entry.");
        }
        if (entry.effectiveDateRequired() && fact.effectiveDate() == null) {
            issues.add("Add an effective date before approving this detail.");
        }
        // `list(dict.fromkeys(issues))`: de-duplicated, first occurrence wins.
        return List.copyOf(new LinkedHashSet<>(issues));
    }

    /**
     * Port of {@code _is_catalog_warning}.
     *
     * <p>Used to tell the reviewer's own warnings apart from the ones this class generated, so a
     * re-annotation replaces the catalog's verdicts and leaves everything else - the parser's
     * conflict warnings, for instance - untouched.
     */
    public static boolean isCatalogWarning(String warning) {
        return warning.startsWith("This concept is not in the active clinical catalog;")
                || warning.equals("A present numeric observation needs a measured value.")
                || warning.startsWith("The catalog requires the unit ")
                || warning.equals("Add an effective date before approving this observation.")
                || warning.equals("Add an effective date before approving this detail.")
                || warning.equals("Numeric values are not accepted for this status detail.")
                || warning.equals("The selected assertion is not supported by this catalog entry.");
    }

    /** Port of {@code _compact_concept}. */
    static String compactConcept(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        PythonText.casefold(value)
                .codePoints()
                .filter(PythonText::isAlnum)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    /** Port of {@code _unit_key}: case folded with all whitespace removed. */
    static String unitKey(String value) {
        return PythonText.removeWhitespace(PythonText.casefold(value));
    }

    /** Port of {@code _unsupported_category}. */
    public static String unsupportedCategory(String factType) {
        return switch (factType) {
            case "condition", "medication", "observation" -> factType;
            default -> "other";
        };
    }
}
