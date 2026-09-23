package com.trialsync.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.dto.imports.ImportApprovalRead;
import com.trialsync.backend.dto.imports.ImportApproveRequest;
import com.trialsync.backend.dto.imports.PatientFactCandidate;
import com.trialsync.backend.dto.imports.PatientImportCandidates;
import com.trialsync.backend.dto.imports.TrialCriterionCandidate;
import com.trialsync.backend.dto.imports.TrialImportCandidates;
import com.trialsync.backend.entity.Criterion;
import com.trialsync.backend.entity.Document;
import com.trialsync.backend.entity.Patient;
import com.trialsync.backend.entity.PatientFact;
import com.trialsync.backend.entity.PatientUnsupportedDetail;
import com.trialsync.backend.entity.Trial;
import com.trialsync.backend.entity.TrialVersion;
import com.trialsync.backend.entity.enums.DocumentKind;
import com.trialsync.backend.entity.enums.DocumentStatus;
import com.trialsync.backend.entity.enums.VersionStatus;
import com.trialsync.backend.imports.ImportCatalog;
import com.trialsync.backend.imports.PatientCandidateAnnotator;
import com.trialsync.backend.repository.CriterionRepository;
import com.trialsync.backend.repository.DocumentRepository;
import com.trialsync.backend.repository.PatientFactRepository;
import com.trialsync.backend.repository.PatientRepository;
import com.trialsync.backend.repository.PatientUnsupportedDetailRepository;
import com.trialsync.backend.repository.TrialRepository;
import com.trialsync.backend.repository.TrialVersionRepository;
import com.trialsync.backend.security.SecurityContext;

/**
 * Port of {@code approve_import()} from {@code trialsync.api.imports}: the one place an import stops
 * being a proposal and becomes a record.
 *
 * <p>It is the whole point of the review workflow, and three properties of it are deliberate.
 *
 * <p><b>Only selected candidates are written.</b> A candidate a reviewer did not tick is left in the
 * document and never reaches the database, so extraction recall costs nothing.
 *
 * <p><b>Nothing outside the catalog is invented, and nothing is silently dropped.</b> A patient fact
 * whose concept the catalog cannot place, or which the catalog can place but not accept - wrong
 * unit, missing measurement, missing date, disallowed assertion - becomes a
 * {@code patient_unsupported_details} row carrying the reasons and the page it came from. It is
 * visible to a human and invisible to the screening engine, which is the only honest answer for a
 * detail the engine cannot reason about.
 *
 * <p><b>A trial criterion is never approved on a guess.</b> Every selected criterion must be
 * {@code parsed} with a rule attached, or approval fails as a whole.
 *
 * <p>The state machine is single-shot in both directions: approval requires
 * {@code needs_review} and the guard is the same one rejection uses, so an approved or rejected
 * import cannot be approved again and a second concurrent attempt cannot duplicate the patient.
 */
@Service
public class ImportApprovalService {

    /** {@code PatientFactInputKind.numeric}. */
    private static final String NUMERIC_INPUT_KIND = "numeric";

    /** {@code context[:500]} on the unsupported detail. */
    private static final int MAXIMUM_CONTEXT = 500;

    private final ImportService importService;
    private final DocumentRepository documents;
    private final PatientRepository patients;
    private final PatientFactRepository patientFacts;
    private final PatientUnsupportedDetailRepository unsupportedDetails;
    private final TrialRepository trials;
    private final TrialVersionRepository trialVersions;
    private final CriterionRepository criteria;
    private final PatientChangeEventRecorder changeEvents;
    private final PatientCandidateAnnotator annotator;
    private final ImportCatalog catalog;

    public ImportApprovalService(
            ImportService importService,
            DocumentRepository documents,
            PatientRepository patients,
            PatientFactRepository patientFacts,
            PatientUnsupportedDetailRepository unsupportedDetails,
            TrialRepository trials,
            TrialVersionRepository trialVersions,
            CriterionRepository criteria,
            PatientChangeEventRecorder changeEvents,
            PatientCandidateAnnotator annotator,
            ImportCatalog catalog) {
        this.importService = importService;
        this.documents = documents;
        this.patients = patients;
        this.patientFacts = patientFacts;
        this.unsupportedDetails = unsupportedDetails;
        this.trials = trials;
        this.trialVersions = trialVersions;
        this.criteria = criteria;
        this.changeEvents = changeEvents;
        this.annotator = annotator;
        this.catalog = catalog;
    }

