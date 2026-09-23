package com.trialsync.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.PatientUnsupportedDetail;

/**
 * Free-text findings that have no controlled-catalog concept and are parked for manual review.
 *
 * <p>They never take part in screening; they exist so importing a document does not silently drop
 * information the engine cannot reason about.
 */
@Repository
public interface PatientUnsupportedDetailRepository
        extends JpaRepository<PatientUnsupportedDetail, UUID> {

    /** Edit and delete paths, scoped to the patient the caller already proved they own. */
    Optional<PatientUnsupportedDetail> findByIdAndPatientId(UUID id, UUID patientId);

    /**
     * Duplicate check on create: same patient, same category, same label ignoring case.
     *
     * <p>Mirrors {@code func.lower(PatientUnsupportedDetail.label) == payload.label.lower()} - the
     * caller passes an already lower-cased label so both sides match Python exactly.
     */
    @Query("""
            select d
              from PatientUnsupportedDetail d
             where d.patientId = :patientId
               and d.category = :category
               and lower(d.label) = :labelLower
            """)
    List<PatientUnsupportedDetail> findByPatientIdAndCategoryAndLoweredLabel(
            @Param("patientId") UUID patientId,
            @Param("category") String category,
            @Param("labelLower") String labelLower);
}
