package com.trialsync.backend.dto.concept;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Body of {@code PATCH /api/v1/clinical-concepts/{concept_id}}.
 *
 * <p>Only the three presentation fields are editable; changing a concept's key, group or input kind
 * would invalidate facts already recorded against it. Supplied-field tracking keeps an omitted
 * field from being overwritten with {@code null}.
 */
/**
 * {@code extra="forbid"}: an unrecognised key is rejected rather than dropped.
 *
 * <p>Explicit, because unknown properties are tolerated globally to match the patient profile
 * models, which Python leaves permissive. A silently ignored key here would let a caller believe
 * they had set something they had not.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class ClinicalConceptUpdateRequest {

    private String displayLabel;
    private boolean displayLabelSupplied;

    private Boolean screeningSupported;
    private boolean screeningSupportedSupplied;

    private String helpText;
    private boolean helpTextSupplied;

    @JsonProperty("display_label")
    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
        this.displayLabelSupplied = true;
    }

    @JsonProperty("screening_supported")
    public void setScreeningSupported(Boolean screeningSupported) {
        this.screeningSupported = screeningSupported;
        this.screeningSupportedSupplied = true;
    }

    @JsonProperty("help_text")
    public void setHelpText(String helpText) {
        this.helpText = helpText;
        this.helpTextSupplied = true;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public boolean isDisplayLabelSupplied() {
        return displayLabelSupplied;
    }

    public Boolean getScreeningSupported() {
        return screeningSupported;
    }

    public boolean isScreeningSupportedSupplied() {
        return screeningSupportedSupplied;
    }

    public String getHelpText() {
        return helpText;
    }

    public boolean isHelpTextSupplied() {
        return helpTextSupplied;
    }
}
