package com.trialsync.backend.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Longitudinal study visit tracking scheduled vs. actual attendance.
 */
@Entity
@Table(name = "research_visit_events")
public class ResearchVisitEvent extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "enrollment_id", nullable = false, columnDefinition = "uuid")
    private UUID enrollmentId;

    @Column(name = "visit_number")
    private Integer visitNumber;

    @Column(name = "visit_name", length = 64)
    private String visitName;

    @Column(name = "scheduled_day", nullable = false)
    private int scheduledDay;

    @Column(name = "actual_day")
    private Integer actualDay;

    @Column(name = "scheduled_at", columnDefinition = "timestamptz")
    private OffsetDateTime scheduledAt;

    @Column(name = "attended_at", columnDefinition = "timestamptz")
    private OffsetDateTime attendedAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ATTENDED";

    @Column(name = "visit_type", length = 32)
    private String visitType;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", insertable = false, updatable = false)
    private ResearchEnrollment enrollment;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(UUID enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public Integer getVisitNumber() {
        return visitNumber;
    }

    public void setVisitNumber(Integer visitNumber) {
        this.visitNumber = visitNumber;
    }

    public String getVisitName() {
        return visitName;
    }

    public void setVisitName(String visitName) {
        this.visitName = visitName;
    }

    public int getScheduledDay() {
        return scheduledDay;
    }

    public void setScheduledDay(int scheduledDay) {
        this.scheduledDay = scheduledDay;
    }

    public Integer getActualDay() {
        return actualDay;
    }

    public void setActualDay(Integer actualDay) {
        this.actualDay = actualDay;
    }

    public OffsetDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(OffsetDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public OffsetDateTime getAttendedAt() {
        return attendedAt;
    }

    public void setAttendedAt(OffsetDateTime attendedAt) {
        this.attendedAt = attendedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVisitType() {
        return visitType;
    }

    public void setVisitType(String visitType) {
        this.visitType = visitType;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public ResearchEnrollment getEnrollment() {
        return enrollment;
    }

    public void setEnrollment(ResearchEnrollment enrollment) {
        this.enrollment = enrollment;
    }
}
