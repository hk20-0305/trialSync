package com.trialsync.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.EligibilityRagRun;

@Repository
public interface EligibilityRagRunRepository extends JpaRepository<EligibilityRagRun, UUID> {
    List<EligibilityRagRun> findByTrialVersionId(UUID trialVersionId);
    List<EligibilityRagRun> findByOwnerId(UUID ownerId);
}
