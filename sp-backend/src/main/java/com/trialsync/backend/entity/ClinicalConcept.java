package com.trialsync.backend.entity;

import java.util.UUID;

import org.hibernate.annotations.Type;

import com.trialsync.backend.domain.model.FactType;
import com.trialsync.backend.entity.type.FactTypeUserType;
import com.trialsync.backend.entity.type.JsonStringUserType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A database-owned entry in the clinical catalog that patient and protocol entry select from.
 *
 * <p>Seeded with 25 frozen rows by migration {@code V9}. {@code concept_group} and
 * {@code input_kind} are {@code varchar} columns constrained by CHECKs, not PostgreSQL enums.
 */
@Entity
@Table(name = "clinical_concepts")
public class ClinicalConcept extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id = UUID.randomUUID();

    @Column(name = "key", nullable = false, length = 80, unique = true)
    private String key;

    @Type(FactTypeUserType.class)
    @Column(name = "fact_type", nullable = false, columnDefinition = "fact_type")
    private FactType factType;

    @Column(name = "concept", nullable = false, length = 160)
    private String concept;

    @Column(name = "display_label", nullable = false, length = 120)
    private String displayLabel;

    @Column(name = "concept_group", nullable = false, length = 24)
    private String conceptGroup;

    @Column(name = "input_kind", nullable = false, length = 24)
    private String inputKind;

    /** A JSON array of assertion wire values, for example {@code ["present", "absent", "unknown"]}. */
    @Type(JsonStringUserType.class)
    @Column(name = "allowed_assertions_json", nullable = false, columnDefinition = "json")
    private String allowedAssertionsJson;

    @Column(name = "fixed_unit", length = 40)
    private String fixedUnit;

    @Column(name = "effective_date_required", nullable = false)
    private boolean effectiveDateRequired = false;

    @Column(name = "screening_supported", nullable = false)
    private boolean screeningSupported = true;

    @Column(name = "help_text", nullable = false, length = 300)
    private String helpText;

    @Column(name = "terminology_system", length = 32)
    private String terminologySystem;

    @Column(name = "terminology_code", length = 80)
    private String terminologyCode;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected ClinicalConcept() {
        // Required by JPA.
    }

    public ClinicalConcept(String key, FactType factType, String concept, String displayLabel) {
        this.key = key;
        this.factType = factType;
        this.concept = concept;
        this.displayLabel = displayLabel;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
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

    public String getDisplayLabel() {
        return displayLabel;
    }

    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getConceptGroup() {
        return conceptGroup;
    }

    public void setConceptGroup(String conceptGroup) {
        this.conceptGroup = conceptGroup;
    }

    public String getInputKind() {
        return inputKind;
    }

    public void setInputKind(String inputKind) {
        this.inputKind = inputKind;
    }

    public String getAllowedAssertionsJson() {
        return allowedAssertionsJson;
    }

    public void setAllowedAssertionsJson(String allowedAssertionsJson) {
        this.allowedAssertionsJson = allowedAssertionsJson;
    }

    public String getFixedUnit() {
        return fixedUnit;
    }

    public void setFixedUnit(String fixedUnit) {
        this.fixedUnit = fixedUnit;
    }

    public boolean isEffectiveDateRequired() {
        return effectiveDateRequired;
    }

    public void setEffectiveDateRequired(boolean effectiveDateRequired) {
        this.effectiveDateRequired = effectiveDateRequired;
    }

    public boolean isScreeningSupported() {
        return screeningSupported;
    }

    public void setScreeningSupported(boolean screeningSupported) {
        this.screeningSupported = screeningSupported;
    }

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText(String helpText) {
        this.helpText = helpText;
    }

    public String getTerminologySystem() {
        return terminologySystem;
    }

    public void setTerminologySystem(String terminologySystem) {
        this.terminologySystem = terminologySystem;
    }

    public String getTerminologyCode() {
        return terminologyCode;
    }

    public void setTerminologyCode(String terminologyCode) {
        this.terminologyCode = terminologyCode;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
