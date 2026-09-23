package com.trialsync.backend.dto.trial;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.trialsync.backend.entity.enums.VersionStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Port of {@code trialsync.schemas.VersionCreate}, used by both {@code POST .../versions} and
 * {@code PUT .../versions/{id}}.
 *
 * <p>{@code status} defaults to {@code draft} when the key is absent but must be a valid label when
 * it is present - including rejecting an explicit {@code null}, which the Pydantic enum also
 * refused. {@link VersionStatus#fromValue} answers {@code null} for an unknown label rather than
 * throwing, so the check lives in the setter: raising there makes Jackson fail the body and produces
 * the same {@code REQUEST_VALIDATION_ERROR} 422 the Pydantic enum error produced, instead of letting
 * a null reach the database and surface as a constraint violation.
 */
public class VersionCreateRequest {

    @NotNull
    @Min(1)
    private Integer version;

    private VersionStatus status = VersionStatus.draft;

    @Size(max = 100_000)
    private String sourceText;

    public Integer getVersion() {
        return version;
    }

    @JsonProperty("version")
    public void setVersion(Integer version) {
        this.version = version;
    }

    public VersionStatus getStatus() {
        return status;
    }

    @JsonProperty("status")
    public void setStatus(String raw) {
        VersionStatus parsed = VersionStatus.fromValue(raw);
        if (parsed == null) {
            throw new IllegalArgumentException("Input should be 'draft' or 'approved'");
        }
        this.status = parsed;
    }

    public String getSourceText() {
        return sourceText;
    }

    @JsonProperty("source_text")
    public void setSourceText(String sourceText) {
        this.sourceText = sourceText;
    }
}
