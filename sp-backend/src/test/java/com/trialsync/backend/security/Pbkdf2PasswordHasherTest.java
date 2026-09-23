package com.trialsync.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Pbkdf2PasswordHasherTest {

    private Pbkdf2PasswordHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new Pbkdf2PasswordHasher();
    }

    @Test
    void testHashFormatAndVerification() {
        String password = "CorrectHorse123";
        String hash = hasher.hash(password);

        assertNotNull(hash);
        assertTrue(hash.startsWith("pbkdf2_sha256$600000$"));

        String[] parts = hash.split("\\$");
        assertEquals(4, parts.length);
        assertEquals("pbkdf2_sha256", parts[0]);
        assertEquals("600000", parts[1]);

        assertTrue(hasher.verify(password, hash));
        assertFalse(hasher.verify("WrongPassword", hash));
        assertFalse(hasher.verify(password, "invalid_hash_string"));
        assertFalse(hasher.verify(null, hash));
        assertFalse(hasher.verify(password, null));
    }

    @Test
    void testDeterministicEncodeMatches() {
        byte[] salt = new byte[16];
        for (int i = 0; i < salt.length; i++) {
            salt[i] = (byte) i;
        }

        String encoded1 = hasher.encode("test-password", salt, 1000);
        String encoded2 = hasher.encode("test-password", salt, 1000);

        assertEquals(encoded1, encoded2);
        assertTrue(hasher.verify("test-password", encoded1));
        assertFalse(hasher.verify("other-password", encoded1));
    }

    @Test
    void testVerifyToleratesUnpaddedBase64() {
        byte[] salt = new byte[16];
        String encoded = hasher.encode("secret", salt, 1000);
        // Strip any trailing '=' to simulate unpadded stored hash
        String unpadded = encoded.replace("=", "");
        assertTrue(hasher.verify("secret", unpadded));
    }
}
