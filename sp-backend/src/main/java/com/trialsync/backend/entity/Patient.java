package com.trialsync.backend.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

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
 * A synthetic person record. The mutable source of truth that screening snapshots are frozen from.
 */
@Entity
@Table(name = "patients")
public class Patient extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /**
     * Constrained to {@code male} or {@code female} by {@code ck_patients_biological_sex}. Held as a
     * plain string because the column is {@code varchar}, not a PostgreSQL enum.
     */
    @Column(name = "sex", length = 32)
    private String sex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", insertable = false, updatable = false)
    private User owner;

    /**
     * Only facts that have not been voided, matching the Python relationship's {@code primaryjoin}.
     * Voided facts stay in the table and are reachable through the repository.
     */
    @OneToMany(mappedBy = "patient", fetch = FetchType.LAZY)
    @SQLRestriction("voided_at is null")
    @OrderBy("createdAt")
    @BatchSize(size = 100)
    private List<PatientFact> facts = new ArrayList<>();

    @OneToMany(mappedBy = "patient", fetch = FetchType.LAZY)
    @OrderBy("createdAt")
    @BatchSize(size = 100)
    private List<PatientUnsupportedDetail> unsupportedDetails = new ArrayList<>();

    @OneToMany(mappedBy = "patient", fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    private List<PatientSnapshot> snapshots = new ArrayList<>();

    @OneToMany(mappedBy = "patient", fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC")
    @BatchSize(size = 100)
    private List<PatientChangeEvent> activity = new ArrayList<>();

    protected Patient() {
        // Required by JPA.
    }

    public Patient(UUID ownerId, String externalId, String displayName) {
        this.ownerId = ownerId;
        this.externalId = externalId;
        this.displayName = displayName;
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

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public User getOwner() {
        return owner;
    }

    public List<PatientFact> getFacts() {
        return facts;
    }

    public List<PatientUnsupportedDetail> getUnsupportedDetails() {
        return unsupportedDetails;
    }

    public List<PatientSnapshot> getSnapshots() {
        return snapshots;
    }

    public List<PatientChangeEvent> getActivity() {
        return activity;
    }
}
