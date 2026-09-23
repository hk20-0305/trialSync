package com.trialsync.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.Criterion;

/** Individual eligibility criteria belonging to a trial version. */
@Repository
public interface CriterionRepository extends JpaRepository<Criterion, UUID> {

    /** A criterion is only addressable through the version that owns it. */
    Optional<Criterion> findByIdAndTrialVersionId(UUID id, UUID trialVersionId);

    /**
     * Highest position inside a version, used to append a new criterion.
     *
     * <p>Native SQL because the column is the reserved word {@code "order"}, which HQL cannot parse
     * as a path expression. Returns {@code null} for an empty version; the caller applies the same
     * fallback Python does.
     */
    @Query(
            value = "select max(\"order\") from criteria where trial_version_id = :trialVersionId",
            nativeQuery = true)
    Integer findMaxOrder(@Param("trialVersionId") UUID trialVersionId);
}
