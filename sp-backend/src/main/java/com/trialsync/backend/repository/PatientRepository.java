package com.trialsync.backend.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.Patient;

/**
 * Patients, always scoped by owner.
 *
 * <p>Every read carries {@code owner_id} in the predicate rather than filtering after loading, so a
 * patient belonging to another account is indistinguishable from one that does not exist and both
 * produce the same 404.
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    /** Ownership guard used by {@code owned_patient()}. */
    Optional<Patient> findByIdAndOwnerId(UUID id, UUID ownerId);

    /**
     * Staleness guard for edits.
     *
     * <p>Python performs {@code update(Patient).where(id, owner_id, updated_at == expected)
     * .returning(Patient.id)} and treats "no row returned" as a lost update. The Java port reads the
     * row back with the same three predicates inside the writing transaction; an empty result means
     * the client's {@code expected_updated_at} no longer matches and the caller raises the same
     * conflict. This is application-level locking on {@code updated_at} - there is no JPA
     * {@code @Version} column, because the schema has no version counter.
     */
    Optional<Patient> findByIdAndOwnerIdAndUpdatedAt(UUID id, UUID ownerId, OffsetDateTime updatedAt);

    /** Patient list: newest activity first, capped at 100 rows. */
    List<Patient> findTop100ByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    /**
     * Duplicate-name warning on create.
     *
     * <p>Mirrors {@code func.lower(Patient.display_name) == payload.display_name.strip().lower()}:
     * the column is lower-cased in SQL and the caller passes an already trimmed and lower-cased
     * label, so the two sides are compared exactly as Python compares them.
     */
    @Query("select p from Patient p where p.ownerId = :ownerId and lower(p.displayName) = :displayNameLower")
    List<Patient> findByOwnerIdAndLoweredDisplayName(
            @Param("ownerId") UUID ownerId, @Param("displayNameLower") String displayNameLower);
}
