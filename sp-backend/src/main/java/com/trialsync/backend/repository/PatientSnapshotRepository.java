package com.trialsync.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.PatientSnapshot;

/**
 * Frozen copies of the patient inputs a screening ran against.
 *
 * <p>{@code snapshot_for_patient()} first looks for an existing snapshot with the same content hash
 * and reuses it, so re-screening an unchanged patient does not accumulate identical rows; the unique
 * constraint on {@code (patient_id, content_hash)} enforces that even under a race.
 */
@Repository
public interface PatientSnapshotRepository extends JpaRepository<PatientSnapshot, UUID> {

    /** Reuse lookup before creating a snapshot. */
    Optional<PatientSnapshot> findByPatientIdAndContentHash(UUID patientId, String contentHash);

    /** Ownership guard when a caller screens against an explicitly supplied snapshot id. */
    Optional<PatientSnapshot> findByIdAndOwnerId(UUID id, UUID ownerId);
}
