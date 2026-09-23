package com.trialsync.backend.research.dropout;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Python ML microservice integration.
 */
@Component
@ConfigurationProperties(prefix = "trialsync.research.ml-service")
public class ResearchMlProperties {

    private String url = "http://localhost:8001";
    private double timeoutSeconds = 10.0;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public double getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(double timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
