package com.trialsync.backend.dto.trial;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.trialsync.backend.domain.model.CriterionKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * Port of {@code trialsync.schemas.CriterionCreate}, used by the free-form criterion create and
 * update endpoints.
 *
 * <p>{@code required} defaults to {@code true} when omitted, and {@code kind} must be a valid label:
 * both are enforced through setters for the same reason as {@link VersionCreateRequest}, because
 * {@link CriterionKind#fromValue} answers {@code null} for an unknown label.
 *
 * <p>{@code normalized_rule} is declared as a map so a JSON array or scalar is refused during
 * binding, matching the Pydantic {@code dict[str, Any] | None} annotation.
 */
public class CriterionCreateRequest {

    @NotNull private CriterionKind kind;

    @NotNull
    @Min(1)
    private Integer order;

    @NotNull
    @Size(min = 1, max = 10_000)
    private String sourceText;

    private Map<String, Object> normalizedRule;

    @NotNull private Boolean required = Boolean.TRUE;

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

    public Integer getOrder() {
        return order;
    }

    @JsonProperty("order")
    public void setOrder(Integer order) {
        this.order = order;
    }

    public String getSourceText() {
        return sourceText;
    }

    @JsonProperty("source_text")
    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }

    public Map<String, Object> getNormalizedRule() {
        return normalizedRule;
    }

    @JsonProperty("normalized_rule")
    public void setNormalizedRule(Map<String, Object> normalizedRule) {
        this.normalizedRule = normalizedRule;
    }

    public Boolean getRequired() {
        return required;
    }

    @JsonProperty("required")
    public void setRequired(Boolean required) {
        // Pydantic's bool field rejects an explicit null; leaving the default in place instead would
        // silently accept it.
        if (required == null) {
            throw new IllegalArgumentException("Input should be a valid boolean");
        }
        this.required = required;
    }
}
