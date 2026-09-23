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
 * An enrolled participant in a study/trial version, serving as the bridge
 * between longitudinal research analytics and the deterministic matching product.
 */
@Entity
@Table(name = "research_enrollments")
public class ResearchEnrollment extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "participant_id", nullable = false, columnDefinition = "uuid")
    private UUID participantId;

    @Column(name = "patient_snapshot_id", columnDefinition = "uuid")
    private UUID patientSnapshotId;

    @Column(name = "trial_id", columnDefinition = "uuid")
    private UUID trialId;

    @Column(name = "trial_version_id", columnDefinition = "uuid")
    private UUID trialVersionId;

    @Column(name = "screening_id", columnDefinition = "uuid")
    private UUID screeningId;

    @Column(name = "enrollment_code", nullable = false, length = 64)
    private String enrollmentCode;

    @Column(name = "enrolled_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime enrolledAt;

    @Column(name = "arm", length = 64)
    private String arm;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", insertable = false, updatable = false)
    private ResearchParticipant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_snapshot_id", insertable = false, updatable = false)
    private PatientSnapshot patientSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trial_id", insertable = false, updatable = false)
    private Trial trial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trial_version_id", insertable = false, updatable = false)
    private TrialVersion trialVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", insertable = false, updatable = false)
    private Screening screening;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public UUID getParticipantId() {
        return participantId;
    }

    public void setParticipantId(UUID participantId) {
        this.participantId = participantId;
    }

    public UUID getPatientSnapshotId() {
        return patientSnapshotId;
    }

    public void setPatientSnapshotId(UUID patientSnapshotId) {
        this.patientSnapshotId = patientSnapshotId;
    }

    public UUID getTrialId() {
        return trialId;
    }

    public void setTrialId(UUID trialId) {
        this.trialId = trialId;
    }

    public UUID getTrialVersionId() {
        return trialVersionId;
    }

    public void setTrialVersionId(UUID trialVersionId) {
        this.trialVersionId = trialVersionId;
    }

    public UUID getScreeningId() {
        return screeningId;
    }

    public void setScreeningId(UUID screeningId) {
        this.screeningId = screeningId;
    }

    public String getEnrollmentCode() {
        return enrollmentCode;
    }

    public void setEnrollmentCode(String enrollmentCode) {
        this.enrollmentCode = enrollmentCode;
    }

    public OffsetDateTime getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(OffsetDateTime enrolledAt) {
        this.enrolledAt = enrolledAt;
    }

    public String getArm() {
        return arm;
    }

    public void setArm(String arm) {
        this.arm = arm;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ResearchParticipant getParticipant() {
        return participant;
    }

    public void setParticipant(ResearchParticipant participant) {
        this.participant = participant;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public PatientSnapshot getPatientSnapshot() {
        return patientSnapshot;
    }

    public void setPatientSnapshot(PatientSnapshot patientSnapshot) {
        this.patientSnapshot = patientSnapshot;
    }

    public Trial getTrial() {
        return trial;
    }

    public void setTrial(Trial trial) {
        this.trial = trial;
    }

    public TrialVersion getTrialVersion() {
        return trialVersion;
    }

    public void setTrialVersion(TrialVersion trialVersion) {
        this.trialVersion = trialVersion;
    }

    public Screening getScreening() {
        return screening;
    }

    public void setScreening(Screening screening) {
        this.screening = screening;
    }
}
