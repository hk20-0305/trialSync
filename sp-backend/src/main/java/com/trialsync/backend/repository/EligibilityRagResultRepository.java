package com.trialsync.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.EligibilityRagResult;

@Repository
public interface EligibilityRagResultRepository extends JpaRepository<EligibilityRagResult, UUID> {
    List<EligibilityRagResult> findByRagRunId(UUID ragRunId);
    List<EligibilityRagResult> findByRagRunIdOrderByRankAsc(UUID ragRunId);
}
