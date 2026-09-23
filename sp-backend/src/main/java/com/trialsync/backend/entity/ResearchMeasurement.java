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
 * Longitudinal laboratory result, vital sign, or clinical measurement.
 */
@Entity
@Table(name = "research_measurements")
public class ResearchMeasurement extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "enrollment_id", nullable = false, columnDefinition = "uuid")
    private UUID enrollmentId;

    @Column(name = "visit_event_id", columnDefinition = "uuid")
    private UUID visitEventId;

    @Column(name = "measurement_day", nullable = false)
    private int measurementDay;

    @Column(name = "measured_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime measuredAt;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", length = 120)
    private String name;

    @Column(name = "numeric_value", precision = 14, scale = 4)
    private BigDecimal numericValue;

    @Column(name = "text_value", length = 255)
    private String textValue;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "reference_range_low", precision = 14, scale = 4)
    private BigDecimal referenceRangeLow;

    @Column(name = "reference_range_high", precision = 14, scale = 4)
    private BigDecimal referenceRangeHigh;

    @Column(name = "is_abnormal")
    private Boolean isAbnormal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", insertable = false, updatable = false)
    private ResearchEnrollment enrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_event_id", insertable = false, updatable = false)
    private ResearchVisitEvent visitEvent;

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

    public UUID getVisitEventId() {
        return visitEventId;
    }

    public void setVisitEventId(UUID visitEventId) {
        this.visitEventId = visitEventId;
    }

    public int getMeasurementDay() {
        return measurementDay;
    }

    public void setMeasurementDay(int measurementDay) {
        this.measurementDay = measurementDay;
    }

    public OffsetDateTime getMeasuredAt() {
        return measuredAt;
    }

    public void setMeasuredAt(OffsetDateTime measuredAt) {
        this.measuredAt = measuredAt;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getNumericValue() {
        return numericValue;
    }

    public void setNumericValue(BigDecimal numericValue) {
        this.numericValue = numericValue;
    }

    public String getTextValue() {
        return textValue;
    }

    public void setTextValue(String textValue) {
        this.textValue = textValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getReferenceRangeLow() {
        return referenceRangeLow;
    }

    public void setReferenceRangeLow(BigDecimal referenceRangeLow) {
        this.referenceRangeLow = referenceRangeLow;
    }

    public BigDecimal getReferenceRangeHigh() {
        return referenceRangeHigh;
    }

    public void setReferenceRangeHigh(BigDecimal referenceRangeHigh) {
        this.referenceRangeHigh = referenceRangeHigh;
    }

    public Boolean getIsAbnormal() {
        return isAbnormal;
    }

    public void setIsAbnormal(Boolean isAbnormal) {
        this.isAbnormal = isAbnormal;
    }

    public ResearchEnrollment getEnrollment() {
        return enrollment;
    }

    public void setEnrollment(ResearchEnrollment enrollment) {
        this.enrollment = enrollment;
    }

    public ResearchVisitEvent getVisitEvent() {
        return visitEvent;
    }

    public void setVisitEvent(ResearchVisitEvent visitEvent) {
        this.visitEvent = visitEvent;
    }
}
