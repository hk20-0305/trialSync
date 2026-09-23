package com.trialsync.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Port of {@code trialsync.schemas.LoginRequest}.
 *
 * <p>The password bound is {@code 1..128} rather than the registration bound of {@code 10..128}:
 * an account created before the stricter rule must still be able to sign in.
 */
public record LoginRequest(
        @NotNull
                @Email
                @Pattern(
                        regexp = "^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$",
                        message = "value is not a valid email address")
                String email,
        @NotNull @Size(min = 1, max = 128) String password) {}
