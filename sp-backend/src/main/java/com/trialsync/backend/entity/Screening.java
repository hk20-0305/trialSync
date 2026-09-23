package com.trialsync.backend.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

import com.trialsync.backend.domain.model.OverallState;
import com.trialsync.backend.entity.type.OverallStateUserType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * A completed screening of one snapshot against one approved trial version.
 *
 * <p>The trial's registry id, title and version number are copied in at write time rather than
 * joined at read time, so a saved result always presents the labels that were shown when it ran.
 * The four version strings pin the engine, DSL, terminology and unit tables used.
 */
@Entity
@Table(name = "screenings")
public class Screening extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "batch_id", columnDefinition = "uuid")
    private UUID batchId;

    @Column(name = "patient_snapshot_id", nullable = false, columnDefinition = "uuid")
    private UUID patientSnapshotId;

    @Column(name = "trial_version_id", nullable = false, columnDefinition = "uuid")
    private UUID trialVersionId;

    @Column(name = "trial_registry_id", nullable = false, length = 64)
    private String trialRegistryId;

    @Column(name = "trial_title", nullable = false, length = 240)
    private String trialTitle;

    @Column(name = "trial_version_number", nullable = false)
    private int trialVersionNumber;

    @Type(OverallStateUserType.class)
    @Column(name = "overall_state", nullable = false, columnDefinition = "overall_state")
    private OverallState overallState;

    @Column(name = "screening_date", nullable = false)
    private LocalDate screeningDate;

    @Column(name = "engine_version", nullable = false, length = 40)
    private String engineVersion;

    @Column(name = "dsl_version", nullable = false, length = 20)
    private String dslVersion;

    @Column(name = "terminology_version", nullable = false, length = 40)
    private String terminologyVersion;

    @Column(name = "unit_version", nullable = false, length = 40)
    private String unitVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", insertable = false, updatable = false)
    private ScreeningBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_snapshot_id", insertable = false, updatable = false)
    private PatientSnapshot patientSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trial_version_id", insertable = false, updatable = false)
    private TrialVersion trialVersion;

    @OneToMany(mappedBy = "screening", fetch = FetchType.LAZY)
    @OrderBy("criterionOrder")
    @BatchSize(size = 100)
    private List<CriterionEvaluation> evaluations = new ArrayList<>();

    @OneToMany(mappedBy = "screening", fetch = FetchType.LAZY)
    @OrderBy("createdAt")
    @BatchSize(size = 100)
    private List<ScreeningChatMessage> chatMessages = new ArrayList<>();

    protected Screening() {
        // Required by JPA.
    }

    public Screening(UUID ownerId, UUID patientSnapshotId, UUID trialVersionId) {
        this.ownerId = ownerId;
        this.patientSnapshotId = patientSnapshotId;
        this.trialVersionId = trialVersionId;
    }

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

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public UUID getPatientSnapshotId() {
        return patientSnapshotId;
    }

    public void setPatientSnapshotId(UUID patientSnapshotId) {
        this.patientSnapshotId = patientSnapshotId;
    }

    public UUID getTrialVersionId() {
        return trialVersionId;
    }

    public void setTrialVersionId(UUID trialVersionId) {
        this.trialVersionId = trialVersionId;
    }

    public String getTrialRegistryId() {
        return trialRegistryId;
    }

    public void setTrialRegistryId(String trialRegistryId) {
        this.trialRegistryId = trialRegistryId;
    }

    public String getTrialTitle() {
        return trialTitle;
    }

    public void setTrialTitle(String trialTitle) {
        this.trialTitle = trialTitle;
    }

    public int getTrialVersionNumber() {
        return trialVersionNumber;
    }

    public void setTrialVersionNumber(int trialVersionNumber) {
        this.trialVersionNumber = trialVersionNumber;
    }

    public OverallState getOverallState() {
        return overallState;
    }

    public void setOverallState(OverallState overallState) {
        this.overallState = overallState;
    }

    public LocalDate getScreeningDate() {
        return screeningDate;
    }

    public void setScreeningDate(LocalDate screeningDate) {
        this.screeningDate = screeningDate;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public String getDslVersion() {
        return dslVersion;
    }

    public void setDslVersion(String dslVersion) {
        this.dslVersion = dslVersion;
    }

    public String getTerminologyVersion() {
        return terminologyVersion;
    }

    public void setTerminologyVersion(String terminologyVersion) {
        this.terminologyVersion = terminologyVersion;
    }

    public String getUnitVersion() {
        return unitVersion;
    }

    public void setUnitVersion(String unitVersion) {
        this.unitVersion = unitVersion;
    }

    public User getOwner() {
        return owner;
    }

    public ScreeningBatch getBatch() {
        return batch;
    }

    public PatientSnapshot getPatientSnapshot() {
        return patientSnapshot;
    }

    public TrialVersion getTrialVersion() {
        return trialVersion;
    }

    public List<CriterionEvaluation> getEvaluations() {
        return evaluations;
    }

    public List<ScreeningChatMessage> getChatMessages() {
        return chatMessages;
    }
}
