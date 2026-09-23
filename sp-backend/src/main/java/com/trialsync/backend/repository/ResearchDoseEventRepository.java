package com.trialsync.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.ResearchDoseEvent;

@Repository
public interface ResearchDoseEventRepository extends JpaRepository<ResearchDoseEvent, UUID> {
    List<ResearchDoseEvent> findByEnrollmentIdOrderByScheduledDayAsc(UUID enrollmentId);
}
