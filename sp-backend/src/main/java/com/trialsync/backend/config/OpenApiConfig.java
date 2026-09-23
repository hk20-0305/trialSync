package com.trialsync.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publishes the same bearer-token API description the Python service exposed. */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI trialSyncOpenApi(TrialSyncProperties properties) {
        return new OpenAPI()
                .info(
                        new Info()
                                .title(properties.getAppName())
                                .version("0.1.0")
                                .description(
                                        "Deterministic synthetic clinical-trial screening API. "
                                                + "Eligibility is decided only by the rule engine."))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SCHEME,
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }
}
