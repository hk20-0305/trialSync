package com.trialsync.backend.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Port of {@code trialsync.schemas.TokenResponse}.
 *
 * <p>{@code token_type} is a constant {@code "bearer"} in the Python model rather than a computed
 * value, so it is defaulted here instead of being passed at every call site.
 */
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        UserRead user) {

    public static final String BEARER = "bearer";

    public TokenResponse(String accessToken, UserRead user) {
        this(accessToken, BEARER, user);
    }
}
