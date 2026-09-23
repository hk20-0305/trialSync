package com.trialsync.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.ResearchVisitEvent;

@Repository
public interface ResearchVisitEventRepository extends JpaRepository<ResearchVisitEvent, UUID> {
    List<ResearchVisitEvent> findByEnrollmentIdOrderByScheduledDayAsc(UUID enrollmentId);
}
