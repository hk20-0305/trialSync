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
 * Longitudinal adverse event / safety finding recorded for an enrolled study participant.
 */
@Entity
@Table(name = "research_adverse_events")
public class ResearchAdverseEvent extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "enrollment_id", nullable = false, columnDefinition = "uuid")
    private UUID enrollmentId;

    @Column(name = "event_day", nullable = false)
    private int eventDay;

    @Column(name = "onset_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime onsetAt;

    @Column(name = "resolved_at", columnDefinition = "timestamptz")
    private OffsetDateTime resolvedAt;

    @Column(name = "term", nullable = false, length = 200)
    private String term;

    @Column(name = "ctcae_grade", nullable = false)
    private int ctcaeGrade = 1;

    @Column(name = "is_serious", nullable = false)
    private boolean isSerious = false;

    @Column(name = "relatedness", nullable = false, length = 32)
    private String relatedness = "NOT_RELATED";

    @Column(name = "action_taken", length = 64)
    private String actionTaken;

    @Column(name = "outcome", length = 64)
    private String outcome;

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

    public int getEventDay() {
        return eventDay;
    }

    public void setEventDay(int eventDay) {
        this.eventDay = eventDay;
    }

    public OffsetDateTime getOnsetAt() {
        return onsetAt;
    }

    public void setOnsetAt(OffsetDateTime onsetAt) {
        this.onsetAt = onsetAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public int getCtcaeGrade() {
        return ctcaeGrade;
    }

    public void setCtcaeGrade(int ctcaeGrade) {
        this.ctcaeGrade = ctcaeGrade;
    }

    public boolean isSerious() {
        return isSerious;
    }

    public void setSerious(boolean serious) {
        isSerious = serious;
    }

    public String getRelatedness() {
        return relatedness;
    }

    public void setRelatedness(String relatedness) {
        this.relatedness = relatedness;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
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
