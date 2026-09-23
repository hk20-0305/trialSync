package com.trialsync.backend.dto.patient;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of {@code PATCH /api/v1/patients/{patient_id}}.
 *
 * <p>Not a record: the endpoint applies {@code payload.model_dump(exclude_unset=True)}, so an
 * omitted field and an explicit {@code null} must behave differently — omitting {@code sex} leaves
 * it untouched while sending {@code "sex": null} clears it. Jackson invokes a setter only for
 * properties actually present in the document (including explicit nulls), so each setter records
 * that the caller supplied the field. That reproduces Pydantic's {@code model_fields_set}.
 */
public class PatientUpdateRequest {

    private String externalId;
    private boolean externalIdSupplied;

    private String displayName;
    private boolean displayNameSupplied;

    private LocalDate dateOfBirth;
    private boolean dateOfBirthSupplied;

    private String sex;
    private boolean sexSupplied;

    private OffsetDateTime expectedUpdatedAt;

    @JsonProperty("external_id")
    public void setExternalId(String externalId) {
        this.externalId = externalId;
        this.externalIdSupplied = true;
    }

    @JsonProperty("display_name")
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.displayNameSupplied = true;
    }

    @JsonProperty("date_of_birth")
    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        this.dateOfBirthSupplied = true;
    }

    @JsonProperty("sex")
    public void setSex(String sex) {
        this.sex = sex;
        this.sexSupplied = true;
    }

    @JsonProperty("expected_updated_at")
    public void setExpectedUpdatedAt(OffsetDateTime expectedUpdatedAt) {
        this.expectedUpdatedAt = expectedUpdatedAt;
    }

    public String getExternalId() {
        return externalId;
    }

    public boolean isExternalIdSupplied() {
        return externalIdSupplied;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isDisplayNameSupplied() {
        return displayNameSupplied;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public boolean isDateOfBirthSupplied() {
        return dateOfBirthSupplied;
    }

    public String getSex() {
        return sex;
    }

    public boolean isSexSupplied() {
        return sexSupplied;
    }

    public OffsetDateTime getExpectedUpdatedAt() {
        return expectedUpdatedAt;
    }

    /** True when the caller supplied at least one field other than the staleness guard. */
    public boolean hasProfileChanges() {
        return externalIdSupplied || displayNameSupplied || dateOfBirthSupplied || sexSupplied;
    }
}
