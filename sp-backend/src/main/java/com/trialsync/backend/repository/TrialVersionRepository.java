package com.trialsync.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.TrialVersion;
import com.trialsync.backend.entity.enums.VersionStatus;

/**
 * Revisions of a trial's eligibility criteria.
 *
 * <p>Screening may only run against an approved version owned by the caller, which Python expresses
 * as one query joining {@code trials} rather than two round trips; the derived method below produces
 * the same single statement. Criteria are not join-fetched: the association is annotated
 * {@code @BatchSize}, which reproduces SQLAlchemy's {@code selectinload} - a second batched
 * {@code IN} query - without turning the parent query into a bag join.
 */
@Repository
public interface TrialVersionRepository extends JpaRepository<TrialVersion, UUID> {

    /** Draft editing: a version is only addressable through its own trial. */
    Optional<TrialVersion> findByIdAndTrialId(UUID id, UUID trialId);

    /** {@code owned_approved_version()}: version id + owner of the parent trial + approved status. */
    Optional<TrialVersion> findByIdAndStatusAndTrial_OwnerId(
            UUID id, VersionStatus status, UUID ownerId);
}
