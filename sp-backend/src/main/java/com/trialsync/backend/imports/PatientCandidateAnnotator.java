package com.trialsync.backend.imports;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trialsync.backend.dto.imports.PatientFactCandidate;
import com.trialsync.backend.dto.imports.PatientImportCandidates;
import org.springframework.stereotype.Component;

/**
 * Port of {@code _annotate_patient_candidates}: attaches the catalog's verdict to every patient
 * fact candidate without silently accepting a free-text concept.
 *
 * <p>This runs on analysis, on every reviewer edit and again at approval, and it is idempotent: the
 * warnings it previously wrote are stripped before new ones are computed, so a reviewer who fixes a
 * unit sees that warning disappear rather than accumulate. Warnings that did <em>not</em> come from
 * the catalog - the parser's conflicting-value notices, anything a reviewer typed - are preserved,
 * which is why {@link ImportCatalog#isCatalogWarning} exists rather than a blanket clear.
 *
 * <p>A candidate document that fails validation here is not converted into a 422. Python calls
 * {@code model_validate} outside any {@code try}, so the failure escapes as an unhandled error and
 * the caller gets a 500; that is preserved deliberately, because reaching this point with invalid
 * candidates means something upstream stored them, which is a server fault and not a client's.
 */
@Component
public class PatientCandidateAnnotator {

    /** The em dash Python used in the {@code "Catalog review: X — Y"} warnings, U+2014. */
    private static final String EM_DASH = "—";

    private static final int MAX_WARNINGS = 10;

    private final CandidateValidator validator;
    private final ImportCatalog catalog;

    public PatientCandidateAnnotator(CandidateValidator validator, ImportCatalog catalog) {
        this.validator = validator;
        this.catalog = catalog;
    }

    /**
     * The annotated candidate document, in all three forms the callers need: validated, serialised
     * and summarised.
     *
     * @param candidates the validated candidates with their warning lists rewritten
     * @param json the same thing as {@code model_dump(mode="json")} output, ready to store
     * @param warnings the document-level {@code "Catalog review: ..."} lines
     */
    public record Annotated(
            PatientImportCandidates candidates, ObjectNode json, List<String> warnings) {}

    public Annotated annotate(JsonNode raw) {
        PatientImportCandidates parsed = validator.validatePatient(raw);
        ImportCatalog.Index index = catalog.index();

        List<PatientFactCandidate> annotatedFacts = new ArrayList<>(parsed.facts().size());
        List<String> warnings = new ArrayList<>();

        for (PatientFactCandidate fact : parsed.facts()) {
            ImportCatalog.Entry entry =
                    index.match(fact.factType().value(), fact.concept());
            List<String> issues = ImportCatalog.issues(fact, entry);

            List<String> factWarnings = new ArrayList<>();
            for (String warning : fact.warnings()) {
                if (!ImportCatalog.isCatalogWarning(warning)) {
                    factWarnings.add(warning);
                }
            }
            for (String issue : issues) {
                if (!factWarnings.contains(issue)) {
                    factWarnings.add(issue);
                }
                String warning = "Catalog review: " + fact.concept() + " " + EM_DASH + " " + issue;
                if (!warnings.contains(warning)) {
                    warnings.add(warning);
                }
            }
            // The field's own `max_length=10` would otherwise reject the document on the next
            // round trip, so the list is truncated rather than allowed to overflow.
            if (factWarnings.size() > MAX_WARNINGS) {
                factWarnings = new ArrayList<>(factWarnings.subList(0, MAX_WARNINGS));
            }
            annotatedFacts.add(fact.withWarnings(List.copyOf(factWarnings)));
        }

        PatientImportCandidates annotated = parsed.withFacts(List.copyOf(annotatedFacts));
        return new Annotated(annotated, validator.toJson(annotated), List.copyOf(warnings));
    }
}
