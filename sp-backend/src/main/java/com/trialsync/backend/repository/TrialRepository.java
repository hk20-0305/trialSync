package com.trialsync.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.Trial;

/** Trials, always scoped by owner in the same way patients are. */
@Repository
public interface TrialRepository extends JpaRepository<Trial, UUID> {

    /** Ownership guard used by {@code owned_trial()}. */
    Optional<Trial> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Trial list: newest activity first, capped at 100 rows. */
    List<Trial> findTop100ByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);
}
