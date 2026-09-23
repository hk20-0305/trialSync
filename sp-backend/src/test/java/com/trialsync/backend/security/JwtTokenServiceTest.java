package com.trialsync.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trialsync.backend.config.TrialSyncProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final String TEST_SECRET = "secret-key-at-least-32-characters-long-123456";
    private JwtTokenService jwtTokenService;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        TrialSyncProperties props = new TrialSyncProperties();
        props.setAuthSecret(TEST_SECRET);
        props.setAccessTokenMinutes(480);
        fixedClock = Clock.fixed(Instant.ofEpochSecond(1700000000L), ZoneOffset.UTC);
        jwtTokenService = new JwtTokenService(props, fixedClock);
    }

    @Test
    void testTokenGenerationAndDecoding() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.createAccessToken(userId);

        assertNotNull(token);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);

        UUID decodedUserId = jwtTokenService.decodeAccessToken(token);
        assertEquals(userId, decodedUserId);
    }

    @Test
    void testDeterministicTokenGenerationAndVerification() {
        UUID userId = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        long now = 1700000000L;
        String token = jwtTokenService.createAccessToken(userId, TEST_SECRET, 60, now);

        UUID decoded = jwtTokenService.decodeAccessToken(token, TEST_SECRET, now);
        assertEquals(userId, decoded);

        // Before expiry: valid
        assertEquals(userId, jwtTokenService.decodeAccessToken(token, TEST_SECRET, now + 3599));

        // At exact expiry (now + 3600): Python compares with <, so exp < now is false (meaning exp == now is valid)
        // Let's verify: expiry = now + 3600. If nowEpochSeconds is now + 3600, expiry < nowEpochSeconds is 3600 < 3600 (false) -> valid
        assertEquals(userId, jwtTokenService.decodeAccessToken(token, TEST_SECRET, now + 3600));

        // After expiry: invalid
        assertNull(jwtTokenService.decodeAccessToken(token, TEST_SECRET, now + 3601));
    }

    @Test
    void testInvalidSignaturesOrMalformedTokensReturnNull() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenService.createAccessToken(userId);

        // Wrong secret
        assertNull(jwtTokenService.decodeAccessToken(token, "wrong-secret-key-at-least-32-chars-long", 1700000000L));

        // Malformed tokens
        assertNull(jwtTokenService.decodeAccessToken("invalid.token"));
        assertNull(jwtTokenService.decodeAccessToken("a.b.c.d"));
        assertNull(jwtTokenService.decodeAccessToken(""));
        assertNull(jwtTokenService.decodeAccessToken(null));
    }
}
