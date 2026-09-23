package com.trialsync.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.ResearchEnrollment;

@Repository
public interface ResearchEnrollmentRepository extends JpaRepository<ResearchEnrollment, UUID> {
    List<ResearchEnrollment> findByOwnerId(UUID ownerId);
    Optional<ResearchEnrollment> findByOwnerIdAndEnrollmentCode(UUID ownerId, String enrollmentCode);
    List<ResearchEnrollment> findByParticipantId(UUID participantId);
    List<ResearchEnrollment> findByTrialVersionId(UUID trialVersionId);
    Optional<ResearchEnrollment> findByScreeningId(UUID screeningId);
}
