package com.trialsync.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Type;

import com.trialsync.backend.domain.model.Assertion;
import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.entity.type.AssertionUserType;
import com.trialsync.backend.entity.type.FactTypeUserType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One recorded clinical detail about a patient.
 *
 * <p>Facts are never hard-deleted by the API: voiding sets {@code voided_at}, {@code void_reason}
 * and {@code voided_by_id}, and the voided row stays for the activity trail.
 */
@Entity
@Table(name = "patient_facts")
public class PatientFact extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "patient_id", nullable = false, columnDefinition = "uuid")
    private UUID patientId;

    @Type(FactTypeUserType.class)
    @Column(name = "fact_type", nullable = false, columnDefinition = "fact_type")
    private FactType factType;

    @Column(name = "concept", nullable = false, length = 160)
    private String concept;

    @Column(name = "value_numeric", precision = 18, scale = 6)
    private BigDecimal valueNumeric;

    @Column(name = "value_text", length = 500)
    private String valueText;

    @Column(name = "unit", length = 40)
    private String unit;

    @Type(AssertionUserType.class)
    @Column(name = "assertion", nullable = false, columnDefinition = "fact_assertion")
    private Assertion assertion = Assertion.PRESENT;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "source_label", nullable = false, length = 120)
    private String sourceLabel = "Manual entry";

    @Column(name = "voided_at", columnDefinition = "timestamptz")
    private OffsetDateTime voidedAt;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_by_id", columnDefinition = "uuid")
    private UUID voidedById;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", insertable = false, updatable = false)
    private Patient patient;

    protected PatientFact() {
        // Required by JPA.
    }

    public PatientFact(UUID patientId, FactType factType, String concept) {
        this.patientId = patientId;
        this.factType = factType;
        this.concept = concept;
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

    public FactType getFactType() {
        return factType;
    }

    public void setFactType(FactType factType) {
        this.factType = factType;
    }

    public String getConcept() {
        return concept;
    }

    public void setConcept(String concept) {
        this.concept = concept;
    }

    public BigDecimal getValueNumeric() {
        return valueNumeric;
    }

    public void setValueNumeric(BigDecimal valueNumeric) {
        this.valueNumeric = valueNumeric;
    }

    public String getValueText() {
        return valueText;
    }

    public void setValueText(String valueText) {
        this.valueText = valueText;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Assertion getAssertion() {
        return assertion;
    }

    public void setAssertion(Assertion assertion) {
        this.assertion = assertion;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public OffsetDateTime getVoidedAt() {
        return voidedAt;
    }

    public void setVoidedAt(OffsetDateTime voidedAt) {
        this.voidedAt = voidedAt;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }

    public UUID getVoidedById() {
        return voidedById;
    }

    public void setVoidedById(UUID voidedById) {
        this.voidedById = voidedById;
    }

    public Patient getPatient() {
        return patient;
    }
}
