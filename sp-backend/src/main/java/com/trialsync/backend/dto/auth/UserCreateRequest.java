package com.trialsync.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Port of {@code trialsync.schemas.UserCreate}.
 *
 * <p>The bounds are the Pydantic bounds verbatim, so a rejected payload leaves the service as the
 * same {@code REQUEST_VALIDATION_ERROR} envelope with the same HTTP 422.
 *
 * <p>{@code display_name} is validated against the raw value and only trimmed afterwards by the
 * service, exactly as {@code api/auth.py} does: Pydantic enforced {@code min_length=2} before the
 * endpoint applied {@code .strip()}, so {@code "  a  "} is accepted and stored as {@code "a"}.
 */
public record UserCreateRequest(
        // Pydantic's EmailStr rejects addresses without a dotted domain, which Jakarta's @Email
        // permits, so the extra pattern restores the stricter contract the frontend already meets.
        @NotNull
                @Email
                @Pattern(
                        regexp = "^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$",
                        message = "value is not a valid email address")
                String email,
        @JsonProperty("display_name") @NotNull @Size(min = 2, max = 100) String displayName,
        @NotNull @Size(min = 10, max = 128) String password) {}
