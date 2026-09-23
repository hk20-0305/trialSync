package com.trialsync.backend.service;

import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.domain.model.CriterionKind;
import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.domain.model.OverallState;
import com.trialsync.backend.entity.Criterion;
import com.trialsync.backend.entity.CriterionEvaluation;
import com.trialsync.backend.entity.Patient;
import com.trialsync.backend.entity.PatientFact;
import com.trialsync.backend.entity.PatientSnapshot;
import com.trialsync.backend.entity.Screening;
import com.trialsync.backend.entity.ScreeningBatch;
import com.trialsync.backend.entity.ScreeningChatMessage;
import com.trialsync.backend.entity.Trial;
import com.trialsync.backend.entity.TrialVersion;
import com.trialsync.backend.entity.User;
import com.trialsync.backend.entity.enums.VersionStatus;
import com.trialsync.backend.repository.CriterionEvaluationRepository;
import com.trialsync.backend.repository.CriterionRepository;
import com.trialsync.backend.repository.PatientFactRepository;
import com.trialsync.backend.repository.PatientRepository;
import com.trialsync.backend.repository.PatientSnapshotRepository;
import com.trialsync.backend.repository.ScreeningBatchRepository;
import com.trialsync.backend.repository.ScreeningChatMessageRepository;
import com.trialsync.backend.repository.ScreeningRepository;
import com.trialsync.backend.repository.TrialRepository;
import com.trialsync.backend.repository.TrialVersionRepository;
import com.trialsync.backend.repository.UserRepository;
import com.trialsync.backend.security.Pbkdf2PasswordHasher;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Byte-for-byte and row-for-row port of Python's {@code trialsync.demo.seed_demo_data}.
 *
 * <p>Reproduces the exact deterministic Phase 8 synthetic demo workspace:
 * <ul>
 *   <li>6 synthetic patients with facts</li>
 *   <li>2 approved trials with versioned criteria</li>
 *   <li>12 deterministic screenings (4 potentially eligible, 4 needs review, 4 likely ineligible)</li>
 *   <li>8 chat messages attached to Dev Malik's needs-review screening</li>
 * </ul>
 *
 * <p>Preserves the existing demo user's row and UUID if one already exists, deleting and
 * recreating only the owned patient, trial, screening, and chat data.
 */
@Service
@Transactional
public class DemoSeedService {

    public static final String DEMO_EMAIL = "demo@trialsync.example";
    public static final String DEMO_PASSWORD = "SyntheticDemo123!";
    public static final String E2E_EMAIL = "phase8-browser@trialsync.example";
    public static final LocalDate DEMO_SCREENING_DATE = LocalDate.of(2026, 7, 16);
    public static final UUID NAMESPACE_URL = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8");

    public record DemoSeedSummary(
            String email,
            int patients,
            int trials,
            int screenings,
            int batches,
            int chatMessages,
            int potentiallyEligible,
            int likelyIneligible,
            int needsReview) {}

