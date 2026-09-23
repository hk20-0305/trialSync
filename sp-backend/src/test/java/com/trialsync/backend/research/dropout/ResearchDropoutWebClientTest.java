package com.trialsync.backend.research.dropout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.research.dropout.client.ResearchDropoutWebClient;
import com.trialsync.backend.research.dropout.dto.DropoutPredictionRequest;
import com.trialsync.backend.research.dropout.dto.DropoutPredictionResponse;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

class ResearchDropoutWebClientTest {

    @Test
    void testPredictSuccess() {
        String jsonResponse = """
                {
                    "dropout_probability": 0.852,
                    "predicted_dropout": true,
                    "risk_tier": "HIGH",
                    "model_type": "xgboost",
                    "model_version": "0.1.0-research",
                    "predicted_at": "2026-09-17T10:00:00Z",
                    "threshold": 0.5,
                    "shap_explanation": {
                        "base_value": 0.0,
                        "predicted_probability": 0.852,
                        "top_contributions": [
                            {
                                "feature": "alt_latest_pre_cutoff",
                                "value": 45.3,
                                "shap_value": 0.949,
                                "abs_magnitude": 0.949,
                                "direction": "INCREASES_RISK"
                            }
                        ]
                    }
                }
                """;

        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(jsonResponse)
                        .build()
        );

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .baseUrl("http://localhost:8001")
                .build();

        ResearchDropoutWebClient client = new ResearchDropoutWebClient(webClient);
        DropoutPredictionRequest req = new DropoutPredictionRequest("xgboost", Map.of("age", 55.0));

        DropoutPredictionResponse response = client.predict(req);

        assertNotNull(response);
        assertEquals(0.852, response.getDropoutProbability(), 0.001);
        assertEquals("HIGH", response.getRiskTier());
        assertTrue(response.isPredictedDropout());
        assertNotNull(response.getShapExplanation());
        assertEquals(1, response.getShapExplanation().getTopContributions().size());
    }

    @Test
    void testPredictConnectionRefusedThrows503() {
        ExchangeFunction exchangeFunction = request -> Mono.error(
                new WebClientRequestException(
                        new RuntimeException("Connection refused"),
                        HttpMethod.POST,
                        URI.create("http://localhost:8001/predict"),
                        HttpHeaders.EMPTY
                )
        );

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .baseUrl("http://localhost:8001")
                .build();

        ResearchDropoutWebClient client = new ResearchDropoutWebClient(webClient);
        DropoutPredictionRequest req = new DropoutPredictionRequest("xgboost", Map.of("age", 55.0));

        ApplicationError error = assertThrows(ApplicationError.class, () -> client.predict(req));
        assertEquals(503, error.getStatusCode());
        assertEquals("ML_SERVICE_UNAVAILABLE", error.getCode());
    }

    @Test
    void testPredict422ValidationFailure() {
        String errorJson = """
                {"code": "VALIDATION_ERROR", "message": "Missing feature: alt_latest_pre_cutoff"}
                """;

        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(errorJson)
                        .build()
        );

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .baseUrl("http://localhost:8001")
                .build();

        ResearchDropoutWebClient client = new ResearchDropoutWebClient(webClient);
        DropoutPredictionRequest req = new DropoutPredictionRequest("xgboost", Map.of("age", 55.0));

        ApplicationError error = assertThrows(ApplicationError.class, () -> client.predict(req));
        assertEquals(422, error.getStatusCode());
        assertEquals("INVALID_ML_PAYLOAD", error.getCode());
    }
}
