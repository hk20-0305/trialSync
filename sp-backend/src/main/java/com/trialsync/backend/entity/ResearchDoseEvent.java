package com.trialsync.backend.entity;

import java.math.BigDecimal;
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
 * Longitudinal study dose administration and adherence record.
 */
@Entity
@Table(name = "research_dose_events")
public class ResearchDoseEvent extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "enrollment_id", nullable = false, columnDefinition = "uuid")
    private UUID enrollmentId;

    @Column(name = "dose_number")
    private Integer doseNumber;

    @Column(name = "scheduled_day", nullable = false)
    private int scheduledDay;

    @Column(name = "scheduled_at", columnDefinition = "timestamptz")
    private OffsetDateTime scheduledAt;

    @Column(name = "administered_at", columnDefinition = "timestamptz")
    private OffsetDateTime administeredAt;

    @Column(name = "prescribed_dose_amount", precision = 10, scale = 2)
    private BigDecimal prescribedDoseAmount;

    @Column(name = "actual_dose_amount", precision = 10, scale = 2)
    private BigDecimal actualDoseAmount;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ADMINISTERED";

    @Column(name = "adherence_ratio", precision = 5, scale = 4)
    private BigDecimal adherenceRatio;

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

    public Integer getDoseNumber() {
        return doseNumber;
    }

    public void setDoseNumber(Integer doseNumber) {
        this.doseNumber = doseNumber;
    }

    public int getScheduledDay() {
        return scheduledDay;
    }

    public void setScheduledDay(int scheduledDay) {
        this.scheduledDay = scheduledDay;
    }

    public OffsetDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(OffsetDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public OffsetDateTime getAdministeredAt() {
        return administeredAt;
    }

    public void setAdministeredAt(OffsetDateTime administeredAt) {
        this.administeredAt = administeredAt;
    }

    public BigDecimal getPrescribedDoseAmount() {
        return prescribedDoseAmount;
    }

    public void setPrescribedDoseAmount(BigDecimal prescribedDoseAmount) {
        this.prescribedDoseAmount = prescribedDoseAmount;
    }

    public BigDecimal getActualDoseAmount() {
        return actualDoseAmount;
    }

    public void setActualDoseAmount(BigDecimal actualDoseAmount) {
        this.actualDoseAmount = actualDoseAmount;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getAdherenceRatio() {
        return adherenceRatio;
    }

    public void setAdherenceRatio(BigDecimal adherenceRatio) {
        this.adherenceRatio = adherenceRatio;
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
