package com.trialsync.backend.service;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.config.TrialSyncProperties;
import com.trialsync.backend.domain.model.OverallState;
import com.trialsync.backend.dto.screening.BatchCreateRequest;
import com.trialsync.backend.dto.screening.BatchPairResponse;
import com.trialsync.backend.dto.screening.BatchStateCountsResponse;
import com.trialsync.backend.dto.screening.ScreeningBatchResponse;
import com.trialsync.backend.dto.screening.ScreeningCountsResponse;
import com.trialsync.backend.entity.Patient;
import com.trialsync.backend.entity.PatientSnapshot;
import com.trialsync.backend.entity.Screening;
import com.trialsync.backend.entity.ScreeningBatch;
import com.trialsync.backend.entity.TrialVersion;
import com.trialsync.backend.entity.User;
import com.trialsync.backend.repository.ScreeningBatchRepository;
import com.trialsync.backend.security.SecurityContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Screens a grid of patients against a set of trial versions. Port of the batch endpoints of
 * {@code trialsync.api.screenings}.
 *
 * <p>A batch is not a second screening implementation: every cell of the grid goes through
 * {@link ScreeningService#runAndStore}, the same method the single-screening endpoint calls, so a
 * batched result is byte-for-byte the result the caller would have got one request at a time. The
 * only difference is that the screening rows carry the batch identifier.
 *
 * <p>The work is synchronous and transactional, exactly as in Python: there is no queue and no
 * background worker, and if any pair fails the entire batch - including snapshots created along the
 * way - is rolled back rather than left half-written.
 */
@Service
public class ScreeningBatchService {

    private final ScreeningBatchRepository batches;
    private final ScreeningService screeningService;
    private final SnapshotService snapshotService;
    private final TrialSyncProperties properties;

    public ScreeningBatchService(
            ScreeningBatchRepository batches,
            ScreeningService screeningService,
            SnapshotService snapshotService,
            TrialSyncProperties properties) {
        this.batches = batches;
        this.screeningService = screeningService;
        this.snapshotService = snapshotService;
        this.properties = properties;
    }

    /**
     * Port of {@code create_batch}, minus the response read.
     *
     * <p>Identifiers are de-duplicated first, preserving the order they were sent in, which is what
     * {@code list(dict.fromkeys(...))} does. The two limit checks then run before anything is
     * loaded or written, so an oversized request costs no database work - and they are two separate
     * checks with two distinct messages, because the selection limit and the pair limit are
     * different configuration knobs.
     *
     * <p>As with a single screening, the identifier is returned and the response is read back in a
     * fresh transaction, mirroring Python's commit-then-re-read.
     */
    @Transactional
    public UUID createBatch(BatchCreateRequest request) {
        User user = SecurityContext.require();

        if (!request.isExactlyOnePatientSource()) {
            throw new ApplicationError(
                    "REQUEST_VALIDATION_ERROR",
                    "Provide exactly one of patient_ids or patient_snapshot_ids.",
                    422);
        }

        List<UUID> patientIds = distinct(request.patientIds());
        List<UUID> snapshotIds = distinct(request.patientSnapshotIds());
        List<UUID> versionIds = distinct(request.trialVersionIds());
        int patientCount = patientIds.isEmpty() ? snapshotIds.size() : patientIds.size();

        if (patientCount > properties.getScreeningBatchMaxPatients()
                || versionIds.size() > properties.getScreeningBatchMaxTrials()) {
            throw new ApplicationError(
                    "BATCH_LIMIT_EXCEEDED", "Batch selection exceeds configured limits.", 422);
        }
        int pairCount = patientCount * versionIds.size();
        if (pairCount > properties.getScreeningBatchMaxPairs()) {
            throw new ApplicationError(
                    "BATCH_LIMIT_EXCEEDED", "Batch pair count exceeds configured limit.", 422);
        }

        List<PatientSnapshot> snapshots = new ArrayList<>();
        if (!patientIds.isEmpty()) {
            List<Patient> selected = new ArrayList<>();
            for (UUID patientId : patientIds) {
                selected.add(screeningService.ownedPatient(user.getId(), patientId));
            }
            for (Patient patient : selected) {
                snapshots.add(snapshotService.snapshotForPatient(patient));
            }
        } else {
            for (UUID snapshotId : snapshotIds) {
                snapshots.add(screeningService.ownedSnapshot(user.getId(), snapshotId));
            }
        }

        List<TrialVersion> versions = new ArrayList<>();
        for (UUID versionId : versionIds) {
            versions.add(screeningService.ownedApprovedVersion(user.getId(), versionId));
        }

        ScreeningBatch batch = new ScreeningBatch(user.getId(), request.label(), pairCount);
        batches.saveAndFlush(batch);

        LocalDate screeningDate =
                request.screeningDate() == null ? LocalDate.now() : request.screeningDate();
        for (PatientSnapshot snapshot : snapshots) {
            for (TrialVersion version : versions) {
                screeningService.runAndStore(user.getId(), snapshot, version, screeningDate, batch);
            }
        }
        return batch.getId();
    }

    /** Port of {@code get_batch}. */
    @Transactional(readOnly = true)
    public ScreeningBatchResponse getBatch(UUID batchId) {
        User user = SecurityContext.require();
        return toResponse(ownedBatch(user.getId(), batchId));
    }

    /** Port of {@code list_batches}: newest first, capped at 100. */
    @Transactional(readOnly = true)
    public List<ScreeningBatchResponse> listBatches() {
        User user = SecurityContext.require();
        List<ScreeningBatchResponse> responses = new ArrayList<>();
        for (ScreeningBatch batch : batches.findTop100ByOwnerIdOrderByCreatedAtDesc(user.getId())) {
            responses.add(toResponse(batch));
        }
        return responses;
    }

    /** Port of {@code _owned_batch}. */
    public ScreeningBatch ownedBatch(UUID ownerId, UUID batchId) {
        return batches
                .findByIdAndOwnerId(batchId, ownerId)
                .orElseThrow(
                        () ->
                                ApplicationError.notFound(
                                        "SCREENING_BATCH_NOT_FOUND",
                                        "Screening batch was not found."));
    }

    /**
     * Port of {@code _batch_read}.
     *
     * <p>{@code pair_count} is what was requested; the state counts and
     * {@code unknown_criterion_count} are totalled from the stored screenings, so the review queue
     * reflects what the engine actually decided.
     */
    public ScreeningBatchResponse toResponse(ScreeningBatch batch) {
        int potentiallyEligible = 0;
        int likelyIneligible = 0;
        int needsReview = 0;
        int unknownCriterionCount = 0;
        List<BatchPairResponse> pairs = new ArrayList<>();

        for (Screening screening : batch.getScreenings()) {
            OverallState state = screening.getOverallState();
            if (state == OverallState.POTENTIALLY_ELIGIBLE) {
                potentiallyEligible++;
            } else if (state == OverallState.LIKELY_INELIGIBLE) {
                likelyIneligible++;
            } else if (state == OverallState.NEEDS_REVIEW) {
                needsReview++;
            }
            ScreeningCountsResponse counts = screeningService.counts(screening);
            unknownCriterionCount += counts.unknownCount();
            pairs.add(
                    new BatchPairResponse(
                            screening.getPatientSnapshotId(),
                            screening.getTrialVersionId(),
                            screeningService.snapshotSummary(screening),
                            screeningService.trialVersionSummary(screening),
                            screening.getId(),
                            state.value(),
                            counts));
        }

        return new ScreeningBatchResponse(
                batch.getId(),
                batch.getLabel(),
                batch.getPairCount(),
                batch.getCreatedAt(),
                new BatchStateCountsResponse(potentiallyEligible, likelyIneligible, needsReview),
                unknownCriterionCount,
                pairs);
    }

    /** Order-preserving de-duplication, the behaviour of {@code list(dict.fromkeys(values))}. */
    private static List<UUID> distinct(List<UUID> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }
}
