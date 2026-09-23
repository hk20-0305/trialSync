package com.trialsync.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.ResearchAdverseEvent;

@Repository
public interface ResearchAdverseEventRepository extends JpaRepository<ResearchAdverseEvent, UUID> {
    List<ResearchAdverseEvent> findByEnrollmentIdOrderByEventDayAsc(UUID enrollmentId);
}
