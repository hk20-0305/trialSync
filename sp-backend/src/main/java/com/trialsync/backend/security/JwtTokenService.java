package com.trialsync.backend.security;

import com.trialsync.backend.config.TrialSyncProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Port of the hand-rolled HS256 tokens in {@code trialsync.security}.
 *
 * <p>A general-purpose JWT library is deliberately not used: the Python implementation serialises
 * its header and payload with compact separators and strips Base64 padding, so any library that
 * orders keys differently or re-pads would produce a different signature and invalidate tokens
 * already issued to browsers.
 *
 * <p>Two behaviours are easy to get wrong and are asserted by the compatibility tests: verification
 * returns {@code null} on every failure rather than throwing, and a token whose {@code exp} equals
 * the current second is still valid because Python compares with {@code <}.
 */
@Service
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final TrialSyncProperties properties;
    private final Clock clock;

    public JwtTokenService(TrialSyncProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Issues a token for the configured lifetime. */
    public String createAccessToken(UUID userId) {
        return createAccessToken(
                userId,
                properties.requireAuthSecret(),
                properties.getAccessTokenMinutes(),
                clock.instant().getEpochSecond());
    }

    /** Deterministic variant: the issued-at second is supplied rather than read from the clock. */
    public String createAccessToken(UUID userId, String secret, int lifetimeMinutes, long nowEpochSeconds) {
        String header = encode(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        long expiry = nowEpochSeconds + (long) lifetimeMinutes * 60L;
        // Python emits json.dumps({"sub": ..., "exp": ...}, separators=(",", ":")), which preserves
        // insertion order, so "sub" precedes "exp".
        String payloadJson = "{\"sub\":\"" + userId + "\",\"exp\":" + expiry + "}";
        String payload = encode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature = sign(header, payload, secret);
        return header + "." + payload + "." + signature;
    }

    /** Resolves the subject of a valid token, or {@code null} when the token is not usable. */
    public UUID decodeAccessToken(String token) {
        return decodeAccessToken(token, properties.getAuthSecret(), clock.instant().getEpochSecond());
    }

    /** Deterministic variant used by the compatibility tests. */
    public UUID decodeAccessToken(String token, String secret, long nowEpochSeconds) {
        if (token == null || secret == null) {
            return null;
        }
        try {
            // Python's str.split(".") with no limit produces more than three parts for a malformed
            // token, and the tuple unpacking then raises ValueError -> None.
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return null;
            }
            String expected = sign(parts[0], parts[1], secret);
            if (!constantTimeEquals(parts[2], expected)) {
                return null;
            }
            String payloadJson = new String(decode(parts[1]), StandardCharsets.UTF_8);
            Long expiry = readLongMember(payloadJson, "exp");
            String subject = readStringMember(payloadJson, "sub");
            if (expiry == null || subject == null) {
                return null;
            }
            if (expiry < nowEpochSeconds) {
                return null;
            }
            return UUID.fromString(subject);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String sign(String header, String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(
                    new SecretKeySpec(
                            secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest =
                    mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8));
            return encode(digest);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }

    private static String encode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] decode(String value) {
        int remainder = value.length() % 4;
        String padded = remainder == 0 ? value : value + "===".substring(0, 4 - remainder);
        return Base64.getUrlDecoder().decode(padded);
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Minimal reader for the two flat members this service writes. Avoiding a full JSON parser here
     * keeps the security path free of configuration that could change how numbers are coerced.
     */
    private static String readStringMember(String json, String name) {
        String needle = "\"" + name + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        int valueStart = start + needle.length();
        int end = json.indexOf('"', valueStart);
        return end < 0 ? null : json.substring(valueStart, end);
    }

    private static Long readLongMember(String json, String name) {
        String needle = "\"" + name + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        int valueStart = start + needle.length();
        int end = valueStart;
        while (end < json.length()) {
            char current = json.charAt(end);
            if (current == ',' || current == '}') {
                break;
            }
            end++;
        }
        String raw = json.substring(valueStart, end).trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1);
        }
        try {
            // Python applies int(data["exp"]); a float such as 12.9 would raise and yield None.
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
