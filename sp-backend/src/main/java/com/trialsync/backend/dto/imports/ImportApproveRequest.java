package com.trialsync.backend.dto.imports;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Port of {@code trialsync.imports.schemas.ImportApproveRequest}.
 *
 * <p>{@code confirm_duplicate_name} defaults to false, so an omitted body still means "do not
 * silently create a second patient with the same display name".
 */
public record ImportApproveRequest(
        @JsonProperty("confirm_duplicate_name") Boolean confirmDuplicateName) {

    /** The Pydantic default applied to a missing or null field. */
    public boolean confirmDuplicateNameOrDefault() {
        return Boolean.TRUE.equals(confirmDuplicateName);
    }
}
