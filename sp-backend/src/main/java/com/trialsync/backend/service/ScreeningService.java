package com.trialsync.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.domain.engine.ScreeningEngine;
import com.trialsync.backend.domain.model.ApprovedTrialVersion;
import com.trialsync.backend.domain.model.CriterionResult;
import com.trialsync.backend.domain.model.EvidenceReference;
import com.trialsync.backend.domain.model.MissingRequirement;
import com.trialsync.backend.domain.model.ScreeningContext;
import com.trialsync.backend.domain.model.ScreeningResult;
import com.trialsync.backend.dto.screening.CriterionEvaluationResponse;
import com.trialsync.backend.dto.screening.PatientSnapshotSummary;
import com.trialsync.backend.dto.screening.ScreeningCountsResponse;
import com.trialsync.backend.dto.screening.ScreeningCreateRequest;
import com.trialsync.backend.dto.screening.ScreeningResponse;
import com.trialsync.backend.dto.screening.TrialVersionSummary;
import com.trialsync.backend.entity.Criterion;
import com.trialsync.backend.entity.Patient;
import com.trialsync.backend.entity.Screening;
import com.trialsync.backend.entity.ScreeningBatch;
import com.trialsync.backend.entity.TrialVersion;
import com.trialsync.backend.entity.User;
import com.trialsync.backend.entity.enums.VersionStatus;
import com.trialsync.backend.repository.CriterionEvaluationRepository;
import com.trialsync.backend.repository.PatientRepository;
import com.trialsync.backend.repository.PatientSnapshotRepository;
import com.trialsync.backend.repository.ScreeningRepository;
import com.trialsync.backend.repository.TrialVersionRepository;
import com.trialsync.backend.security.SecurityContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the deterministic engine and stores what it produced. Port of
 * {@code trialsync.screening.service.run_and_store} together with the screening endpoints of
 * {@code trialsync.api.screenings}.
 *
 * <p>The eligibility decision belongs to {@link ScreeningEngine} alone. This class prepares the
 * engine's inputs from immutable rows, writes the engine's output down verbatim, and reads it back;
 * it never inspects a verdict in order to change one, and it never adds evidence the engine did not
 * emit.
 */
@Service
public class ScreeningService {

    private static final TypeReference<List<Map<String, Object>>> JSON_ARRAY =
            new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

    private static final String SYNTHETIC_PATIENT = "Synthetic patient";

    private final PatientRepository patients;
    private final PatientSnapshotRepository patientSnapshots;
    private final TrialVersionRepository trialVersions;
    private final ScreeningRepository screenings;
    private final CriterionEvaluationRepository criterionEvaluations;
    private final SnapshotService snapshotService;
    private final ObjectMapper objectMapper;

