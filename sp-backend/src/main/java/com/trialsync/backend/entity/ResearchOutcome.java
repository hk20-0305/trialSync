package com.trialsync.backend.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Longitudinal study outcome record for an enrolled participant,
 * capturing the ground truth dropout status within an evaluation horizon (e.g. 90 days).
 */
@Entity
@Table(name = "research_outcomes")
public class ResearchOutcome extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "enrollment_id", nullable = false, columnDefinition = "uuid")
    private UUID enrollmentId;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays = 90;

    @Column(name = "dropout_within_horizon", nullable = false)
    private boolean dropoutWithinHorizon;

    @Column(name = "dropout_day")
    private Integer dropoutDay;

    @Column(name = "dropout_reason", length = 64)
    private String dropoutReason;

    @Column(name = "is_censored", nullable = false)
    private boolean isCensored = false;

    @Column(name = "censoring_day")
    private Integer censoringDay;

    @Column(name = "follow_up_days", nullable = false)
    private int followUpDays;

    @Column(name = "completed_study", nullable = false)
    private boolean completedStudy = false;

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

    public int getHorizonDays() {
        return horizonDays;
    }

    public void setHorizonDays(int horizonDays) {
        this.horizonDays = horizonDays;
    }

    public boolean isDropoutWithinHorizon() {
        return dropoutWithinHorizon;
    }

    public void setDropoutWithinHorizon(boolean dropoutWithinHorizon) {
        this.dropoutWithinHorizon = dropoutWithinHorizon;
    }

    public Integer getDropoutDay() {
        return dropoutDay;
    }

    public void setDropoutDay(Integer dropoutDay) {
        this.dropoutDay = dropoutDay;
    }

    public String getDropoutReason() {
        return dropoutReason;
    }

    public void setDropoutReason(String dropoutReason) {
        this.dropoutReason = dropoutReason;
    }

    public boolean isCensored() {
        return isCensored;
    }

    public void setCensored(boolean censored) {
        isCensored = censored;
    }

    public Integer getCensoringDay() {
        return censoringDay;
    }

    public void setCensoringDay(Integer censoringDay) {
        this.censoringDay = censoringDay;
    }

    public int getFollowUpDays() {
        return followUpDays;
    }

    public void setFollowUpDays(int followUpDays) {
        this.followUpDays = followUpDays;
    }

    public boolean isCompletedStudy() {
        return completedStudy;
    }

    public void setCompletedStudy(boolean completedStudy) {
        this.completedStudy = completedStudy;
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
