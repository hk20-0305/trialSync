package com.trialsync.backend.dto.trial;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trialsync.backend.domain.model.CriterionKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Port of {@code trialsync.schemas.GuidedCriterionCreate}: the payload of the guided rule builder,
 * where the caller picks a catalog subject and an operator and the service derives both the
 * human-readable text and the deterministic rule.
 *
 * <p>{@code operator} and {@code biological_sex} are held as strings constrained by patterns rather
 * than as enums. That is deliberate: the Pydantic {@code Literal}/enum produced a validation error
 * (HTTP 422, {@code REQUEST_VALIDATION_ERROR}) for an unrecognised value, and a bean-validation
 * failure produces exactly that, whereas an enum whose Jackson creator answers {@code null} would
 * have leaked the bad value into the rule-building branch and raised a different code.
 *
 * <p>The Pydantic model sets {@code extra="forbid"}, so an unexpected key is an error rather than
 * being ignored. The application-wide Jackson configuration disables
 * {@code FAIL_ON_UNKNOWN_PROPERTIES}, and {@code @JsonIgnoreProperties(ignoreUnknown = false)} does
 * not re-enable it, so the rejection is implemented with an any-setter that refuses every key it is
 * handed.
 */
public class GuidedCriterionCreateRequest {

    @NotNull private CriterionKind kind;

    @NotNull
    @Pattern(regexp = "^[a-z0-9_]+$")
    @Size(min = 1, max = 80)
    private String subjectKey;

    @NotNull
    @Pattern(regexp = "present|absent|gte|lte|between|is")
    private String operator;

    private BigDecimal value;

    private BigDecimal minimum;

    private BigDecimal maximum;

    @Pattern(regexp = "male|female")
    private String biologicalSex;

    public CriterionKind getKind() {
        return kind;
    }

    @JsonProperty("kind")
    public void setKind(String raw) {
        CriterionKind parsed = CriterionKind.fromValue(raw);
        if (parsed == null) {
            throw new IllegalArgumentException("Input should be 'inclusion' or 'exclusion'");
        }
        this.kind = parsed;
    }

    public String getSubjectKey() {
        return subjectKey;
    }

    @JsonProperty("subject_key")
    public void setSubjectKey(String subjectKey) {
        this.subjectKey = subjectKey;
    }

    public String getOperator() {
        return operator;
    }

    @JsonProperty("operator")
    public void setOperator(String operator) {
        this.operator = operator;
    }

    public BigDecimal getValue() {
        return value;
    }

    @JsonProperty("value")
    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public BigDecimal getMinimum() {
        return minimum;
    }

    @JsonProperty("minimum")
    public void setMinimum(BigDecimal minimum) {
        this.minimum = minimum;
    }

    public BigDecimal getMaximum() {
        return maximum;
    }

    @JsonProperty("maximum")
    public void setMaximum(BigDecimal maximum) {
        this.maximum = maximum;
    }

    public String getBiologicalSex() {
        return biologicalSex;
    }

    @JsonProperty("biological_sex")
    public void setBiologicalSex(String biologicalSex) {
        this.biologicalSex = biologicalSex;
    }

    /** Reproduces {@code model_config = ConfigDict(extra="forbid")}. */
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignored) {
        throw new IllegalArgumentException("Extra inputs are not permitted: " + name);
    }
}
