package com.trialsync.backend.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Known-answer tests for the hand-rolled PBKDF2-HMAC-SHA256 in {@link Pbkdf2PasswordHasher}.
 *
 * <p>Replacing {@code SecretKeyFactory("PBKDF2WithHmacSHA256")} only pays off if the replacement
 * reproduces it exactly, so this class pins the derivation to values that come from outside this
 * codebase:
 *
 * <ul>
 *   <li><b>RFC 7914 Appendix B</b> - the published "Test Vectors for PBKDF2 with HMAC-SHA-256".
 *       Both vectors return 64 bytes, which makes them the only published values that exercise the
 *       second PBKDF2 block (the {@code INT_32_BE(2)} counter input). A block-counter bug that the
 *       32-byte production output could never show fails here.
 *   <li><b>RFC 6070's input set evaluated with HMAC-SHA-256</b> - the same (password, salt,
 *       iteration-count) tuples RFC 6070 publishes for HMAC-SHA-1, with the SHA-256 digests
 *       recomputed independently through OpenSSL's {@code PKCS5_PBKDF2_HMAC} via {@code node:crypto}
 *       ({@code target/ts-rfc-vectors.mjs}). These cover 16-, 32- and 40-byte outputs, i.e. partial
 *       and exact block lengths, plus embedded NUL bytes.
 *   <li><b>The stored wire format</b> - {@link Pbkdf2PasswordHasher#encode} is checked against a
 *       published digest, so the Base64 framing and the {@code pbkdf2_sha256$iters$salt$digest}
 *       layout cannot drift away from the KDF they wrap.
 * </ul>
 *
 * <p>Round-trip compatibility with the pre-optimisation SunJCE outputs is proven separately in
 * {@code Pbkdf2CompatibilityTest}.
 */
class Pbkdf2KnownAnswerTest {

    /** RFC 7914 Appendix B: P = "passwd", S = "salt", c = 1, dkLen = 64. */
    private static final String RFC7914_PASSWD =
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc"
                    + "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783";

    /** RFC 7914 Appendix B: P = "Password", S = "NaCl", c = 80000, dkLen = 64. */
    private static final String RFC7914_PASSWORD =
            "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56"
                    + "a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d";

    /** RFC 6070 inputs with HMAC-SHA-256: P = "password", S = "salt", c = 1, dkLen = 32. */
    private static final String SHA256_C1_32 =
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b";

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return bytes;
    }

    @Test
    void rfc7914AppendixBVectorsPass() {
        assertArrayEquals(
                hex(RFC7914_PASSWD), Pbkdf2PasswordHasher.derive("passwd", utf8("salt"), 1, 64));
        assertArrayEquals(
                hex(RFC7914_PASSWORD),
                Pbkdf2PasswordHasher.derive("Password", utf8("NaCl"), 80_000, 64));
    }

    @Test
    void rfc6070InputSetWithHmacSha256VectorsPass() {
        assertArrayEquals(
                hex(SHA256_C1_32), Pbkdf2PasswordHasher.derive("password", utf8("salt"), 1, 32));
        assertArrayEquals(
                hex("ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43"),
                Pbkdf2PasswordHasher.derive("password", utf8("salt"), 2, 32));
        assertArrayEquals(
                hex("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a"),
                Pbkdf2PasswordHasher.derive("password", utf8("salt"), 4096, 32));
        assertArrayEquals(
                hex(
                        "348c89dbcbd32b2f32d814b8116e84cf2b17347ebc1800181c4e2a1fb8dd53e1"
                                + "c635518c7dac47e9"),
                Pbkdf2PasswordHasher.derive(
                        "passwordPASSWORDpassword",
                        utf8("saltSALTsaltSALTsaltSALTsaltSALTsalt"),
                        4096,
                        40));
        assertArrayEquals(
                hex("89b69d0516f829893c696226650a8687"),
                Pbkdf2PasswordHasher.derive(
                        "pass\u0000word", "sa\u0000lt".getBytes(StandardCharsets.UTF_8), 4096, 16));
    }

    /**
     * The second block must be independent of the first: PBKDF2 defines every block from {@code
     * PRF(P, S || INT_32_BE(i))}, so a 64-byte derivation has to start with exactly the 32-byte
     * derivation. Truncation happens only after the loop, never inside it.
     */
    @Test
    void secondBlockIsIndependentOfTheFirst() {
        byte[] salt = utf8("shared-block-salt");
        byte[] twoBlocks = Pbkdf2PasswordHasher.derive("password", salt, 1000, 64);
        byte[] oneBlock = Pbkdf2PasswordHasher.derive("password", salt, 1000, 32);
        assertArrayEquals(oneBlock, Arrays.copyOf(twoBlocks, 32));
        assertEquals(64, twoBlocks.length);
    }

    /**
     * The stored format must carry a published digest verbatim: this is the exact string a legacy
     * deployment would have written for {@code encode("password", "salt", 1)}.
     */
    @Test
    void encodeEmbedsThePublishedDigestInTheStoredFormat() {
        String encoded = hasher.encode("password", utf8("salt"), 1);

        String expectedDigest =
                Base64.getUrlEncoder().encodeToString(hex(SHA256_C1_32));
        assertEquals("pbkdf2_sha256$1$c2FsdA==$" + expectedDigest, encoded);
        assertTrue(hasher.verify("password", encoded));
        assertFalse(hasher.verify("passw0rd", encoded));
    }

    @Test
    void deriveRejectsImpossibleParameters() {
        byte[] salt = utf8("salt");
        assertThrows(
                IllegalArgumentException.class,
                () -> Pbkdf2PasswordHasher.derive("password", salt, 0, 32));
        assertThrows(
                IllegalArgumentException.class,
                () -> Pbkdf2PasswordHasher.derive("password", salt, -1, 32));
        assertThrows(
                IllegalArgumentException.class,
                () -> Pbkdf2PasswordHasher.derive("password", salt, 1, 0));
    }
}

