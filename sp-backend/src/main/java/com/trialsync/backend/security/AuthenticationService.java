package com.trialsync.backend.security;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.entity.User;
import com.trialsync.backend.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a bearer token to a user. Port of {@code get_current_user}: a missing or malformed
 * credential is {@code AUTHENTICATION_REQUIRED}, while a well-formed but unusable token — bad
 * signature, expired, or pointing at a deleted user — is {@code INVALID_TOKEN}.
 */
@Service
public class AuthenticationService {

    private final JwtTokenService tokenService;
    private final UserRepository users;

    public AuthenticationService(JwtTokenService tokenService, UserRepository users) {
        this.tokenService = tokenService;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User authenticate(String authorizationHeader) {
        String credential = extractBearerCredential(authorizationHeader);
        if (credential == null) {
            throw new ApplicationError("AUTHENTICATION_REQUIRED", "Sign in is required.", 401);
        }
        UUID userId = tokenService.decodeAccessToken(credential);
        Optional<User> user =
                userId == null ? Optional.empty() : users.findById(userId);
        return user.orElseThrow(
                () ->
                        new ApplicationError(
                                "INVALID_TOKEN", "The access token is invalid or expired.", 401));
    }

    /**
     * Extracts the credential from an {@code Authorization: Bearer <token>} header. The scheme is
     * compared case-insensitively, matching {@code credentials.scheme.lower() != "bearer"}.
     */
    static String extractBearerCredential(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        int separator = header.indexOf(' ');
        if (separator < 0) {
            return null;
        }
        String scheme = header.substring(0, separator);
        if (!"bearer".equalsIgnoreCase(scheme)) {
            return null;
        }
        String credential = header.substring(separator + 1).trim();
        return credential.isEmpty() ? null : credential;
    }
}
