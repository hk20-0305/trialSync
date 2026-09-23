package com.trialsync.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trialsync.backend.entity.ResearchMeasurement;

@Repository
public interface ResearchMeasurementRepository extends JpaRepository<ResearchMeasurement, UUID> {
    List<ResearchMeasurement> findByEnrollmentIdOrderByMeasurementDayAsc(UUID enrollmentId);
    List<ResearchMeasurement> findByEnrollmentIdAndCodeOrderByMeasurementDayAsc(UUID enrollmentId, String code);
}
