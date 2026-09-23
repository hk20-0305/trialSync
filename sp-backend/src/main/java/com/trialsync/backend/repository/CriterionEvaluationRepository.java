package com.trialsync.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.CriterionEvaluation;

/**
 * Per-criterion outcomes of a saved screening.
 *
 * <p>Always read in the criterion order captured at screening time, so a stored result presents its
 * criteria in the same sequence the trial version defined even if the draft is later renumbered.
 */
@Repository
public interface CriterionEvaluationRepository extends JpaRepository<CriterionEvaluation, UUID> {

    /** {@code where(screening_id == ...).order_by(criterion_order)}. */
    List<CriterionEvaluation> findByScreeningIdOrderByCriterionOrder(UUID screeningId);
}
