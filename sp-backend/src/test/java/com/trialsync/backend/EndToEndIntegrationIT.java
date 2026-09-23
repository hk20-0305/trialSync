package com.trialsync.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.dto.auth.TokenResponse;
import com.trialsync.backend.dto.auth.UserCreateRequest;
import com.trialsync.backend.dto.patient.FactResponse;
import com.trialsync.backend.dto.patient.PatientChangeEventResponse;
import com.trialsync.backend.dto.patient.PatientCreateRequest;
import com.trialsync.backend.dto.patient.PatientFactCreateRequest;
import com.trialsync.backend.dto.patient.PatientResponse;
import com.trialsync.backend.dto.patient.PatientUpdateRequest;
import com.trialsync.backend.dto.patient.UnsupportedDetailCreateRequest;
import com.trialsync.backend.dto.patient.UnsupportedDetailResponse;
import com.trialsync.backend.dto.screening.BatchCreateRequest;
import com.trialsync.backend.dto.screening.ScreeningBatchResponse;
import com.trialsync.backend.dto.screening.ScreeningCreateRequest;
import com.trialsync.backend.dto.screening.ScreeningResponse;
import com.trialsync.backend.dto.trial.CriterionCreateRequest;
import com.trialsync.backend.dto.trial.CriterionRead;
import com.trialsync.backend.dto.trial.TrialCreateRequest;
import com.trialsync.backend.dto.trial.TrialRead;
import com.trialsync.backend.dto.trial.VersionCreateRequest;
import com.trialsync.backend.dto.trial.VersionRead;
import com.trialsync.backend.entity.Document;
import com.trialsync.backend.entity.DocumentSpan;
import com.trialsync.backend.entity.ScreeningChatMessage;
import com.trialsync.backend.entity.User;
import com.trialsync.backend.entity.enums.DocumentKind;
import com.trialsync.backend.entity.enums.DocumentSourceType;
import com.trialsync.backend.entity.enums.DocumentStatus;
import com.trialsync.backend.entity.enums.VersionStatus;
import com.trialsync.backend.repository.DocumentRepository;
import com.trialsync.backend.repository.ScreeningChatMessageRepository;
import com.trialsync.backend.repository.UserRepository;
import com.trialsync.backend.security.SecurityContext;
import com.trialsync.backend.service.AuthService;
import com.trialsync.backend.service.CriterionService;
import com.trialsync.backend.service.PatientFactService;
import com.trialsync.backend.service.PatientService;
import com.trialsync.backend.service.ScreeningBatchService;
import com.trialsync.backend.service.ScreeningService;
import com.trialsync.backend.service.TrialService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EndToEndIntegrationIT extends BaseIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private PatientService patientService;
    @Autowired private PatientFactService patientFactService;
    @Autowired private TrialService trialService;
    @Autowired private CriterionService criterionService;
    @Autowired private ScreeningService screeningService;
    @Autowired private ScreeningBatchService batchService;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private ScreeningChatMessageRepository chatRepository;
    @Autowired private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    void setUp() {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        TokenResponse tokenResponse = authService.register(new UserCreateRequest(email, "Dr. Alice", "Password123!"));
        testUser = userRepository.findById(tokenResponse.user().id()).orElseThrow();
        SecurityContext.setForTesting(testUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    @Test
    void testFullEndToEndPatientTrialScreeningFlow() throws Exception {
        // -------------------------------------------------------------
        // STEP 4: PATIENT FLOW
        // -------------------------------------------------------------
        String externalId = "EXT-" + UUID.randomUUID().toString().substring(0, 8);
        PatientResponse patient = patientService.create(new PatientCreateRequest(
                externalId, "John Doe", LocalDate.of(1980, 5, 12), "male", false));
        final UUID patientId = patient.id();
        assertNotNull(patientId);
        assertEquals("John Doe", patient.displayName());

        // Add clinical condition fact: Type 2 diabetes
        FactResponse conditionFact = patientFactService.create(patientId, new PatientFactCreateRequest(
                "type2_diabetes",
                objectMapper.readTree("{\"input_kind\":\"status\",\"assertion\":\"present\"}"),
                "Doctor note",
                patient.updatedAt()));
        assertNotNull(conditionFact.id());
        assertEquals("type2_diabetes", conditionFact.concept());

        // Refresh patient to get updated timestamp
        patient = patientService.get(patientId);

        // Add observation fact: eGFR (numeric)
        FactResponse observationFact = patientFactService.create(patientId, new PatientFactCreateRequest(
                "egfr",
                objectMapper.readTree("{\"input_kind\":\"numeric\",\"assertion\":\"present\",\"value_numeric\":75.0,\"effective_date\":\"2026-07-01\"}"),
                "Lab report",
                patient.updatedAt()));
        assertNotNull(observationFact.id());

        // Refresh patient again
        patient = patientService.get(patientId);

        // Add unsupported detail
        UnsupportedDetailResponse unsupported = patientService.createUnsupportedDetail(patientId,
                new UnsupportedDetailCreateRequest("condition", "Rare allergy to latex", "Reported by patient", "Intake form"));
        assertNotNull(unsupported.id());

        // Verify activity change events exist
        List<PatientChangeEventResponse> activity = patientService.activity(patientId);
        assertFalse(activity.isEmpty());

        // Test optimistic locking on patient update
        PatientUpdateRequest updateReq = new PatientUpdateRequest();
        updateReq.setDisplayName("John Doe Updated");
        updateReq.setExpectedUpdatedAt(patient.updatedAt());
        PatientResponse updatedPatient = patientService.update(patientId, updateReq);
        assertEquals("John Doe Updated", updatedPatient.displayName());

        // Stale update must fail with 409 PATIENT_RECORD_STALE
        PatientUpdateRequest staleReq = new PatientUpdateRequest();
        staleReq.setDisplayName("Stale Name");
        staleReq.setExpectedUpdatedAt(patient.updatedAt()); // Old updatedAt before last update
        ApplicationError staleError = assertThrows(ApplicationError.class, () -> patientService.update(patientId, staleReq));
        assertEquals("PATIENT_RECORD_STALE", staleError.getCode());

        // -------------------------------------------------------------
        // STEP 5: TRIAL FLOW
        // -------------------------------------------------------------
        String registryId = "NCT" + UUID.randomUUID().toString().substring(0, 8);
        TrialRead trial = trialService.createTrial(new TrialCreateRequest(
                registryId, "Diabetes Efficacy Study", "type2_diabetes", "Phase 3"));
        assertNotNull(trial.id());

        // Create draft version 1
        VersionCreateRequest vReq = new VersionCreateRequest();
        vReq.setVersion(1);
        vReq.setStatus("draft");
        vReq.setSourceText("Clinical protocol v1 source text");
        VersionRead draftVersion = trialService.createVersion(trial.id(), vReq);
        assertNotNull(draftVersion.id());

        // Add criterion 1: Type 2 diabetes inclusion
        CriterionCreateRequest crit1Req = new CriterionCreateRequest();
        crit1Req.setKind("inclusion");
        crit1Req.setOrder(1);
        crit1Req.setSourceText("Must have type 2 diabetes");
        crit1Req.setNormalizedRule(Map.of("op", "present", "fact", "condition.type2_diabetes"));
        crit1Req.setRequired(true);
        CriterionRead crit1 = criterionService.createCriterion(trial.id(), draftVersion.id(), crit1Req);
        assertNotNull(crit1.id());

        // Add criterion 2: eGFR < 30 exclusion
        CriterionCreateRequest crit2Req = new CriterionCreateRequest();
        crit2Req.setKind("exclusion");
        crit2Req.setOrder(2);
        crit2Req.setSourceText("Exclude severe kidney disease (eGFR < 30)");
        crit2Req.setNormalizedRule(Map.of(
                "op", "lt",
                "fact", "observation.egfr",
                "value", 30,
                "unit", "mL/min/1.73m2"));
        crit2Req.setRequired(true);
        CriterionRead crit2 = criterionService.createCriterion(trial.id(), draftVersion.id(), crit2Req);
        assertNotNull(crit2.id());

        // Approve trial version
        VersionCreateRequest approveReq = new VersionCreateRequest();
        approveReq.setVersion(1);
        approveReq.setStatus("approved");
        approveReq.setSourceText("Clinical protocol v1 approved");
        VersionRead approvedVersion = trialService.updateVersion(trial.id(), draftVersion.id(), approveReq);
        assertEquals(VersionStatus.approved, approvedVersion.status());

        // Verify modifying criteria on approved version is rejected
        ApplicationError immutabilityError = assertThrows(ApplicationError.class, () ->
                criterionService.createCriterion(trial.id(), draftVersion.id(), crit1Req));
        assertEquals("APPROVED_VERSION_IMMUTABLE", immutabilityError.getCode());

        // -------------------------------------------------------------
        // STEP 6: SCREENING FLOW
        // -------------------------------------------------------------
        UUID screeningId = screeningService.createScreening(new ScreeningCreateRequest(
                patientId, approvedVersion.id(), LocalDate.now()));
        assertNotNull(screeningId);

        ScreeningResponse screening = screeningService.getScreening(screeningId);
        assertNotNull(screening);
        assertEquals("potentially_eligible", screening.overallState());
        assertEquals(2, screening.evaluations().size());
        assertEquals("pass", screening.evaluations().get(0).result());
        assertEquals("pass", screening.evaluations().get(1).result());

        // -------------------------------------------------------------
        // STEP 7: BATCH SCREENING
        // -------------------------------------------------------------
        UUID batchId = batchService.createBatch(new BatchCreateRequest(
                List.of(patientId),
                null,
                List.of(approvedVersion.id()),
                "Batch Run 1",
                LocalDate.now()));
        assertNotNull(batchId);

        ScreeningBatchResponse batch = batchService.getBatch(batchId);
        assertNotNull(batch);
        assertEquals(1, batch.pairCount());
        assertEquals(1, batch.screenings().size());
        assertEquals("potentially_eligible", batch.screenings().get(0).overallState());

        // -------------------------------------------------------------
        // STEP 3: ADDITIONAL JPA PERSISTENCE (Documents, Spans, Chat)
        // -------------------------------------------------------------
        Document doc = new Document(testUser.getId(), DocumentKind.patient, DocumentSourceType.text);
        doc.setApprovedResourceId(patientId);
        doc.setFilename("clinical_note.txt");
        doc.setMimeType("text/plain");
        doc.setSizeBytes(100);
        doc.setChecksum("a".repeat(64));
        doc.setSourceText("Patient history note text");
        doc.setPagesJson("[]");
        doc.setCandidatesJson("[]");
        doc.setWarningsJson("[]");
        doc.setQualityJson("{}");
        doc.setStatus(DocumentStatus.approved);
        Document savedDoc = documentRepository.save(doc);
        assertNotNull(savedDoc.getId());

        DocumentSpan span = new DocumentSpan(1, 0, 15, "Patient history");
        savedDoc.addSpan(span);
        documentRepository.saveAndFlush(savedDoc);

        ScreeningChatMessage msg = new ScreeningChatMessage(
                screeningId, "user", "Why did this patient qualify?");
        ScreeningChatMessage savedMsg = chatRepository.save(msg);
        assertNotNull(savedMsg.getId());
    }

    @Test
    void testBatchValidationRules() {
        // patient_ids and snapshot_ids cannot both be present
        ApplicationError bothError = assertThrows(ApplicationError.class, () ->
                batchService.createBatch(new BatchCreateRequest(
                        List.of(UUID.randomUUID()),
                        List.of(UUID.randomUUID()),
                        List.of(UUID.randomUUID()),
                        "Invalid Batch",
                        LocalDate.now())));
        assertEquals("REQUEST_VALIDATION_ERROR", bothError.getCode());

        // neither present
        ApplicationError neitherError = assertThrows(ApplicationError.class, () ->
                batchService.createBatch(new BatchCreateRequest(
                        null,
                        null,
                        List.of(UUID.randomUUID()),
                        "Invalid Batch",
                        LocalDate.now())));
        assertEquals("REQUEST_VALIDATION_ERROR", neitherError.getCode());
    }
}
