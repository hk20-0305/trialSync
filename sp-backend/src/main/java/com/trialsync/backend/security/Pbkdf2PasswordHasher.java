package com.trialsync.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Byte-for-byte port of {@code trialsync.security.hash_password} / {@code verify_password}.
 *
 * <p>Stored format is {@code pbkdf2_sha256$600000$<salt>$<digest>} where both segments are
 * <em>padded</em> URL-safe Base64. The migration specification describes these as unpadded, but the
 * Python implementation uses {@code base64.urlsafe_b64encode} without stripping padding, so existing
 * password hashes carry the {@code =} characters. Behavioural compatibility with the running system
 * wins over the wording in the document; changing this would invalidate every stored credential.
 *
 * <p>The derivation itself is PBKDF2-HMAC-SHA256 computed directly over a single {@link Mac}
 * instance rather than delegated to {@code SecretKeyFactory("PBKDF2WithHmacSHA256")}. The JDK's
 * provider re-derives the HMAC key for every one of the iteration rounds, which cost roughly two
 * seconds per sign-in at {@value #DEFAULT_ITERATIONS} iterations; {@code Mac.doFinal} already resets
 * the MAC to its initialised state, so one {@code init} can serve every round. The algorithm,
 * iteration count, salt handling, password encoding (UTF-8, matching Python's {@code
 * password.encode()}) and therefore every derived digest are unchanged - see
 * {@code Pbkdf2KnownAnswerTest} for the byte-for-byte compatibility vectors.
 */
@Component
public class Pbkdf2PasswordHasher {

    public static final String ALGORITHM_LABEL = "pbkdf2_sha256";
    public static final int DEFAULT_ITERATIONS = 600_000;

    private static final int SALT_BYTES = 16;
    private static final int DERIVED_BITS = 256;
    private static final String PRF_ALGORITHM = "HmacSHA256";

    private final SecureRandom random = new SecureRandom();

    /** Derives a fresh hash using a random 16-byte salt. */
    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return encode(password, salt, DEFAULT_ITERATIONS);
    }

    /** Deterministic variant used by the compatibility tests. */
    public String encode(String password, byte[] salt, int iterations) {
        byte[] derived = derive(password, salt, iterations, DERIVED_BITS / 8);
        Base64.Encoder encoder = Base64.getUrlEncoder();
        return ALGORITHM_LABEL
                + "$"
                + iterations
                + "$"
                + encoder.encodeToString(salt)
                + "$"
                + encoder.encodeToString(derived);
    }

    /**
     * Verifies a candidate password. Any malformed stored value returns {@code false} rather than
     * throwing, matching the Python {@code except (ValueError, TypeError)} guard.
     */
    public boolean verify(String password, String encoded) {
        if (password == null || encoded == null) {
            return false;
        }
        try {
            // Python uses split("$", 3), which yields exactly four segments and leaves any
            // additional "$" inside the final digest segment.
            String[] parts = splitLimit(encoded, '$', 4);
            if (parts.length != 4) {
                return false;
            }
            if (!ALGORITHM_LABEL.equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = decodeUrlBase64(parts[2]);
            byte[] expected = decodeUrlBase64(parts[3]);
            byte[] actual = derive(password, salt, iterations, DERIVED_BITS / 8);
            return MessageDigest.isEqual(actual, expected);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * PBKDF2-HMAC-SHA256 for an explicit derived-key length.
     *
     * <p>Package-private so the known-answer tests can also exercise multi-block derivations; the
     * RFC 7914 vectors publish 64-byte keys, which needs two PRF blocks.
     *
     * <p>A single {@link Mac} is initialised once and reused for every PRF call. {@code doFinal}
     * resets the MAC to the state it was in after {@code init}, keeping the secret key, so the
     * inner and outer HMAC pads are never rebuilt. This is the same construction OpenSSL's
     * {@code PKCS5_PBKDF2_HMAC} uses, and it produces identical output - only the per-iteration
     * JDK key setup is removed.
     */
    static byte[] derive(String password, byte[] salt, int iterations, int derivedKeyBytes) {
        if (iterations <= 0) {
            throw new IllegalArgumentException("iteration count must be positive");
        }
        if (derivedKeyBytes <= 0) {
            throw new IllegalArgumentException("derived key length must be positive");
        }
        try {
            // The JDK encodes the password bytes as UTF-8 for PBKDF2WithHmacSHA*, which matches
            // Python's password.encode() default of UTF-8. Deriving the bytes directly (rather than
            // through PBEKeySpec) keeps that encoding explicit.
            Mac mac = Mac.getInstance(PRF_ALGORITHM);
            mac.init(new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), PRF_ALGORITHM));

            int hashLength = mac.getMacLength();
            int blockCount = (derivedKeyBytes + hashLength - 1) / hashLength;
            byte[] derived = new byte[blockCount * hashLength];

            // S || INT_32_BE(i): the salt is copied once, only the four-byte block index changes.
            byte[] saltedBlock = new byte[salt.length + 4];
            System.arraycopy(salt, 0, saltedBlock, 0, salt.length);

            byte[] u;
            byte[] accumulator = new byte[hashLength];
            for (int block = 1; block <= blockCount; block++) {
                saltedBlock[salt.length] = (byte) (block >>> 24);
                saltedBlock[salt.length + 1] = (byte) (block >>> 16);
                saltedBlock[salt.length + 2] = (byte) (block >>> 8);
                saltedBlock[salt.length + 3] = (byte) block;

                u = mac.doFinal(saltedBlock); // U_1 = PRF(P, S || INT(i))
                System.arraycopy(u, 0, accumulator, 0, hashLength);
                for (int iteration = 1; iteration < iterations; iteration++) {
                    u = mac.doFinal(u); // U_j = PRF(P, U_j-1)
                    for (int index = 0; index < hashLength; index++) {
                        accumulator[index] ^= u[index];
                    }
                }
                System.arraycopy(accumulator, 0, derived, (block - 1) * hashLength, hashLength);
            }
            return derivedKeyBytes == derived.length
                    ? derived
                    : Arrays.copyOf(derived, derivedKeyBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("PBKDF2-HMAC-SHA256 is unavailable", ex);
        }
    }

    /**
     * Decodes padded or unpadded URL-safe Base64. Python's {@code urlsafe_b64decode} requires
     * padding, and every hash this service writes carries it, but tolerating both keeps hashes
     * generated by other tooling verifiable.
     */
    private static byte[] decodeUrlBase64(String value) {
        String padded = value;
        int remainder = padded.length() % 4;
        if (remainder != 0) {
            padded = padded + "===".substring(0, 4 - remainder);
        }
        return Base64.getUrlDecoder().decode(padded.getBytes(StandardCharsets.US_ASCII));
    }

    /** Equivalent of Python's {@code str.split(sep, maxsplit)} with a fixed segment count. */
    private static String[] splitLimit(String value, char separator, int limit) {
        String[] result = new String[limit];
        int index = 0;
        int start = 0;
        while (index < limit - 1) {
            int next = value.indexOf(separator, start);
            if (next < 0) {
                return new String[0];
            }
            result[index++] = value.substring(start, next);
            start = next + 1;
        }
        result[index] = value.substring(start);
        return result;
    }
}