    public ScreeningService(
            PatientRepository patients,
            PatientSnapshotRepository patientSnapshots,
            TrialVersionRepository trialVersions,
            ScreeningRepository screenings,
            CriterionEvaluationRepository criterionEvaluations,
            SnapshotService snapshotService,
            ObjectMapper objectMapper) {
        this.patients = patients;
        this.patientSnapshots = patientSnapshots;
        this.trialVersions = trialVersions;
        this.screenings = screenings;
        this.criterionEvaluations = criterionEvaluations;
        this.snapshotService = snapshotService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------ endpoints

    /**
     * Port of {@code create_screening}, minus the response read.
     *
     * <p>Python wraps the whole body in {@code try/commit/except: rollback; raise}, so a failure
     * anywhere - an unknown patient, an unapproved version, a database error - leaves no snapshot,
     * no screening and no evaluations behind. Here the transaction boundary is this method:
     * {@link ApplicationError} is unchecked, so it rolls the same work back.
     *
     * <p>The identifier is returned rather than a response body because Python re-reads the screening
     * <em>after</em> committing. The controller performs that read in a second transaction, which is
     * what makes the freshly written snapshot and evaluation rows visible as loaded associations.
     */
    @Transactional
    public UUID createScreening(ScreeningCreateRequest request) {
        User user = SecurityContext.require();
        Patient patient = ownedPatient(user.getId(), request.patientId());
        TrialVersion version = ownedApprovedVersion(user.getId(), request.trialVersionId());
        com.trialsync.backend.entity.PatientSnapshot snapshot =
                snapshotService.snapshotForPatient(patient);
        LocalDate screeningDate =
                request.screeningDate() == null ? LocalDate.now() : request.screeningDate();
        Screening screening =
                runAndStore(user.getId(), snapshot, version, screeningDate, null);
        return screening.getId();
    }

    /** Port of {@code get_screening}. */
    @Transactional(readOnly = true)
    public ScreeningResponse getScreening(UUID screeningId) {
        User user = SecurityContext.require();
        return toResponse(ownedScreening(user.getId(), screeningId));
    }

    /** Port of {@code list_screenings}: newest first, capped at 100. */
    @Transactional(readOnly = true)
    public List<ScreeningResponse> listScreenings() {
        User user = SecurityContext.require();
        List<ScreeningResponse> responses = new ArrayList<>();
        for (Screening screening :
                screenings.findTop100ByOwnerIdOrderByCreatedAtDesc(user.getId())) {
            responses.add(toResponse(screening));
        }
        return responses;
    }

    // ------------------------------------------------------------------ engine + persistence

    /**
     * Port of {@code run_and_store}.
     *
     * <p>Everything written here comes from the {@link ScreeningResult} the engine returned: the
     * overall state, the per-criterion result, truth value and reason code, the canonical
     * explanation, and the three evidence collections with their units and provenance intact. The
     * only values sourced elsewhere are the trial labels copied onto the screening row and the
     * criterion source text, which Python also takes from the version's criteria.
     *
     * <p>The screening row is flushed before its evaluations because they carry its identifier, and
     * the evaluations are flushed before returning so a later failure in the same transaction still
     * rolls the complete unit back.
     */
    public Screening runAndStore(
            UUID ownerId,
            com.trialsync.backend.entity.PatientSnapshot snapshot,
            TrialVersion version,
            LocalDate screeningDate,
            ScreeningBatch batch) {
        ScreeningResult result =
                ScreeningEngine.screen(
                        snapshotService.toDomain(snapshot),
                        domainTrial(version),
                        // Python passes only the date and the engine version; the remaining stamps
                        // fall back to the context defaults.
                        new ScreeningContext(
                                screeningDate, SnapshotService.ENGINE_VERSION, null, null));

        Screening screening = new Screening(ownerId, snapshot.getId(), version.getId());
        screening.setBatchId(batch == null ? null : batch.getId());
        screening.setTrialRegistryId(version.getTrial().getRegistryId());
        screening.setTrialTitle(version.getTrial().getTitle());
        screening.setTrialVersionNumber(version.getVersion());
        screening.setOverallState(result.overallState());
        screening.setScreeningDate(result.screeningDate());
        screening.setEngineVersion(result.engineVersion());
        screening.setDslVersion(result.dslVersion());
        screening.setTerminologyVersion(result.terminologyVersion());
        screening.setUnitVersion(result.unitVersion());
        screenings.saveAndFlush(screening);

        Map<String, String> sourceTextById = new HashMap<>();
        for (Criterion criterion : version.getCriteria()) {
            sourceTextById.put(criterion.getId().toString(), criterion.getSourceText());
        }

        List<com.trialsync.backend.entity.CriterionEvaluation> rows = new ArrayList<>();
        for (com.trialsync.backend.domain.model.CriterionEvaluation evaluation :
                result.evaluations()) {
            String sourceText = sourceTextById.get(evaluation.criterionId());
            if (sourceText == null) {
                // Python indexes the dict directly, so an evaluation for a criterion outside the
                // version would raise there too. It cannot happen: the engine only reports on the
                // criteria it was given.
                throw new IllegalStateException(
                        "Engine reported an evaluation for an unknown criterion: "
                                + evaluation.criterionId());
            }
            com.trialsync.backend.entity.CriterionEvaluation row =
                    new com.trialsync.backend.entity.CriterionEvaluation(
                            screening.getId(),
                            UUID.fromString(evaluation.criterionId()),
                            evaluation.criterionOrder());
            row.setCriterionKind(evaluation.criterionKind());
            row.setCriterionSourceText(sourceText);
            row.setResult(evaluation.result());
            row.setTruth(evaluation.truth().value());
            row.setReasonCode(evaluation.reasonCode().value());
            row.setCanonicalExplanation(evaluation.explanation());
            row.setEvidenceJson(writeJson(evidencePayload(evaluation.evidence())));
            row.setRejectedEvidenceJson(writeJson(evidencePayload(evaluation.rejectedEvidence())));
            row.setMissingInformationJson(writeJson(missingPayload(evaluation.missing())));
            rows.add(row);
        }
        criterionEvaluations.saveAllAndFlush(rows);
        return screening;
    }

    /**
     * Port of {@code _domain_trial}.
     *
     * <p>A criterion with no normalized rule is handed to the engine as {@code {"op":"unsupported"}},
     * which is how an un-normalizable criterion becomes an honest {@code unknown} instead of being
     * quietly skipped. Python's {@code or} treats an empty rule the same way, so an empty object
     * takes the same path here.
     */
    public ApprovedTrialVersion domainTrial(TrialVersion version) {
        List<com.trialsync.backend.domain.model.Criterion> criteria = new ArrayList<>();
        for (Criterion criterion : version.getCriteria()) {
            criteria.add(
                    new com.trialsync.backend.domain.model.Criterion(
                            criterion.getId().toString(),
                            criterion.getKind(),
                            criterion.getOrder(),
                            criterion.getSourceText(),
                            expression(criterion.getNormalizedRule()),
                            criterion.isRequired()));
        }
        return new ApprovedTrialVersion(
                version.getId().toString(), String.valueOf(version.getVersion()), criteria);
    }

    private Map<String, Object> expression(String normalizedRule) {
        if (normalizedRule == null || normalizedRule.isBlank()) {
            return Map.of("op", "unsupported");
        }
        Map<String, Object> parsed;
        try {
            parsed = objectMapper.readValue(normalizedRule, JSON_OBJECT);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Stored normalized rule could not be parsed", exception);
        }
        if (parsed == null || parsed.isEmpty()) {
            return Map.of("op", "unsupported");
        }
        return parsed;
    }

    /** Port of {@code _evidence_payload}: the engine's evidence, unchanged, in Python's key order. */
    private static List<Map<String, Object>> evidencePayload(List<EvidenceReference> items) {
        List<Map<String, Object>> payload = new ArrayList<>(items.size());
        for (EvidenceReference item : items) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("fact_id", item.factId());
            entry.put("source_label", item.sourceLabel());
            entry.put("value", item.value());
            entry.put("unit", item.unit());
            entry.put(
                    "effective_date",
                    item.effectiveDate() == null ? null : item.effectiveDate().toString());
            payload.add(entry);
        }
        return payload;
    }

