package com.trialsync.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trialsync.backend.BaseIntegrationTest;
import com.trialsync.backend.domain.model.OverallState;
import com.trialsync.backend.dto.auth.TokenResponse;
import com.trialsync.backend.dto.auth.UserCreateRequest;
import com.trialsync.backend.dto.patient.PatientCreateRequest;
import com.trialsync.backend.dto.screening.BatchCreateRequest;
import com.trialsync.backend.dto.screening.ScreeningBatchResponse;
import com.trialsync.backend.dto.screening.ScreeningCreateOutcome;
import com.trialsync.backend.dto.screening.ScreeningCreateRequest;
import com.trialsync.backend.dto.trial.CriterionCreateRequest;
import com.trialsync.backend.dto.trial.TrialCreateRequest;
import com.trialsync.backend.dto.trial.TrialRead;
import com.trialsync.backend.dto.trial.VersionCreateRequest;
import com.trialsync.backend.dto.trial.VersionRead;
import com.trialsync.backend.entity.Screening;
import com.trialsync.backend.entity.User;
import com.trialsync.backend.repository.ScreeningRepository;
import com.trialsync.backend.repository.UserRepository;
import com.trialsync.backend.security.SecurityContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The business rule: one screening per patient and trial version, batch runs included.
 *
 * <p>Every case goes through the real service against the migrated database, because the rule is
 * enforced in two places that must agree - the existence check in {@code ScreeningService} and the
 * unique key on {@code (patient_id, trial_version_id)}. Testing only the first would leave the
 * second untested, and the second is the one that survives two concurrent submissions.
 *
 * <p>Counting is done with SQL rather than through {@code listScreenings}: a duplicate the service
 * had already returned around would still be visible in the table, and the table is what the user
 * sees as the saved screening count.
 *
 * <p>A batch reuses the screening of a pair it was asked to cover instead of screening it again, so
 * the pair still has exactly one row and one set of criterion evaluations. A pair that no batch owns
 * yet is claimed by the batch that screens it, which is what keeps the batch report - built from the
 * rows stamped with its identifier - complete.
 */