    /**
     * Port of {@code approve_import()}.
     *
     * <p>Everything runs in one transaction. Python wraps the write half in a {@code try} that rolls
     * back on any failure, including the {@code ApplicationError}s raised inside it; the
     * {@code @Transactional} boundary does the same, which matters because the re-annotation above it
     * has already modified the document. A rejected approval therefore leaves the import exactly as
     * the reviewer left it.
     */
    @Transactional
    public ImportApprovalRead approve(UUID importId, ImportApproveRequest request) {
        Document document = importService.ownedImport(importId);
        if (document.getStatus() != DocumentStatus.needs_review) {
            throw ApplicationError.conflict(
                    "IMPORT_ALREADY_REVIEWED", "This import has already been reviewed.");
        }
        UUID ownerId = SecurityContext.require().getId();

        ObjectNode candidates =
                importService.validateCandidates(
                        document.getKind(), importService.readJson(document.getCandidatesJson()));
        if (document.getKind() == DocumentKind.patient) {
            // Approval re-runs the catalog review rather than trusting what was stored: the catalog
            // may have been edited since the reviewer last saw this document, and an entry retired in
            // the meantime has to demote its facts here rather than write them.
            PatientCandidateAnnotator.Annotated annotated = annotator.annotate(candidates);
            candidates = annotated.json();
            document.setCandidatesJson(importService.writeJson(candidates));
            document.setWarningsJson(
                    importService.writeWarnings(
                            importService.mergeCatalogWarnings(document, annotated.warnings())));
        }

        UUID resourceId =
                document.getKind() == DocumentKind.patient
                        ? approvePatient(candidates, ownerId, request)
                        : approveTrial(document, candidates, ownerId);

        document.setStatus(DocumentStatus.approved);
        document.setApprovedResourceId(resourceId);
        documents.flush();
        return new ImportApprovalRead(document.getKind(), resourceId, document.getId());
    }

    // --------------------------------------------------------------- patient

    /**
     * Writes the patient, its activity trail, its catalog-backed facts and its review-only details.
     *
     * <p>{@code model_validate} is called here without the {@code IMPORT_REVIEW_INVALID} wrapper,
     * matching Python: these candidates were validated and re-serialised moments ago, so a failure
     * now is a server fault rather than a client's and is answered as a 500.
     */
    private UUID approvePatient(
            ObjectNode candidates, UUID ownerId, ImportApproveRequest request) {
        PatientImportCandidates parsed = importService.validatePatientCandidates(candidates);

        List<Patient> existing =
                patients.findByOwnerIdAndLoweredDisplayName(
                        ownerId,
                        parsed.profile().displayName().strip().toLowerCase(Locale.ROOT));
        Patient duplicate = existing.isEmpty() ? null : existing.get(0);
        if (duplicate != null && !request.confirmDuplicateNameOrDefault()) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("patient_id", duplicate.getId().toString());
            detail.put("display_name", duplicate.getDisplayName());
            throw new ApplicationError(
                    "PATIENT_NAME_REVIEW_REQUIRED",
                    "A patient with this name already exists. Review it or confirm a "
                            + "distinct synthetic person.",
                    409,
                    null,
                    List.of(detail));
        }

        Patient patient =
                new Patient(ownerId, syntheticExternalId(), parsed.profile().displayName());
        patient.setDateOfBirth(parsed.profile().dateOfBirth());
        patient.setSex(parsed.profile().sex());
        patient = patients.saveAndFlush(patient);

        // Resolved once and reused for every fact, so a concept cannot be accepted for one candidate
        // and refused for the next within the same approval.
        ImportCatalog.Index index = catalog.index();
        changeEvents.record(
                patient.getId(),
                ownerId,
                "patient_created",
                "patient",
                patient.getId(),
                null,
                PatientChangeEventRecorder.profilePayload(patient),
                null);

