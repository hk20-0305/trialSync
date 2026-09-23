package com.trialsync.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.PatientChangeEvent;

/**
 * Append-only audit trail for a patient record.
 *
 * <p>Rows are written, never updated, which is why the entity has {@code created_at} but no
 * {@code updated_at}. The activity feed shows the 100 most recent entries, newest first.
 */
@Repository
public interface PatientChangeEventRepository extends JpaRepository<PatientChangeEvent, UUID> {

    /** Activity feed: {@code order_by(desc(created_at)).limit(100)}. */
    List<PatientChangeEvent> findTop100ByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