class ScreeningDeduplicationTest extends BaseIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private PatientService patientService;
    @Autowired private TrialService trialService;
    @Autowired private CriterionService criterionService;
    @Autowired private ScreeningService screeningService;
    @Autowired private ScreeningBatchService batchService;
    @Autowired private ScreeningRepository screenings;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private User user;

    @BeforeEach
    void setUp() {
        String email = "dedup-" + suffix() + "@example.com";
        TokenResponse registered =
                authService.register(new UserCreateRequest(email, "Dr. Dedup", "Password123!"));
        user = userRepository.findById(registered.user().id()).orElseThrow();
        SecurityContext.setForTesting(user);
    }

    /**
     * Deletes the user this case created.
     *
     * <p>The {@code it} profile points at a real shared PostgreSQL database, not a rolled-back one:
     * every {@code @Transactional} boundary here commits for good. {@code ON DELETE CASCADE} takes the
     * patient, trial, snapshot, screening and evaluation rows with the user, so one statement leaves
     * nothing behind - which matters because {@code DemoSeedTest} counts users globally. Matching on
     * the email prefix also clears rows written by a run that was interrupted before its teardown.
     */
    @AfterEach
    void tearDown() {
        SecurityContext.clear();
        jdbc.update("delete from users where email like 'dedup-%@example.com'");
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
    // ---------------------------------------------------------------------- fixtures

    /** A patient of this user; external id and name are unique per call so nothing collides. */
    private UUID createPatient(String label) {
        return patientService.create(
                        new PatientCreateRequest(
                                "EXT-" + suffix(),
                                label + " " + suffix(),
                                LocalDate.of(1980, 5, 12),
                                "female",
                                false))
                .id();
    }

    /**
     * A trial carrying one approved version with a single criterion, which is the smallest protocol a
     * real screening can run against: {@code createScreening} refuses an unapproved version.
     */
    private UUID createApprovedTrial(String label) {
        TrialRead trial =
                trialService.createTrial(
                        new TrialCreateRequest(
                                "NCT" + suffix(),
                                label + " " + suffix(),
                                "type2_diabetes",
                                "Phase 3"));

        VersionCreateRequest draft = new VersionCreateRequest();
        draft.setVersion(1);
        draft.setStatus("draft");
        draft.setSourceText("Protocol source for " + label);
        VersionRead version = trialService.createVersion(trial.id(), draft);

        CriterionCreateRequest criterion = new CriterionCreateRequest();
        criterion.setKind("inclusion");
        criterion.setOrder(1);
        criterion.setSourceText("Must have type 2 diabetes");
        criterion.setNormalizedRule(Map.of("op", "present", "fact", "condition.type2_diabetes"));
        criterion.setRequired(true);
        criterionService.createCriterion(trial.id(), version.id(), criterion);

        VersionCreateRequest approved = new VersionCreateRequest();
        approved.setVersion(1);
        approved.setStatus("approved");
        approved.setSourceText("Protocol approved for " + label);
        return trialService.updateVersion(trial.id(), version.id(), approved).id();
    }

    /** Screens the pair and fails when the submission did not report a newly created screening. */
    private UUID screen(UUID patientId, UUID trialVersionId) {
        ScreeningCreateOutcome outcome =
                screeningService.createScreening(
                        new ScreeningCreateRequest(patientId, trialVersionId, LocalDate.now()));
        assertTrue(outcome.created(), "expected this patient and trial pair to be screened");
        assertNotNull(outcome.screeningId());
        return outcome.screeningId();
    }

    // ---------------------------------------------------------------------- counting

    /** Rows of the history for one pair - the count the dashboard and history page report. */
    private long standaloneRows(UUID patientId, UUID trialVersionId) {
        return rowsFor(patientId, trialVersionId, "and s.batch_id is null");
    }

    /** Every row for one pair, batch rows included. */
    private long allRows(UUID patientId, UUID trialVersionId) {
        return rowsFor(patientId, trialVersionId, "");
    }

    /**
     * Counted through {@code patient_snapshots} rather than {@code screenings.patient_id} on purpose:
     * that column is the denormalised copy the unique index is built on, so reading a count through
     * the snapshot proves the copy agrees with the relationship it was derived from.
     */
    private long rowsFor(UUID patientId, UUID trialVersionId, String extraCondition) {
        Long count =
                jdbc.queryForObject(
                        "select count(*) from screenings s"
                                + " join patient_snapshots ps on ps.id = s.patient_snapshot_id"
                                + " where ps.patient_id = cast(? as uuid)"
                                + " and s.trial_version_id = cast(? as uuid) "
                                + extraCondition,
                        Long.class,
                        patientId.toString(),
                        trialVersionId.toString());
        return count == null ? 0L : count;
    }

    /** The denormalised patient identifier the unique index is built on. */
    private UUID storedPatientId(UUID screeningId) {
        return UUID.fromString(
                jdbc.queryForObject(
                        "select patient_id::text from screenings where id = cast(? as uuid)",
                        String.class,
                        screeningId.toString()));
    }

    /**
     * A screening row identical to {@code original} apart from the batch it belongs to, used to test
     * the index without going through the service - the service would never attempt the insert.
     */
    private Screening copyOf(
            Screening original, UUID patientId, UUID trialVersionId, UUID batchId) {
        Screening copy =
                new Screening(user.getId(), original.getPatientSnapshotId(), trialVersionId);
        copy.setPatientId(patientId);
        copy.setBatchId(batchId);
        copy.setTrialRegistryId(original.getTrialRegistryId());
        copy.setTrialTitle(original.getTrialTitle());
        copy.setTrialVersionNumber(original.getTrialVersionNumber());
        copy.setOverallState(OverallState.POTENTIALLY_ELIGIBLE);
        copy.setScreeningDate(LocalDate.now());
        copy.setEngineVersion(original.getEngineVersion());
        copy.setDslVersion(original.getDslVersion());
        copy.setTerminologyVersion(original.getTerminologyVersion());
        copy.setUnitVersion(original.getUnitVersion());
        return copy;
    }

    // ---------------------------------------------------------------------- cases

    /** Requirement 5a: the first time a pair is screened, one screening is created. */
    @Test
    void firstScreeningOfAPatientAndTrialIsCreated() {
        UUID patientId = createPatient("First");
        UUID trialVersionId = createApprovedTrial("First");

        UUID screeningId = screen(patientId, trialVersionId);

        assertEquals(1L, standaloneRows(patientId, trialVersionId));
        // The column the unique index depends on has to be filled in, not left to the join.
        assertEquals(patientId, storedPatientId(screeningId));
    }

    /** Requirement 5b: the same patient and trial again creates nothing and returns what exists. */
    @Test
    void screeningTheSamePatientAndTrialAgainReturnsTheExistingScreening() {
        UUID patientId = createPatient("Repeat");
        UUID trialVersionId = createApprovedTrial("Repeat");
        UUID first = screen(patientId, trialVersionId);

        ScreeningCreateOutcome second =
                screeningService.createScreening(
                        new ScreeningCreateRequest(patientId, trialVersionId, LocalDate.now()));

        assertEquals(first, second.screeningId(), "the existing screening must be returned");
        assertFalse(second.created(), "a repeat submission must not report a new screening");
        assertEquals(1L, standaloneRows(patientId, trialVersionId), "saved count must not grow");
        assertEquals(1L, allRows(patientId, trialVersionId));
    }

    /** Requirement 5c: a different patient against the same trial is a different screening. */
    @Test
    void anotherPatientAgainstTheSameTrialGetsItsOwnScreening() {
        UUID firstPatient = createPatient("Shared trial A");
        UUID secondPatient = createPatient("Shared trial B");
        UUID trialVersionId = createApprovedTrial("Shared trial");

        UUID firstScreening = screen(firstPatient, trialVersionId);
        UUID secondScreening = screen(secondPatient, trialVersionId);

        assertNotNull(secondScreening);
        assertFalse(firstScreening.equals(secondScreening));
        assertEquals(1L, standaloneRows(firstPatient, trialVersionId));
        assertEquals(1L, standaloneRows(secondPatient, trialVersionId));
    }

    /** Requirement 5d: the same patient against a different trial is a different screening. */
    @Test
    void theSamePatientAgainstAnotherTrialGetsItsOwnScreening() {
        UUID patientId = createPatient("Two trials");
        UUID firstTrialVersion = createApprovedTrial("Trial one");
        UUID secondTrialVersion = createApprovedTrial("Trial two");

        UUID firstScreening = screen(patientId, firstTrialVersion);
        UUID secondScreening = screen(patientId, secondTrialVersion);

        assertFalse(firstScreening.equals(secondScreening));
        assertEquals(1L, standaloneRows(patientId, firstTrialVersion));
        assertEquals(1L, standaloneRows(patientId, secondTrialVersion));
    }

    /**
     * Requirement 4: the key itself, not only the service pre-check, stops a second row.
     *
     * <p>The row is written straight through the repository, so this case still fails if the pre-check
     * is removed. What a batch does with an already-screened pair is {@link
     * #batchScreeningsForAnAlreadyScreenedPairReuseTheStoredScreening()}'s job, because a
     * {@code batch_id} has to reference a real batch.
     */
    @Test
    void theDatabaseRejectsASecondStandaloneScreeningForTheSamePair() {
        UUID patientId = createPatient("Index");
        UUID trialVersionId = createApprovedTrial("Index");
        UUID stored = screen(patientId, trialVersionId);
        Screening original = screenings.findById(stored).orElseThrow();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> screenings.saveAndFlush(copyOf(original, patientId, trialVersionId, null)));
        assertEquals(1L, standaloneRows(patientId, trialVersionId));
        assertEquals(1L, allRows(patientId, trialVersionId));
    }

    /**
     * The key covers batch rows too: a second screening of one pair cannot be written even when it
     * names a batch, which is exactly the case the standalone-only key allowed. The row is written
     * straight through the repository, so this fails if the key ever becomes partial again.
     */
    @Test
    void theDatabaseRejectsASecondBatchedScreeningForTheSamePair() {
        UUID patientId = createPatient("Batched key");
        UUID trialVersionId = createApprovedTrial("Batched key");
        UUID stored = screen(patientId, trialVersionId);

        UUID batchId =
                batchService.createBatch(
                        new BatchCreateRequest(
                                List.of(patientId),
                                null,
                                List.of(trialVersionId),
                                "Index probe " + suffix(),
                                LocalDate.now()));
        Screening original = screenings.findById(stored).orElseThrow();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> screenings.saveAndFlush(copyOf(original, patientId, trialVersionId, batchId)));
        assertEquals(1L, allRows(patientId, trialVersionId));
    }

    /**
     * A batch reuses the screening of a pair that already has one instead of screening it again: the
     * stored row - and its criterion evaluations - is what the batch cell shows, so the pair keeps
     * exactly one row while the batch report still lists the cell it was asked for. The single
     * screening that already exists is also what a following submission returns, so nothing is ever
     * added to history for a pair.
     */
    @Test
    void batchScreeningsForAnAlreadyScreenedPairReuseTheStoredScreening() {
        UUID patientId = createPatient("Batched");
        UUID trialVersionId = createApprovedTrial("Batched");
        UUID standalone = screen(patientId, trialVersionId);

        UUID batchId =
                batchService.createBatch(
                        new BatchCreateRequest(
                                List.of(patientId),
                                null,
                                List.of(trialVersionId),
                                "Dedup batch " + suffix(),
                                LocalDate.now()));
        ScreeningBatchResponse batch = batchService.getBatch(batchId);

        assertEquals(1, batch.pairCount());
        assertEquals(1, batch.screenings().size(), "the batch must still list the pair it was given");
        assertEquals(standalone, batch.screenings().get(0).screeningId(), "reused, not re-stored");
        assertEquals(1L, allRows(patientId, trialVersionId), "the pair must keep exactly one row");
        // The stored row now belongs to the batch that re-screened the pair.
        assertEquals(0L, standaloneRows(patientId, trialVersionId));

        ScreeningCreateOutcome afterBatch =
                screeningService.createScreening(
                        new ScreeningCreateRequest(patientId, trialVersionId, LocalDate.now()));
        assertEquals(standalone, afterBatch.screeningId());
        assertFalse(afterBatch.created());
        assertEquals(1L, allRows(patientId, trialVersionId));
    }

    /**
     * A pair that has only ever been screened inside a batch is screened no further: the batch result
     * already answers it, so the submission returns that result and no standalone history row appears.
     */
    @Test
    void aPairScreenedOnlyInsideABatchIsNotScreenedAgain() {
        UUID patientId = createPatient("Batch only");
        UUID trialVersionId = createApprovedTrial("Batch only");

        UUID batchId =
                batchService.createBatch(
                        new BatchCreateRequest(
                                List.of(patientId),
                                null,
                                List.of(trialVersionId),
                                "Batch-only dedup probe " + suffix(),
                                LocalDate.now()));
        UUID batched = batchService.getBatch(batchId).screenings().get(0).screeningId();
        assertEquals(0L, standaloneRows(patientId, trialVersionId));

        ScreeningCreateOutcome outcome =
                screeningService.createScreening(
                        new ScreeningCreateRequest(patientId, trialVersionId, LocalDate.now()));
        assertEquals(batched, outcome.screeningId());
        assertFalse(outcome.created());
        assertEquals(0L, standaloneRows(patientId, trialVersionId), "the POST must not add history");
        assertEquals(1L, allRows(patientId, trialVersionId));
    }
}
