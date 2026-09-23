package com.trialsync.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.ResearchParticipant;

@Repository
public interface ResearchParticipantRepository extends JpaRepository<ResearchParticipant, UUID> {
    List<ResearchParticipant> findByOwnerId(UUID ownerId);
    Optional<ResearchParticipant> findByOwnerIdAndParticipantCode(UUID ownerId, String participantCode);
    List<ResearchParticipant> findByPatientId(UUID patientId);
}
