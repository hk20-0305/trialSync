package com.trialsync.backend.research.dropout.client;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.research.dropout.dto.DropoutPredictionRequest;
import com.trialsync.backend.research.dropout.dto.DropoutPredictionResponse;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Spring WebClient-based implementation of {@link ResearchDropoutClient}.
 */
@Component
public class ResearchDropoutWebClient implements ResearchDropoutClient {

    private static final Logger log = LoggerFactory.getLogger(ResearchDropoutWebClient.class);

    private final WebClient webClient;

    public ResearchDropoutWebClient(WebClient researchMlWebClient) {
        this.webClient = researchMlWebClient;
    }

    @Override
    public DropoutPredictionResponse predict(DropoutPredictionRequest request) {
        log.info("Dispatching dropout prediction to Python ML service for model: {}", request.getModelType());

        try {
            DropoutPredictionResponse response = webClient.post()
                    .uri("/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(DropoutPredictionResponse.class)
                    .block(Duration.ofSeconds(15));

            if (response == null) {
                throw new ApplicationError("MALFORMED_ML_RESPONSE", "ML service returned empty body", 502);
            }

            return response;

        } catch (WebClientResponseException ex) {
            log.warn("ML service responded with HTTP status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().is4xxClientError()) {
                throw new ApplicationError("INVALID_ML_PAYLOAD", "ML service rejected feature schema: " + ex.getMessage(), 422);
            }
            throw new ApplicationError("ML_SERVICE_ERROR", "ML service encountered an internal failure: " + ex.getStatusCode(), 502);

        } catch (WebClientRequestException ex) {
            log.error("Failed to establish connection to Python ML microservice: {}", ex.getMessage());
            throw new ApplicationError("ML_SERVICE_UNAVAILABLE", "Dropout prediction ML service is currently unavailable", 503);

        } catch (Exception ex) {
            if (ex.getCause() instanceof TimeoutException || ex instanceof IllegalStateException) {
                log.error("Timeout communicating with Python ML service: {}", ex.getMessage());
                throw new ApplicationError("ML_SERVICE_TIMEOUT", "Timeout awaiting response from ML service", 504);
            }
            log.error("Unexpected error invoking ML service: {}", ex.getMessage());
            throw new ApplicationError("ML_INTEGRATION_ERROR", "Failed to communicate with dropout ML service", 502);
        }
    }
}
