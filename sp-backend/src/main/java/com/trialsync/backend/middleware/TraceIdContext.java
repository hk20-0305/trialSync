package com.trialsync.backend.middleware;

import java.util.UUID;

/**
 * Request-scoped trace identifier.
 *
 * <p>The Python service stored this on the ASGI request state. A thread local is the closest
 * equivalent for Spring MVC's thread-per-request model, and the filter always clears it so a pooled
 * worker never leaks an identifier into the next request.
 */
public final class TraceIdContext {

    public static final String HEADER = "X-Trace-ID";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceIdContext() {}

    static void set(String traceId) {
        CURRENT.set(traceId);
    }

    static void clear() {
        CURRENT.remove();
    }

    /**
     * Returns the active trace identifier, or a fresh one when called outside a request. This
     * mirrors {@code get_trace_id}, which fell back to {@code str(uuid4())}.
     */
    public static String current() {
        String value = CURRENT.get();
        return value != null ? value : UUID.randomUUID().toString();
    }
}
