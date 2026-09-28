package com.trialsync.backend.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Proves the optimised {@link Pbkdf2PasswordHasher} still accepts every credential hash the
 * pre-optimisation implementation produced, so no stored password is invalidated by the speed-up.
 *
 * <p>Three independent layers of evidence:
 *
 * <ol>
 *   <li><b>Frozen SunJCE goldens</b> - seven complete {@code pbkdf2_sha256$...} strings generated
 *       by the original {@code SecretKeyFactory("PBKDF2WithHmacSHA256")} path before it was
 *       replaced. They cover the production 600,000 iterations, a non-ASCII password, embedded NUL
 *       bytes, a NUL-byte salt and a short password at a different iteration count.
 *   <li><b>The live demo credential</b> - the exact row {@code DemoSeedService} writes for
 *       {@code demo@trialsync.example}, as read back from the database.
 *   <li><b>A live cross-check</b> - the JDK's own SunJCE provider is still invoked on the same
 *       inputs and compared byte for byte, so the equivalence claim survives a JDK or provider
 *       upgrade rather than resting on values captured once.
 * </ol>
 *
 * <p>Cross-checked digests in {@code Pbkdf2KnownAnswerTest} additionally pin the algorithm itself
 * to published RFC vectors.
 */
class Pbkdf2CompatibilityTest {

    /** Row stored by {@code DemoSeedService} for {@code demo@trialsync.example}. */
    private static final String DEMO_STORED_HASH =
            "pbkdf2_sha256$600000$MA4Zg8V-2sWV3EAOTInIEA==$9zFceNw8GL6UJzSrxplOHouV_B9UslwPXb3tOieigmk=";

    private static final byte[] COUNTING_SALT = countingSalt();

    private static final byte[] NUL_SALT = "sa\u0000lt".getBytes(StandardCharsets.UTF_8);

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    private static byte[] countingSalt() {
        byte[] salt = new byte[16];
        for (int index = 0; index < salt.length; index++) {
            salt[index] = (byte) index;
        }
        return salt;
    }

