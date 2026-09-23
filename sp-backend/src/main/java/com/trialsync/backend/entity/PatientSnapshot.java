package com.trialsync.backend.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

import com.trialsync.backend.entity.type.JsonStringUserType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * An immutable copy of the patient inputs a screening was run against.
 *
 * <p>{@code content_hash} is a canonical SHA-256 of the facts, and is reused as
 * {@code snapshot_version}; the unique constraint on {@code (patient_id, content_hash)} means an
 * unchanged patient reuses its snapshot instead of creating another one.
 *
 * <p>{@code patient_id} is nullable and its foreign key is {@code ON DELETE SET NULL}: deleting a
 * patient must not destroy the evidence behind past screenings.
 */
@Entity
@Table(name = "patient_snapshots")
public class PatientSnapshot extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "patient_id", columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "snapshot_version", nullable = false, length = 64)
    private String snapshotVersion;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Type(JsonStringUserType.class)
    @Column(name = "facts_json", nullable = false, columnDefinition = "json")
    private String factsJson;

    @Type(JsonStringUserType.class)
    @Column(name = "source_summary", nullable = false, columnDefinition = "json")
    private String sourceSummary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", insertable = false, updatable = false)
    private Patient patient;

    @OneToMany(mappedBy = "patientSnapshot", fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    private List<Screening> screenings = new ArrayList<>();

    protected PatientSnapshot() {
        // Required by JPA.
    }

    public PatientSnapshot(UUID ownerId, UUID patientId, String contentHash) {
        this.ownerId = ownerId;
        this.patientId = patientId;
        this.contentHash = contentHash;
        this.snapshotVersion = contentHash;
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

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(String snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getFactsJson() {
        return factsJson;
    }

    public void setFactsJson(String factsJson) {
        this.factsJson = factsJson;
    }

    public String getSourceSummary() {
        return sourceSummary;
    }

    public void setSourceSummary(String sourceSummary) {
        this.sourceSummary = sourceSummary;
    }

    public User getOwner() {
        return owner;
    }

    public Patient getPatient() {
        return patient;
    }

    public List<Screening> getScreenings() {
        return screenings;
    }
}