    private final UserRepository users;
    private final PatientRepository patients;
    private final PatientFactRepository patientFacts;
    private final TrialRepository trials;
    private final TrialVersionRepository trialVersions;
    private final CriterionRepository criteria;
    private final ScreeningRepository screenings;
    private final ScreeningBatchRepository screeningBatches;
    private final PatientSnapshotRepository patientSnapshots;
    private final CriterionEvaluationRepository criterionEvaluations;
    private final ScreeningChatMessageRepository chatMessages;
    private final SnapshotService snapshotService;
    private final ScreeningService screeningService;
    private final Pbkdf2PasswordHasher passwordHasher;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    public DemoSeedService(
            UserRepository users,
            PatientRepository patients,
            PatientFactRepository patientFacts,
            TrialRepository trials,
            TrialVersionRepository trialVersions,
            CriterionRepository criteria,
            ScreeningRepository screenings,
            ScreeningBatchRepository screeningBatches,
            PatientSnapshotRepository patientSnapshots,
            CriterionEvaluationRepository criterionEvaluations,
            ScreeningChatMessageRepository chatMessages,
            SnapshotService snapshotService,
            ScreeningService screeningService,
            Pbkdf2PasswordHasher passwordHasher,
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate) {
        this.users = users;
        this.patients = patients;
        this.patientFacts = patientFacts;
        this.trials = trials;
        this.trialVersions = trialVersions;
        this.criteria = criteria;
        this.screenings = screenings;
        this.screeningBatches = screeningBatches;
        this.patientSnapshots = patientSnapshots;
        this.criterionEvaluations = criterionEvaluations;
        this.chatMessages = chatMessages;
        this.snapshotService = snapshotService;
        this.screeningService = screeningService;
        this.passwordHasher = passwordHasher;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Deterministic UUIDv5 generator matching Python's {@code uuid.uuid5(uuid.NAMESPACE_URL, url)}. */
    public static UUID id(String name, String namespace) {
        String prefix = (namespace != null && !namespace.isBlank()) ? namespace + "/" : "";
        String url = "https://trialsync.local/demo/" + prefix + name;
        return uuid5(NAMESPACE_URL, url);
    }

    public static UUID uuid5(UUID namespace, String name) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
            bb.putLong(namespace.getMostSignificantBits());
            bb.putLong(namespace.getLeastSignificantBits());
            md.update(bb.array());
            md.update(name.getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest();
            hash[6] = (byte) ((hash[6] & 0x0f) | 0x50); // version 5
            hash[8] = (byte) ((hash[8] & 0x3f) | 0x80); // RFC 4122 variant
            ByteBuffer buf = ByteBuffer.wrap(hash);
            return new UUID(buf.getLong(), buf.getLong());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 digest unavailable", ex);
        }
    }

    public DemoSeedSummary seedDemoData() {
        return seedDemoData(DEMO_EMAIL, DEMO_PASSWORD, null);
    }

    /**
     * Seeds the reproducible synthetic demo workspace.
     * Preserves existing user row and ID if present; clears and recreates owned data.
     */
    @Transactional
    public DemoSeedSummary seedDemoData(String email, String password, String namespace) {
        String normalizedEmail = email.toLowerCase().trim();
        String idNamespace = namespace != null ? namespace : (normalizedEmail.equals(DEMO_EMAIL) ? null : normalizedEmail);

        Optional<User> existingUser = users.findByEmail(normalizedEmail);
        User user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
            user.setDisplayName("Demo Coordinator");
            user.setPasswordHash(passwordHasher.hash(password));
            users.saveAndFlush(user);

            // Clean only this demo user's owned data, preserving the user row and UUID
            deleteDemoOwnedData(user.getId());
        } else {
            UUID userId = id("user/phase8-demo", idNamespace);
            user = new User(normalizedEmail, "Demo Coordinator", passwordHasher.hash(password));
            user.setId(userId);
            users.saveAndFlush(user);
        }

        UUID ownerId = user.getId();

        // 1. Build & persist 6 synthetic patients with deterministic facts
        List<Patient> patientList = buildPatients(ownerId, idNamespace);
        for (Patient p : patientList) {
            entityManager.persist(p);
            for (PatientFact f : p.getFacts()) {
                entityManager.persist(f);
            }
        }
        entityManager.flush();

        // 2. Build & persist 2 approved trials with versions and criteria
        List<Trial> trialList = buildTrials(ownerId, idNamespace);
        for (Trial t : trialList) {
            entityManager.persist(t);
            for (TrialVersion v : t.getVersions()) {
                entityManager.persist(v);
                for (Criterion c : v.getCriteria()) {
                    entityManager.persist(c);
                }
            }
        }
        entityManager.flush();

        // 3. Create snapshots for each patient
        List<PatientSnapshot> snapshotList = new ArrayList<>(patientList.size());
        for (Patient p : patientList) {
            snapshotList.add(snapshotService.snapshotForPatient(p));
        }

        // 4. Create screening batch
        ScreeningBatch batch = new ScreeningBatch(
                ownerId,
                "Phase 8 mixed-outcome matrix",
                snapshotList.size() * trialList.size());
        batch.setId(id("batch/mixed-matrix", idNamespace));
        entityManager.persist(batch);
        entityManager.flush();

        // 5. Run deterministic screening for all 6 patients x 2 trials
        List<Screening> screeningList = new ArrayList<>(snapshotList.size() * trialList.size());
        for (PatientSnapshot snapshot : snapshotList) {
            for (Trial trial : trialList) {
                TrialVersion version = trial.getVersions().get(0);
                Screening screening = screeningService.runAndStore(
                        ownerId, snapshot, version, DEMO_SCREENING_DATE, batch);
                screeningList.add(screening);
            }
        }