    /** Port of {@code _missing_payload}. */
    private static List<Map<String, Object>> missingPayload(List<MissingRequirement> items) {
        List<Map<String, Object>> payload = new ArrayList<>(items.size());
        for (MissingRequirement item : items) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("fact", item.fact());
            entry.put("reason", item.reason().value());
            entry.put("detail", item.detail());
            payload.add(entry);
        }
        return payload;
    }

    // ------------------------------------------------------------------ ownership guards

    /** Port of {@code owned_patient}. */
    public Patient ownedPatient(UUID ownerId, UUID patientId) {
        return patients
                .findByIdAndOwnerId(patientId, ownerId)
                .orElseThrow(
                        () -> ApplicationError.notFound("PATIENT_NOT_FOUND", "Patient was not found."));
    }

    /** Port of {@code owned_snapshot}. */
    public com.trialsync.backend.entity.PatientSnapshot ownedSnapshot(
            UUID ownerId, UUID snapshotId) {
        return patientSnapshots
                .findByIdAndOwnerId(snapshotId, ownerId)
                .orElseThrow(
                        () ->
                                ApplicationError.notFound(
                                        "PATIENT_SNAPSHOT_NOT_FOUND",
                                        "Patient snapshot was not found."));
    }

    /**
     * Port of {@code owned_approved_version}: the version must exist, be approved, and belong to a
     * trial the caller owns. Anything else is a 404, never a 403.
     */
    public TrialVersion ownedApprovedVersion(UUID ownerId, UUID versionId) {
        return trialVersions
                .findByIdAndStatusAndTrial_OwnerId(versionId, VersionStatus.approved, ownerId)
                .orElseThrow(
                        () ->
                                ApplicationError.notFound(
                                        "APPROVED_TRIAL_VERSION_NOT_FOUND",
                                        "Approved trial version was not found."));
    }

    /** Port of {@code _owned_screening}. */
    public Screening ownedScreening(UUID ownerId, UUID screeningId) {
        return screenings
                .findByIdAndOwnerId(screeningId, ownerId)
                .orElseThrow(
                        () ->
                                ApplicationError.notFound(
                                        "SCREENING_NOT_FOUND", "Screening was not found."));
    }

    // ------------------------------------------------------------------ response mapping

    /** Port of {@code _screening_read}. */
    public ScreeningResponse toResponse(Screening screening) {
        List<CriterionEvaluationResponse> evaluations = new ArrayList<>();
        for (com.trialsync.backend.entity.CriterionEvaluation item : screening.getEvaluations()) {
            evaluations.add(
                    new CriterionEvaluationResponse(
                            item.getId(),
                            item.getCriterionId(),
                            item.getCriterionOrder(),
                            item.getCriterionKind(),
                            item.getResult().value(),
                            item.getTruth(),
                            item.getReasonCode(),
                            item.getCriterionSourceText(),
                            item.getCanonicalExplanation(),
                            readJsonArray(item.getEvidenceJson()),
                            readJsonArray(item.getRejectedEvidenceJson()),
                            readJsonArray(item.getMissingInformationJson())));
        }
        return new ScreeningResponse(
                screening.getId(),
                screening.getBatchId(),
                screening.getPatientSnapshotId(),
                screening.getTrialVersionId(),
                snapshotSummary(screening),
                trialVersionSummary(screening),
                screening.getOverallState().value(),
                screening.getScreeningDate(),
                screening.getEngineVersion(),
                screening.getDslVersion(),
                screening.getTerminologyVersion(),
                screening.getUnitVersion(),
                screening.getCreatedAt(),
                counts(screening),
                evaluations);
    }

    /**
     * Port of {@code _snapshot_summary}.
     *
     * <p>The labels come from the snapshot's frozen source summary, with Python's
     * {@code dict.get(key, default)} semantics preserved: a missing key falls back to
     * {@code "Synthetic patient"}, and a sex that was never recorded stays null rather than becoming
     * the string {@code "None"}.
     */
    public PatientSnapshotSummary snapshotSummary(Screening screening) {
        com.trialsync.backend.entity.PatientSnapshot snapshot = resolveSnapshot(screening);
        Map<String, Object> source = snapshotService.readSource(snapshot);
        Object externalId =
                source.containsKey("external_id") ? source.get("external_id") : SYNTHETIC_PATIENT;
        Object displayName =
                source.containsKey("display_name") ? source.get("display_name") : SYNTHETIC_PATIENT;
        Object sex = source.get("sex");
        return new PatientSnapshotSummary(
                snapshot.getId(),
                String.valueOf(externalId),
                String.valueOf(displayName),
                snapshot.getDateOfBirth(),
                sex == null ? null : String.valueOf(sex),
                snapshotService.readFacts(snapshot));
    }

    /** Port of the inline {@code TrialVersionSummary(...)} built from the screening's copied labels. */
    public TrialVersionSummary trialVersionSummary(Screening screening) {
        return new TrialVersionSummary(
                screening.getTrialRegistryId(),
                screening.getTrialTitle(),
                screening.getTrialVersionNumber());
    }

    /** Port of {@code _counts}: tallies the stored evaluations, it does not re-run the engine. */
    public ScreeningCountsResponse counts(Screening screening) {
        int passCount = 0;
        int failCount = 0;
        int unknownCount = 0;
        for (com.trialsync.backend.entity.CriterionEvaluation item : screening.getEvaluations()) {
            CriterionResult result = item.getResult();
            if (result == CriterionResult.PASS) {
                passCount++;
            } else if (result == CriterionResult.FAIL) {
                failCount++;
            } else if (result == CriterionResult.UNKNOWN) {
                unknownCount++;
            }
        }
        return new ScreeningCountsResponse(passCount, failCount, unknownCount);
    }

    /**
     * The screening's snapshot.
     *
     * <p>{@code Screening.patientSnapshot} is mapped read-only, so it is populated when the row is
     * loaded but stays null on an instance this service has just constructed. Falling back to the
     * foreign key keeps the mapping usable in both cases instead of depending on how the caller
     * obtained the screening.
     */
    public com.trialsync.backend.entity.PatientSnapshot resolveSnapshot(Screening screening) {
        com.trialsync.backend.entity.PatientSnapshot snapshot = screening.getPatientSnapshot();
        if (snapshot != null) {
            return snapshot;
        }
        return patientSnapshots
                .findById(screening.getPatientSnapshotId())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Screening "
                                                + screening.getId()
                                                + " references a missing patient snapshot"));
    }

    /** Parses one of the stored evidence columns back into the list the API echoes. */
    public List<Map<String, Object>> readJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> parsed = objectMapper.readValue(json, JSON_ARRAY);
            return parsed == null ? List.of() : parsed;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored evaluation payload could not be parsed", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Evaluation payload could not be serialised", exception);
        }
    }
}
