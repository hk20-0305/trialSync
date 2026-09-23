package com.trialsync.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.EligibilityRagIndex;

@Repository
public interface EligibilityRagIndexRepository extends JpaRepository<EligibilityRagIndex, UUID> {
    Optional<EligibilityRagIndex> findByTrialVersionId(UUID trialVersionId);
    Optional<EligibilityRagIndex> findByTrialVersionIdAndIndexVersion(UUID trialVersionId, String indexVersion);
    List<EligibilityRagIndex> findByStatus(String status);
}
