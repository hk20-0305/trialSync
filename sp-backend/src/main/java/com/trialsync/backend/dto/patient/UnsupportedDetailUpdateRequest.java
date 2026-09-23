package com.trialsync.backend.dto.patient;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of {@code PATCH /api/v1/patients/{patient_id}/unsupported-details/{detail_id}}.
 *
 * <p>Supplied-field tracking matters twice here: it decides which columns are written, and an
 * update that supplies nothing but the staleness guard performs no write at all, leaving
 * {@code updated_at} untouched.
 */
public class UnsupportedDetailUpdateRequest {

    private String category;
    private boolean categorySupplied;

    private String label;
    private boolean labelSupplied;

    private String context;
    private boolean contextSupplied;

    private OffsetDateTime expectedUpdatedAt;

    @JsonProperty("category")
    public void setCategory(String category) {
        this.category = category;
        this.categorySupplied = true;
    }

    @JsonProperty("label")
    public void setLabel(String label) {
        this.label = label;
        this.labelSupplied = true;
    }

    @JsonProperty("context")
    public void setContext(String context) {
        this.context = context;
        this.contextSupplied = true;
    }

    @JsonProperty("expected_updated_at")
    public void setExpectedUpdatedAt(OffsetDateTime expectedUpdatedAt) {
        this.expectedUpdatedAt = expectedUpdatedAt;
    }

    public String getCategory() {
        return category;
    }

    public boolean isCategorySupplied() {
        return categorySupplied;
    }

    public String getLabel() {
        return label;
    }

    public boolean isLabelSupplied() {
        return labelSupplied;
    }

    public String getContext() {
        return context;
    }

    public boolean isContextSupplied() {
        return contextSupplied;
    }

    public OffsetDateTime getExpectedUpdatedAt() {
        return expectedUpdatedAt;
    }

    /** True when the caller supplied at least one editable field. */
    public boolean hasChanges() {
        return categorySupplied || labelSupplied || contextSupplied;
    }
}
