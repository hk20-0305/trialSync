package com.trialsync.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.Screening;

/**
 * Saved screening results.
 *
 * <p>Unlike patients and trials the history is ordered by {@code created_at}, not
 * {@code updated_at}: a screening is written once and never edited, so creation time is the only
 * meaningful ordering.
 */
@Repository
public interface ScreeningRepository extends JpaRepository<Screening, UUID> {

    /** Ownership guard used by {@code _owned_screening()}. */
    Optional<Screening> findByIdAndOwnerId(UUID id, UUID ownerId);

    /**
     * The screening a patient/trial-version pair already has, if any.
     *
     * <p>This mirrors the unique key on {@code (patient_id, trial_version_id)}
     * ({@code ux_screenings_patient_trial_version}) exactly. That is what makes it the one lookup
     * that answers "has this pair already been screened?" for every entry point: a single
     * submission, a batch cell and the demo seed all ask this question, and all of them reuse the
     * stored row - together with its criterion evaluations - instead of writing a second screening
     * for one pair. Batch rows are included, because the rule is one screening per pair no matter
     * which entry point produced it.
     *
     * <p>No owner predicate is added on purpose. The pair already belongs to exactly one owner
     * through {@code patients.owner_id} and {@code trials.owner_id}, and matching the key the
     * database enforces is what keeps this check in step with the constraint it stands in front of.
     *
     * <p>{@code findFirst ... OrderByCreatedAtDesc} rather than a plain {@code findBy} so a database
     * still carrying pre-index duplicates answers with the row the migration kept - the newest one -
     * instead of raising {@code NonUniqueResultException}.
     */
    Optional<Screening> findFirstByPatientIdAndTrialVersionIdOrderByCreatedAtDesc(
            UUID patientId, UUID trialVersionId);

    /** Screening history: newest first, capped at 100 rows. */
    List<Screening> findTop100ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
