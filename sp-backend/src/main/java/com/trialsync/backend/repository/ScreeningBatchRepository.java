package com.trialsync.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.ScreeningBatch;

/** Groups of screenings requested together. */
@Repository
public interface ScreeningBatchRepository extends JpaRepository<ScreeningBatch, UUID> {

    /** Ownership guard for batch detail. */
    Optional<ScreeningBatch> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Batch history: newest first, capped at 100 rows. */
    List<ScreeningBatch> findTop100ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
