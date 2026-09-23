package com.trialsync.backend.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON conventions that match the FastAPI/Pydantic responses.
 *
 * <p>Null fields stay in the payload because the Python response models serialised them, with the
 * single exception of the error envelope, which used {@code exclude_none} and therefore carries an
 * explicit {@link JsonInclude} annotation of its own.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer trialSyncJacksonCustomizer() {
        return builder -> {
            builder.modules(new JavaTimeModule());
            builder.serializationInclusion(JsonInclude.Include.ALWAYS);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}