    private static byte[] legacyDerive(String password, byte[] salt, int iterations, int bits)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, bits);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();
    }

    private void assertGolden(
            String label, String password, byte[] salt, int iterations, String golden) {
        assertEquals(golden, hasher.encode(password, salt, iterations), label + " digest drifted");
        assertTrue(hasher.verify(password, golden), label + " no longer verifies");
        assertFalse(hasher.verify(password + "!", golden), label + " accepted a wrong password");
    }

    @Test
    void demoAccountHashStillVerifies() {
        assertTrue(hasher.verify("SyntheticDemo123!", DEMO_STORED_HASH));
        assertFalse(hasher.verify("SyntheticDemo123", DEMO_STORED_HASH));
        assertFalse(hasher.verify("", DEMO_STORED_HASH));
    }

    @Test
    void sunJceGoldenHashesAreReproducedByteForByte() {
        assertGolden(
                "demo-600k",
                "SyntheticDemo123!",
                COUNTING_SALT,
                600_000,
                "pbkdf2_sha256$600000$AAECAwQFBgcICQoLDA0ODw==$OHNVxYXspPImNwRt9DJdxgvugYa-Z_IyE3DugYZbizk=");
        assertGolden(
                "horse-600k",
                "CorrectHorse123",
                COUNTING_SALT,
                600_000,
                "pbkdf2_sha256$600000$AAECAwQFBgcICQoLDA0ODw==$OT9Ui8APvbC9rcSlOnh71GQ9HpyN6MgLZT9n4ZwFyC4=");
        assertGolden(
                "unicode-1k",
                "p\u00e4ssw\u00f6rd-\u00dcn\u00efcode-\u2713",
                COUNTING_SALT,
                1_000,
                "pbkdf2_sha256$1000$AAECAwQFBgcICQoLDA0ODw==$QVcLcuuZrk0vVnIFyW1BlR8CkHjwzBjDvqKPvB7_BXw=");
        assertGolden(
                "nul-1k",
                "pass\u0000word",
                COUNTING_SALT,
                1_000,
                "pbkdf2_sha256$1000$AAECAwQFBgcICQoLDA0ODw==$r4AjW37SZfq41iXabBV-Ph9cl7PRvI_ieBsv-RqAjKQ=");
        assertGolden(
                "plain-1k",
                "test-password",
                COUNTING_SALT,
                1_000,
                "pbkdf2_sha256$1000$AAECAwQFBgcICQoLDA0ODw==$CsicvUWbHGs2ttWh5luWYSLPzQdifVez0xEe9cJ9thE=");
        assertGolden(
                "short-10k",
                "abc",
                COUNTING_SALT,
                10_000,
                "pbkdf2_sha256$10000$AAECAwQFBgcICQoLDA0ODw==$6IxjLIRTY3MAJe70ar0JTyOpcAWcJkvOIilznuQkVoM=");
        assertGolden(
                "nul-salt-1k",
                "password",
                NUL_SALT,
                1_000,
                "pbkdf2_sha256$1000$c2EAbHQ=$fvifF3nTHjFaL1EiGB9DU79n8H8dG5gKwOurC5SRyFw=");
    }

    /**
     * Runs the JDK's own SunJCE PBKDF2 side by side with the replacement for the same inputs.
     * SunJCE is what produced every hash already in the database, so byte-for-byte equality here is
     * the compatibility guarantee that matters: multi-block keys, non-ASCII and NUL passwords, and
     * NUL salts included.
     */
    @Test
    void matchesTheJdkSecretKeyFactoryForIdenticalInputs() throws Exception {
        String[] passwords = {
            "password",
            "CorrectHorse123",
            "p\u00e4ssw\u00f6rd-\u00dcn\u00efcode-\u2713",
            "pass\u0000word"
        };
        int[] iterationCounts = {1, 2, 1_000, 10_000};
        int[] derivedKeyBytes = {16, 32, 64};
        byte[][] salts = {COUNTING_SALT, NUL_SALT};

        for (String password : passwords) {
            for (int iterations : iterationCounts) {
                for (int keyBytes : derivedKeyBytes) {
                    for (byte[] salt : salts) {
                        String context =
                                "password="
                                        + password
                                        + ", iterations="
                                        + iterations
                                        + ", dkLen="
                                        + keyBytes;
                        assertArrayEquals(
                                legacyDerive(password, salt, iterations, keyBytes * 8),
                                Pbkdf2PasswordHasher.derive(password, salt, iterations, keyBytes),
                                context);
                    }
                }
            }
        }
    }

    /**
     * Regression tripwire for the reason this code was rewritten: SunJCE needed roughly 1.8-2.0 s
     * for one production derivation, which is what made sign-in feel broken. The bound is about
     * ten times the observed cost of the optimised path so it cannot flake on a busy machine, yet
     * it still fails if per-iteration key setup ever creeps back in.
     */
    @Test
    void productionDerivationStaysWellInsideTheLegacyBudget() {
        hasher.encode("warmup", COUNTING_SALT, 100_000);

        long started = System.nanoTime();
        Pbkdf2PasswordHasher.derive(
                "CorrectHorse123", COUNTING_SALT, Pbkdf2PasswordHasher.DEFAULT_ITERATIONS, 32);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertTrue(
                elapsedMs < 1500,
                () ->
                        "600,000-iteration derivation took "
                                + elapsedMs
                                + " ms; the legacy SunJCE path cost ~1800-2000 ms, so the"
                                + " optimised Mac-reuse path is probably no longer in use");
    }

    /** Cross-checking against Base64 requires no padding surprises in either direction. */
    @Test
    void saltEncodingRoundTripsThroughTheStoredFormat() {
        String encoded = hasher.encode("password", COUNTING_SALT, 1_000);
        String[] parts = encoded.split("\\$", -1);
        assertEquals(4, parts.length);
        assertArrayEquals(COUNTING_SALT, Base64.getUrlDecoder().decode(parts[2]));
        assertTrue(hasher.verify("password", encoded));
    }
}
