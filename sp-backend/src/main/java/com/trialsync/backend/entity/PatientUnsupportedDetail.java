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
 * A clinical detail that has no catalog concept and therefore cannot take part in screening.
 *
 * <p>{@code category} is constrained to {@code condition}, {@code medication}, {@code observation}
 * or {@code other} by {@code ck_patient_unsupported_detail_category}; it is a {@code varchar}, not a
 * PostgreSQL enum.
 */
@Entity
@Table(name = "patient_unsupported_details")
public class PatientUnsupportedDetail extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Column(name = "category", nullable = false, length = 24)
    private String category;

    @Column(name = "label", nullable = false, length = 160)
    private String label;

    @Column(name = "context", length = 500)
    private String context;

    @Column(name = "source_label", nullable = false, length = 120)
    private String sourceLabel = "Manual review item";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", insertable = false, updatable = false)
    private Patient patient;

    protected PatientUnsupportedDetail() {
        // Required by JPA.
    }

    public PatientUnsupportedDetail(UUID patientId, String category, String label) {
        this.patientId = patientId;
        this.category = category;
        this.label = label;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPatientId() {
        return patientId;
    }

    public void setPatientId(UUID patientId) {
        this.patientId = patientId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public Patient getPatient() {
        return patient;
    }
}
