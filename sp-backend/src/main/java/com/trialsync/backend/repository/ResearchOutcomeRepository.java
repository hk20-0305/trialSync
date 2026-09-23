package com.trialsync.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.ResearchOutcome;

@Repository
public interface ResearchOutcomeRepository extends JpaRepository<ResearchOutcome, UUID> {
    List<ResearchOutcome> findByEnrollmentId(UUID enrollmentId);
    Optional<ResearchOutcome> findByEnrollmentIdAndHorizonDays(UUID enrollmentId, int horizonDays);
}
