package com.trialsync.backend.security;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.entity.User;

/**
 * Holds the authenticated user for the duration of a request.
 *
 * <p>Spring Security is not on the classpath: the Python service authenticated through a single
 * FastAPI dependency, and reproducing that with one filter plus this holder keeps the authorization
 * rules in the services where the original code had them.
 */
public final class SecurityContext {

    private static final ThreadLocal<User> CURRENT = new ThreadLocal<>();

    private SecurityContext() {}

    static void set(User user) {
        CURRENT.set(user);
    }

    public static void setForTesting(User user) {
        CURRENT.set(user);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Returns the signed-in user, or {@code null} on an anonymous request. */
    public static User currentOrNull() {
        return CURRENT.get();
    }

    /**
     * Returns the signed-in user or fails with the Python {@code AUTHENTICATION_REQUIRED} envelope.
     */
    public static User require() {
        User user = CURRENT.get();
        if (user == null) {
            throw new ApplicationError("AUTHENTICATION_REQUIRED", "Sign in is required.", 401);
        }
        return user;
    }
}
