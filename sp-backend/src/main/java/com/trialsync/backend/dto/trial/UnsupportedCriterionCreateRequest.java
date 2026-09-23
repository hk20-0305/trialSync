package com.trialsync.backend.dto.trial;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.trialsync.backend.domain.model.CriterionKind;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Port of {@code trialsync.schemas.UnsupportedCriterionCreate}: a criterion a reviewer read but
 * could not express as a deterministic rule, recorded so the version cannot be approved until it is
 * resolved.
 *
 * <p>{@code category} is validated and then never used, exactly as in the Python endpoint - it is
 * part of the request contract the frontend sends but the stored row keeps only the kind and the
 * text.
 *
 * <p>{@code source_text} is whitespace-collapsed <em>before</em> the length bound is applied, which
 * is what the Pydantic {@code mode="before"} validator did. A body of only whitespace therefore
 * collapses to the empty string and fails {@code min_length=1} rather than being stored blank.
 */
public class UnsupportedCriterionCreateRequest {

    @NotNull private CriterionKind kind;

    @NotNull
    @Pattern(regexp = "demographic|condition|medication|observation|other")
    private String category;

    @NotNull
    @Size(min = 1, max = 10_000)
    private String sourceText;

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

    public String getCategory() {
        return category;
    }

    @JsonProperty("category")
    public void setCategory(String category) {
        this.category = category;
    }

    public String getSourceText() {
        return sourceText;
    }

    @JsonProperty("source_text")
    public void setSourceText(String sourceText) {
        this.sourceText = collapseWhitespace(sourceText);
    }

    /**
     * Equivalent of {@code " ".join(value.split())}. {@code (?U)} makes {@code \s} Unicode-aware so a
     * non-breaking space is collapsed the way Python's {@code str.split()} collapses it.
     */
    static String collapseWhitespace(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? "" : trimmed.replaceAll("(?U)\\s+", " ");
    }

    /** Reproduces {@code model_config = ConfigDict(extra="forbid")}. */
    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object ignored) {
        throw new IllegalArgumentException("Extra inputs are not permitted: " + name);
    }
}
