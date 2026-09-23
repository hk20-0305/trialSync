package com.trialsync.backend.service;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.dto.auth.LoginRequest;
import com.trialsync.backend.dto.auth.TokenResponse;
import com.trialsync.backend.dto.auth.UserCreateRequest;
import com.trialsync.backend.dto.auth.UserRead;
import com.trialsync.backend.entity.User;
import com.trialsync.backend.repository.UserRepository;
import com.trialsync.backend.security.JwtTokenService;
import com.trialsync.backend.security.Pbkdf2PasswordHasher;
import com.trialsync.backend.security.SecurityContext;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Port of {@code trialsync.api.auth}. */
@Service
public class AuthService {

    private final UserRepository users;
    private final Pbkdf2PasswordHasher passwordHasher;
    private final JwtTokenService tokenService;

    public AuthService(
            UserRepository users, Pbkdf2PasswordHasher passwordHasher, JwtTokenService tokenService) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    /**
     * Creates an account and returns a token for it.
     *
     * <p>The email is lower-cased on the way in because {@code ix_users_email} is a plain equality
     * index and every later lookup lower-cases too, and the display name is trimmed only after the
     * length bound has been checked - the Pydantic model measured the raw value and the endpoint
     * stripped afterwards, so a name of spaces around a single letter is accepted and stored as that
     * letter.
     *
     * <p>Duplicate registration is decided by the unique index rather than by a pre-check, so two
     * concurrent registrations cannot both succeed. Spring surfaces the violation as
     * {@link DataIntegrityViolationException} and it is not covered by the global handler, so
     * catching it here is what turns it into the 409 the client expects instead of a 500.
     */
    @Transactional
    public TokenResponse register(UserCreateRequest payload) {
        User user =
                new User(
                        payload.email().toLowerCase(Locale.ROOT),
                        payload.displayName().strip(),
                        passwordHasher.hash(payload.password()));
        try {
            // Reassigned because the id is pre-assigned in the constructor, so Spring Data routes the
            // save through merge and the generated timestamps land on the managed copy it returns.
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ApplicationError(
                    "EMAIL_ALREADY_REGISTERED",
                    "An account with this email already exists.",
                    409,
                    "email");
        }
        return new TokenResponse(tokenService.createAccessToken(user.getId()), UserRead.of(user));
    }

    /**
     * Verifies credentials and issues a token.
     *
     * <p>The same error is returned for an unknown address and a wrong password so the response
     * cannot be used to enumerate accounts.
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest payload) {
        Optional<User> found = users.findByEmail(payload.email().toLowerCase(Locale.ROOT));
        User user =
                found.filter(
                                candidate ->
                                        passwordHasher.verify(
                                                payload.password(), candidate.getPasswordHash()))
                        .orElseThrow(
                                () ->
                                        new ApplicationError(
                                                "INVALID_CREDENTIALS",
                                                "Email or password is incorrect.",
                                                401));
        return new TokenResponse(tokenService.createAccessToken(user.getId()), UserRead.of(user));
    }

    /** The signed-in account, as resolved by the authentication filter. */
    public UserRead currentUser() {
        return UserRead.of(SecurityContext.require());
    }
}
