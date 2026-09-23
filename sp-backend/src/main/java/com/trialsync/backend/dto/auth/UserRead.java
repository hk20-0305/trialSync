package com.trialsync.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.trialsync.backend.entity.User;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Port of {@code trialsync.schemas.UserRead}. The password hash is deliberately absent, matching the
 * Python response model.
 *
 * <p>Component order reproduces the Pydantic field order so the serialised object keys appear in the
 * same sequence the React client already receives.
 */
public record UserRead(
        UUID id,
        String email,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("is_catalog_admin") boolean catalogAdmin,
        @JsonProperty("created_at") OffsetDateTime createdAt) {

    /** Equivalent of {@code UserRead.model_validate(user)}. */
    public static UserRead of(User user) {
        return new UserRead(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.isCatalogAdmin(),
                user.getCreatedAt());
    }
}
