package com.trialsync.backend.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Application-wide singletons and the startup validation of the settings contract. */
@Configuration
public class AppConfig implements InitializingBean {

    private final TrialSyncProperties properties;

    public AppConfig(TrialSyncProperties properties) {
        this.properties = properties;
    }

    /** Fails fast on a misconfigured deployment, exactly as the Python settings model did. */
    @Override
    public void afterPropertiesSet() {
        properties.validate();
    }

    /**
     * The single clock every service reads. The deterministic screening engine never receives it;
     * callers resolve a screening date first and pass that value in.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Shared HTTP client for the Groq and terminology integrations. */
    @Bean
    public HttpClient externalHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