        // 6. Verify 4/4/4 outcome distribution
        int potentiallyEligible = 0;
        int likelyIneligible = 0;
        int needsReview = 0;
        for (Screening s : screeningList) {
            if (s.getOverallState() == OverallState.POTENTIALLY_ELIGIBLE) {
                potentiallyEligible++;
            } else if (s.getOverallState() == OverallState.LIKELY_INELIGIBLE) {
                likelyIneligible++;
            } else if (s.getOverallState() == OverallState.NEEDS_REVIEW) {
                needsReview++;
            }
        }

        if (potentiallyEligible != 4 || likelyIneligible != 4 || needsReview != 4) {
            throw new IllegalStateException(String.format(
                    "Seeded screenings did not produce expected 4/4/4 distribution! "
                            + "Found: %d potentially eligible, %d likely ineligible, %d needs review.",
                    potentiallyEligible, likelyIneligible, needsReview));
        }

        // 7. Seed demo conversation history on Dev Malik's needs-review screening for Trial 1 (Metabolic)
        UUID metabolicVersionId = trialList.get(0).getVersions().get(0).getId();
        UUID devMalikSnapshotId = snapshotList.get(3).getId();

        Screening needsReviewScreening = screeningList.stream()
                .filter(s -> s.getTrialVersionId().equals(metabolicVersionId)
                        && s.getPatientSnapshotId().equals(devMalikSnapshotId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find Dev Malik's metabolic screening"));

        List<CriterionEvaluation> evals = criterionEvaluations
                .findByScreeningIdOrderByCriterionOrder(needsReviewScreening.getId());
        if (evals.isEmpty()) {
            throw new IllegalStateException("Dev Malik's metabolic screening has no criterion evaluations");
        }
        CriterionEvaluation ageEvaluation = evals.get(0); // Criterion 1: Age

        List<ScreeningChatMessage> messages = buildChatMessages(needsReviewScreening.getId(), ageEvaluation);
        for (ScreeningChatMessage message : messages) {
            entityManager.persist(message);
        }
        entityManager.flush();

        return new DemoSeedSummary(
                user.getEmail(),
                patientList.size(),
                trialList.size(),
                screeningList.size(),
                1,
                messages.size(),
                potentiallyEligible,
                likelyIneligible,
                needsReview);
    }

    private void deleteDemoOwnedData(UUID ownerId) {
        // Delete any screenings owned by this user OR referencing this user's trial versions or patient snapshots
        // (to satisfy ON DELETE RESTRICT on screenings_trial_version_id_fkey)
        jdbcTemplate.update("""
            DELETE FROM screenings
             WHERE owner_id = ?
                OR trial_version_id IN (SELECT tv.id FROM trial_versions tv JOIN trials t ON tv.trial_id = t.id WHERE t.owner_id = ?)
                OR patient_snapshot_id IN (SELECT id FROM patient_snapshots WHERE owner_id = ?)
            """, ownerId, ownerId, ownerId);

        jdbcTemplate.update("DELETE FROM screening_batches WHERE owner_id = ?", ownerId);
        jdbcTemplate.update("DELETE FROM patient_snapshots WHERE owner_id = ?", ownerId);
        jdbcTemplate.update("DELETE FROM patients WHERE owner_id = ?", ownerId);
        jdbcTemplate.update("DELETE FROM trials WHERE owner_id = ?", ownerId);
        jdbcTemplate.update("DELETE FROM documents WHERE owner_id = ?", ownerId);

        entityManager.flush();
        entityManager.clear();
    }

    private PatientFact fact(
            Patient patient,
            String name,
            FactType factType,
            String concept,
            String numeric,
            String unit,
            Assertion assertion,
            LocalDate effectiveDate,
            String sourceLabel,
            String namespace) {
        PatientFact pf = new PatientFact(patient.getId(), factType, concept);
        pf.setId(id("fact/" + name, namespace));
        if (numeric != null) {
            pf.setValueNumeric(new BigDecimal(numeric));
        }
        pf.setUnit(unit);
        pf.setAssertion(assertion != null ? assertion : Assertion.PRESENT);
        pf.setEffectiveDate(effectiveDate);
        pf.setSourceLabel(sourceLabel != null ? sourceLabel : "Synthetic Phase 8 demo fixture");
        return pf;
    }

    private List<Patient> buildPatients(UUID ownerId, String namespace) {
        List<Patient> records = new ArrayList<>();

        Patient p1 = new Patient(ownerId, "SYN-P8-001", "Synthetic Ada Mercer");
        p1.setId(id("patient/eligible", namespace));
        p1.setDateOfBirth(LocalDate.of(1980, 1, 15));
        p1.setSex("female");
        records.add(p1);

        Patient p2 = new Patient(ownerId, "SYN-P8-002", "Synthetic Ben Carter");
        p2.setId(id("patient/inclusion-fail", namespace));
        p2.setDateOfBirth(LocalDate.of(2012, 3, 10));
        p2.setSex("male");
        records.add(p2);

        Patient p3 = new Patient(ownerId, "SYN-P8-003", "Synthetic Cora Bennett");
        p3.setId(id("patient/exclusion-fail", namespace));
        p3.setDateOfBirth(LocalDate.of(1988, 9, 22));
        p3.setSex("female");
        records.add(p3);

        Patient p4 = new Patient(ownerId, "SYN-P8-004", "Synthetic Dev Malik");
        p4.setId(id("patient/needs-review", namespace));
        p4.setDateOfBirth(null);
        p4.setSex(null);
        records.add(p4);

        Patient p5 = new Patient(ownerId, "SYN-P8-005", "Synthetic Emi Tanaka");
        p5.setId(id("patient/type1", namespace));
        p5.setDateOfBirth(LocalDate.of(1994, 11, 5));
        p5.setSex("female");
        records.add(p5);

        Patient p6 = new Patient(ownerId, "SYN-P8-006", "Synthetic Finn Osei");
        p6.setId(id("patient/boundary", namespace));
        p6.setDateOfBirth(LocalDate.of(2008, 7, 16));
        p6.setSex("male");
        records.add(p6);

        LocalDate recent = DEMO_SCREENING_DATE.minusDays(7); // 2026-07-09

        for (Patient patient : records) {
            String suffix = patient.getExternalId().toLowerCase();
            patient.getFacts().add(fact(patient, suffix + "/hba1c", FactType.OBSERVATION, "hba1c",
                    "7.6", "%", Assertion.PRESENT, recent, "Synthetic Phase 8 demo fixture", namespace));
            patient.getFacts().add(fact(patient, suffix + "/pregnancy", FactType.CONDITION, "pregnancy",
                    null, null, Assertion.ABSENT, null, "Synthetic Phase 8 demo fixture", namespace));
            patient.getFacts().add(fact(patient, suffix + "/egfr", FactType.OBSERVATION, "egfr",
                    "72", "mL/min/1.73m2", Assertion.PRESENT, recent, "Synthetic Phase 8 demo fixture", namespace));
            patient.getFacts().add(fact(patient, suffix + "/type2", FactType.CONDITION, "type2_diabetes",
                    null, null, Assertion.PRESENT, null, "Synthetic Phase 8 demo fixture", namespace));
        }

        // Cora Bennett (index 2): Pregnancy absent is removed, pregnancy present added
        records.get(2).getFacts().removeIf(f -> f.getFactType() == FactType.CONDITION && "pregnancy".equals(f.getConcept()));
        records.get(2).getFacts().add(fact(records.get(2), "syn-p8-003/pregnancy-trigger", FactType.CONDITION,
                "pregnancy", null, null, Assertion.PRESENT, null, "Synthetic Phase 8 demo fixture", namespace));

        // Emi Tanaka (index 4): type2_diabetes is removed, type1_diabetes and eGFR 28 added
        records.get(4).getFacts().removeIf(f -> f.getFactType() == FactType.CONDITION && "type2_diabetes".equals(f.getConcept()));
        records.get(4).getFacts().add(fact(records.get(4), "syn-p8-005/type1", FactType.CONDITION,
                "type1_diabetes", null, null, Assertion.PRESENT, null, "Synthetic Phase 8 demo fixture", namespace));
        records.get(4).getFacts().add(fact(records.get(4), "syn-p8-005/egfr-trigger", FactType.OBSERVATION,
                "egfr", "28", "mL/min/1.73m2", Assertion.PRESENT, recent, "Synthetic Phase 8 demo fixture", namespace));

        return records;
    }

    private List<Trial> buildTrials(UUID ownerId, String namespace) {
        List<Trial> trialList = new ArrayList<>();

        // Trial 1: Synthetic metabolic eligibility study
        Trial metabolic = new Trial(ownerId, "SYN-P8-METABOLIC", "Synthetic metabolic eligibility study", "Synthetic metabolic condition");
        metabolic.setId(id("trial/metabolic", namespace));
        metabolic.setPhase("Phase 2");

        TrialVersion metabolicVersion = new TrialVersion(metabolic.getId(), 1);
        metabolicVersion.setId(id("trial-version/metabolic/1", namespace));
        metabolicVersion.setStatus(VersionStatus.approved);
        metabolicVersion.setSourceText("Synthetic protocol used only for the Phase 8 demonstration.");

        Criterion c1 = new Criterion(metabolicVersion.getId(), CriterionKind.INCLUSION, 1, "Age 18 to 75 years at screening");
        c1.setId(id("criterion/metabolic/age", namespace));
        c1.setNormalizedRule("{\"op\":\"between\",\"fact\":\"demographic.age\",\"min\":18,\"max\":75,\"unit\":\"year\"}");
        c1.setRequired(true);

        Criterion c2 = new Criterion(metabolicVersion.getId(), CriterionKind.INCLUSION, 2, "Documented Type 2 diabetes");
        c2.setId(id("criterion/metabolic/type2", namespace));
        c2.setNormalizedRule("{\"op\":\"present\",\"fact\":\"condition.type2_diabetes\"}");
        c2.setRequired(true);

        Criterion c3 = new Criterion(metabolicVersion.getId(), CriterionKind.INCLUSION, 3, "HbA1c between 7.0% and 10.0%");
        c3.setId(id("criterion/metabolic/hba1c", namespace));
        c3.setNormalizedRule("{\"op\":\"between\",\"fact\":\"observation.hba1c\",\"min\":7.0,\"max\":10.0,\"unit\":\"%\",\"selection\":\"latest\"}");
        c3.setRequired(true);

        Criterion c4 = new Criterion(metabolicVersion.getId(), CriterionKind.EXCLUSION, 4, "Current pregnancy");
        c4.setId(id("criterion/metabolic/pregnancy", namespace));
        c4.setNormalizedRule("{\"op\":\"present\",\"fact\":\"condition.pregnancy\"}");
        c4.setRequired(true);

        metabolicVersion.setTrial(metabolic);
        c1.setTrialVersion(metabolicVersion);
        c2.setTrialVersion(metabolicVersion);
        c3.setTrialVersion(metabolicVersion);
        c4.setTrialVersion(metabolicVersion);
        metabolicVersion.getCriteria().addAll(List.of(c1, c2, c3, c4));
        metabolic.getVersions().add(metabolicVersion);
        trialList.add(metabolic);

        // Trial 2: Synthetic renal safety study
        Trial renal = new Trial(ownerId, "SYN-P8-RENAL", "Synthetic renal safety study", "Synthetic renal monitoring condition");
        renal.setId(id("trial/renal", namespace));
        renal.setPhase("Phase 3");

        TrialVersion renalVersion = new TrialVersion(renal.getId(), 1);
        renalVersion.setId(id("trial-version/renal/1", namespace));
        renalVersion.setStatus(VersionStatus.approved);
        renalVersion.setSourceText("Synthetic protocol used only for the Phase 8 demonstration.");

        Criterion rc1 = new Criterion(renalVersion.getId(), CriterionKind.INCLUSION, 1, "Age 21 to 68 years at screening");
        rc1.setId(id("criterion/renal/age", namespace));
        rc1.setNormalizedRule("{\"op\":\"between\",\"fact\":\"demographic.age\",\"min\":21,\"max\":68,\"unit\":\"year\"}");
        rc1.setRequired(true);

        Criterion rc2 = new Criterion(renalVersion.getId(), CriterionKind.EXCLUSION, 2, "eGFR below 30 mL/min/1.73m2 within 30 days");
        rc2.setId(id("criterion/renal/egfr", namespace));
        rc2.setNormalizedRule("{\"op\":\"within_before\",\"days\":30,\"arg\":{\"op\":\"lt\",\"fact\":\"observation.egfr\",\"value\":30,\"unit\":\"mL/min/1.73m2\",\"selection\":\"latest\"}}");
        rc2.setRequired(true);

        renalVersion.setTrial(renal);
        rc1.setTrialVersion(renalVersion);
        rc2.setTrialVersion(renalVersion);
        renalVersion.getCriteria().addAll(List.of(rc1, rc2));
        renal.getVersions().add(renalVersion);
        trialList.add(renal);

        return trialList;
    }

    private List<ScreeningChatMessage> buildChatMessages(UUID screeningId, CriterionEvaluation ageEvaluation) {
        OffsetDateTime started = OffsetDateTime.of(2026, 7, 16, 10, 0, 0, 0, ZoneOffset.UTC);
        List<ScreeningChatMessage> list = new ArrayList<>();

        // Turn 1
        ScreeningChatMessage m1 = new ScreeningChatMessage(screeningId, "user", "Why does this result need review?");
        m1.setCreatedAt(started);
        list.add(m1);

        ScreeningChatMessage m2 = new ScreeningChatMessage(screeningId, "assistant",
                "The age criterion is unresolved because the approved snapshot has no date of birth.");
        m2.setAnswerState("supported");
        m2.setCitationsJson(String.format(
                "[{\"criterion_id\":\"%s\",\"evaluation_id\":\"%s\",\"evidence_ids\":[],\"label\":\"Age is unresolved\"}]",
                ageEvaluation.getCriterionId(), ageEvaluation.getId()));
        m2.setProvider("canonical");
        m2.setModelId("deterministic-canonical-1");
        m2.setPromptVersion("screening-chat-v1");
        m2.setCreatedAt(started.plusMinutes(1));
        list.add(m2);

        // Turn 2
        ScreeningChatMessage m3 = new ScreeningChatMessage(screeningId, "user", "What information is missing?");
        m3.setCreatedAt(started.plusMinutes(2));
        list.add(m3);

        ScreeningChatMessage m4 = new ScreeningChatMessage(screeningId, "assistant",
                "A date of birth is required to calculate age at the recorded screening date.");
        m4.setAnswerState("supported");
        m4.setCitationsJson(String.format(
                "[{\"criterion_id\":\"%s\",\"evaluation_id\":\"%s\",\"evidence_ids\":[],\"label\":\"Date of birth is missing\"}]",
                ageEvaluation.getCriterionId(), ageEvaluation.getId()));
        m4.setProvider("canonical");
        m4.setModelId("deterministic-canonical-1");
        m4.setPromptVersion("screening-chat-v1");
        m4.setCreatedAt(started.plusMinutes(3));
        list.add(m4);

        // Turn 3
        ScreeningChatMessage m5 = new ScreeningChatMessage(screeningId, "user", "Should this participant enroll?");
        m5.setCreatedAt(started.plusMinutes(4));
        list.add(m5);

        ScreeningChatMessage m6 = new ScreeningChatMessage(screeningId, "assistant",
                "I cannot recommend enrollment. TrialSync only explains the stored educational pre-screening result.");
        m6.setAnswerState("refused");
        m6.setCitationsJson("[]");
        m6.setProvider("canonical");
        m6.setModelId("deterministic-canonical-1");
        m6.setPromptVersion("screening-chat-v1");
        m6.setCreatedAt(started.plusMinutes(5));
        list.add(m6);

        // Turn 4
        ScreeningChatMessage m7 = new ScreeningChatMessage(screeningId, "user", "What is the participant's preferred meal?");
        m7.setCreatedAt(started.plusMinutes(6));
        list.add(m7);

        ScreeningChatMessage m8 = new ScreeningChatMessage(screeningId, "assistant",
                "The screening record does not contain enough information to answer that question.");
        m8.setAnswerState("insufficient_evidence");
        m8.setCitationsJson("[]");
        m8.setProvider("canonical");
        m8.setModelId("deterministic-canonical-1");
        m8.setPromptVersion("screening-chat-v1");
        m8.setCreatedAt(started.plusMinutes(7));
        list.add(m8);

        return list;
    }
}
