package com.trialsync.backend.controller;

import com.trialsync.backend.dto.auth.LoginRequest;
import com.trialsync.backend.dto.auth.TokenResponse;
import com.trialsync.backend.dto.auth.UserCreateRequest;
import com.trialsync.backend.dto.auth.UserRead;
import com.trialsync.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of {@code trialsync.api.auth}.
 *
 * <p>{@code register} and {@code login} are the only two routes the authentication filter lets
 * through unauthenticated; {@code me} is what the client calls on start-up to turn a stored token
 * back into a session.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody UserCreateRequest payload) {
        return authService.register(payload);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest payload) {
        return authService.login(payload);
    }

    @GetMapping("/me")
    public UserRead me() {
        return authService.currentUser();
    }
}
