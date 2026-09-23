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

    /** Screening history: newest first, capped at 100 rows. */
    List<Screening> findTop100ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
