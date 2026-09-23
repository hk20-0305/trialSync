package com.trialsync.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.entity.PatientFact;

/**
 * Clinical details recorded against a patient.
 *
 * <p>Facts are never hard-deleted: voiding sets {@code voided_at}, so "active" always means
 * {@code voided_at is null}. The duplicate finders below reproduce
 * {@code active_duplicate_fact_query()}: same patient, same catalog concept, still active, ordered
 * newest first. The {@code exclude_fact_id} filter that Python adds when editing an existing fact is
 * applied by the caller on the returned (already ordered) list, which selects the same row.
 */
@Repository
public interface PatientFactRepository extends JpaRepository<PatientFact, UUID> {

    /** Edit and void paths, which refuse to touch an already voided fact. */
    Optional<PatientFact> findByIdAndPatientIdAndVoidedAtIsNull(UUID id, UUID patientId);

    /** Restore path, which must be able to load a voided fact. */
    Optional<PatientFact> findByIdAndPatientId(UUID id, UUID patientId);

    /**
     * Duplicate check for catalog concepts whose input kind is not numeric: one active row per
     * concept, regardless of effective date.
     */
    List<PatientFact> findByPatientIdAndFactTypeAndConceptAndVoidedAtIsNullOrderByCreatedAtDesc(
            UUID patientId, FactType factType, String concept);

    /**
     * Duplicate check for numeric concepts with null effective date.
     */
    List<PatientFact> findByPatientIdAndFactTypeAndConceptAndVoidedAtIsNullAndEffectiveDateIsNullOrderByCreatedAtDesc(
            UUID patientId, FactType factType, String concept);

    /**
     * Duplicate check for numeric concepts with a specific effective date.
     */
    List<PatientFact> findByPatientIdAndFactTypeAndConceptAndVoidedAtIsNullAndEffectiveDateOrderByCreatedAtDesc(
            UUID patientId, FactType factType, String concept, LocalDate effectiveDate);
}