        for (PatientFactCandidate fact : parsed.facts()) {
            if (!fact.selected()) {
                continue;
            }
            ImportCatalog.Entry entry = index.match(fact.factType().value(), fact.concept());
            List<String> issues = ImportCatalog.issues(fact, entry);
            String sourceLabel = "Imported document p." + fact.source().page();
            if (entry == null || !issues.isEmpty()) {
                unsupportedDetails.save(unsupportedImportDetail(patient.getId(), fact, issues));
                continue;
            }
            // A non-numeric entry stores no unit at all, and a numeric one stores the catalog's
            // canonical unit rather than the document's spelling of it.
            String canonicalUnit =
                    NUMERIC_INPUT_KIND.equals(entry.inputKind()) ? entry.fixedUnit() : null;
            PatientFact saved = new PatientFact(patient.getId(), entry.factType(), entry.concept());
            saved.setValueNumeric(fact.valueNumeric());
            saved.setValueText(null);
            saved.setUnit(canonicalUnit);
            saved.setAssertion(fact.assertion());
            saved.setEffectiveDate(fact.effectiveDate());
            saved.setSourceLabel(sourceLabel);
            saved = patientFacts.saveAndFlush(saved);
            changeEvents.record(
                    patient.getId(),
                    ownerId,
                    "fact_created",
                    "fact",
                    saved.getId(),
                    null,
                    factEventPayload(
                            saved.getId(),
                            entry.factType().value(),
                            entry.concept(),
                            fact.assertion().value(),
                            fact.valueNumeric(),
                            canonicalUnit,
                            fact.effectiveDate() == null ? null : fact.effectiveDate().toString(),
                            sourceLabel),
                    null);
        }
        return patient.getId();
    }

    /**
     * Port of {@code _fact_event_payload()}.
     *
     * <p>Deliberately not {@link PatientChangeEventRecorder#factPayload}: the import trail carries
     * eight keys in this order and omits {@code value_text}, {@code voided_at} and
     * {@code void_reason}, which the manual-entry payload includes. The trail is append-only and
     * already holds entries in this shape, so the difference is preserved rather than unified.
     */
    private static Map<String, Object> factEventPayload(
            UUID factId,
            String factType,
            String concept,
            String assertion,
            BigDecimal valueNumeric,
            String unit,
            String effectiveDate,
            String sourceLabel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", factId.toString());
        payload.put("fact_type", factType);
        payload.put("concept", concept);
        payload.put("assertion", assertion);
        // `str(value_numeric)`: the scale the reviewer confirmed, not the column's.
        payload.put("value_numeric", valueNumeric == null ? null : valueNumeric.toPlainString());
        payload.put("unit", unit);
        payload.put("effective_date", effectiveDate);
        payload.put("source_label", sourceLabel);
        return payload;
    }

    /**
     * Port of {@code _unsupported_import_detail()}: the candidate the catalog could not accept, kept
     * as a review item with its page and the reasons it was demoted.
     */
    private static PatientUnsupportedDetail unsupportedImportDetail(
            UUID patientId, PatientFactCandidate fact, List<String> issues) {
        String sourceLabel = "Imported document p." + fact.source().page();
        String context =
                sourceLabel + ": " + fact.source().text() + ". " + String.join(" ", issues);
        PatientUnsupportedDetail detail =
                new PatientUnsupportedDetail(
                        patientId,
                        ImportCatalog.unsupportedCategory(fact.factType().value()),
                        fact.concept());
        detail.setContext(truncate(context, MAXIMUM_CONTEXT));
        detail.setSourceLabel(sourceLabel);
        return detail;
    }

    // ----------------------------------------------------------------- trial

    /**
     * Writes the trial, its first draft version and the selected criteria.
     *
     * <p>The version is a {@code draft}, never {@code approved}: an imported protocol still has to
     * pass the trial-version approval gate before a screening may cite it, so import approval cannot
     * be used as a shortcut around it.
     */
    private UUID approveTrial(Document document, ObjectNode candidates, UUID ownerId) {
        TrialImportCandidates parsed = importService.validateTrialCandidates(candidates);
        List<TrialCriterionCandidate> selected = new ArrayList<>();
        for (TrialCriterionCandidate criterion : parsed.criteria()) {
            if (criterion.selected()) {
                selected.add(criterion);
            }
        }
        for (TrialCriterionCandidate criterion : selected) {
            if (!TrialCriterionCandidate.PARSED.equals(criterion.parseState())
                    || criterion.normalizedRule() == null) {
                throw ApplicationError.unprocessable(
                        "IMPORT_REVIEW_INCOMPLETE",
                        "Selected criteria need valid manual rules before approval.",
                        null);
            }
        }

        Trial trial =
                new Trial(
                        ownerId,
                        syntheticRegistryId(),
                        parsed.profile().title(),
                        parsed.profile().condition());
        trial.setPhase(parsed.profile().phase());
        trial = trials.saveAndFlush(trial);

        TrialVersion version = new TrialVersion(trial.getId(), 1);
        version.setStatus(VersionStatus.draft);
        // The whole extracted document, so the approved protocol keeps the text its criteria were
        // read from.
        version.setSourceText(document.getSourceText());
        version = trialVersions.saveAndFlush(version);

        int order = 0;
        for (TrialCriterionCandidate candidate : selected) {
            // `enumerate(selected, 1)`: the stored position is the reviewer's ordering of the
            // selected criteria, not the candidate's own `order` field.
            order++;
            Criterion criterion =
                    new Criterion(
                            version.getId(),
                            candidate.kind(),
                            order,
                            candidate.sourceText());
            criterion.setNormalizedRule(importService.writeJson(candidate.normalizedRule()));
            criterion.setRequired(true);
            criteria.save(criterion);
        }
        criteria.flush();
        return trial.getId();
    }

    // --------------------------------------------------------------- helpers

    /** {@code f"SYN-{uuid.uuid4().hex[:10].upper()}"}. */
    private static String syntheticExternalId() {
        return "SYN-" + shortHex();
    }

    /** {@code f"SYN-TRIAL-{uuid.uuid4().hex[:10].upper()}"}. */
    private static String syntheticRegistryId() {
        return "SYN-TRIAL-" + shortHex();
    }

    private static String shortHex() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10)
                .toUpperCase(Locale.ROOT);
    }

    /** {@code value[:limit]}, counted in code points so an astral character is never split. */
    private static String truncate(String value, int limit) {
        if (value.codePointCount(0, value.length()) <= limit) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, limit));
    }
}
