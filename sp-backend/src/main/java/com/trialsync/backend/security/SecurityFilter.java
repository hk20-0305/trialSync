package com.trialsync.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.dto.common.ErrorResponse;
import com.trialsync.backend.middleware.TraceIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the authenticated user for protected routes.
 *
 * <p>Only the endpoints the Python service left open are skipped. Everything else authenticates
 * eagerly so a bad token fails with the same envelope the FastAPI dependency produced.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SecurityFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PATHS =
            Set.of(
                    "/health/live",
                    "/health/ready",
                    "/api/v1/health/live",
                    "/api/v1/health/ready",
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/openapi.json",
                    "/docs",
                    "/error");

    private static final List<String> PUBLIC_PREFIXES =
            List.of("/docs/", "/swagger-ui", "/v3/api-docs", "/actuator/");

    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    public SecurityFilter(AuthenticationService authenticationService, ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isPublic(request)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            SecurityContext.set(
                    authenticationService.authenticate(request.getHeader("Authorization")));
            chain.doFilter(request, response);
        } catch (ApplicationError error) {
            // Raised inside a filter, this never reaches @RestControllerAdvice, so the envelope is
            // written here to keep the response identical.
            writeError(response, error);
        } finally {
            SecurityContext.clear();
        }
    }

    private boolean isPublic(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void writeError(HttpServletResponse response, ApplicationError error)
            throws IOException {
        response.setStatus(error.getStatusCode());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body =
                new ErrorResponse(
                        new ErrorResponse.ErrorDetail(
                                error.getCode(),
                                error.getMessage(),
                                TraceIdContext.current(),
                                error.getField(),
                                error.getDetails()));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
