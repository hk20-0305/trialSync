package com.trialsync.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.entity.ClinicalConcept;

/**
 * The controlled catalog of clinical concepts a patient fact may reference.
 *
 * <p>Retiring a concept flips {@code active} to false instead of deleting the row, so facts already
 * recorded against it keep resolving. Everything on the data-entry path therefore filters on
 * {@code active is true}; only the admin listing shows retired entries, sorted so they fall to the
 * bottom.
 */
@Repository
public interface ClinicalConceptRepository extends JpaRepository<ClinicalConcept, UUID> {

    /** Catalog served to the patient form: grouped, then in curated display order. */
    List<ClinicalConcept> findByActiveTrueOrderByConceptGroupAscDisplayOrderAsc();

    /** Resolve a catalog entry by its stable key. */
    Optional<ClinicalConcept> findByKeyAndActiveTrue(String key);

    /** Resolve a catalog entry from a stored fact's {@code (fact_type, concept)} pair. */
    Optional<ClinicalConcept> findByFactTypeAndConceptAndActiveTrue(FactType factType, String concept);

    /** Admin listing: active first, then group, curated order and label. */
    @Query("""
            select c
              from ClinicalConcept c
             order by c.active desc, c.conceptGroup, c.displayOrder, c.displayLabel
            """)
    List<ClinicalConcept> findAllForAdministration();

    /**
     * Highest curated position inside a group, used to append a newly created concept.
     *
     * <p>Returns {@code null} for an empty group; the caller applies the same {@code or 0} fallback
     * and {@code + 10} step as Python.
     */
    @Query("select max(c.displayOrder) from ClinicalConcept c where c.conceptGroup = :conceptGroup")
    Integer findMaxDisplayOrder(@Param("conceptGroup") String conceptGroup);
}
