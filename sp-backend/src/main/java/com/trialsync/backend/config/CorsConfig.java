package com.trialsync.backend.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Reproduces the Starlette CORSMiddleware setup: explicit origins only, credentials allowed, and
 * the trace header exposed so the browser client can surface it.
 */
@Configuration
public class CorsConfig {

    private final TrialSyncProperties properties;

    public CorsConfig(TrialSyncProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = properties.getCorsOrigins();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Trace-ID"));
        configuration.setAllowCredentials(!origins.isEmpty());
        configuration.setMaxAge(600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!origins.isEmpty()) {
            source.registerCorsConfiguration("/**", configuration);
        }
        return new CorsFilter(source);
    }
}
