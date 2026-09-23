package com.trialsync.backend.middleware;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Assigns one trace identifier per request and echoes it on every response. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        TraceIdContext.set(traceId);
        // The header is set before the chain runs so it survives even when a handler commits the
        // response itself, matching the ASGI wrapper that stamped http.response.start.
        response.setHeader(TraceIdContext.HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            TraceIdContext.clear();
        }
    }
}
