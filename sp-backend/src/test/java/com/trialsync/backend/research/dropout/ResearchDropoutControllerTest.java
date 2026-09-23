package com.trialsync.backend.research.dropout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.research.dropout.controller.ResearchDropoutController;
import com.trialsync.backend.research.dropout.dto.DropoutPredictionRequest;
import com.trialsync.backend.research.dropout.dto.DropoutPredictionResponse;
import com.trialsync.backend.research.dropout.service.ResearchDropoutService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ResearchDropoutControllerTest {

    private ResearchDropoutService mockService;
    private ResearchDropoutController controller;

    @BeforeEach
    void setUp() {
        mockService = mock(ResearchDropoutService.class);
        controller = new ResearchDropoutController(mockService);
    }

    @Test
    void testPredictSuccessReturns200() {
        DropoutPredictionResponse mockResponse = new DropoutPredictionResponse();
        mockResponse.setDropoutProbability(0.65);
        mockResponse.setRiskTier("HIGH");
        mockResponse.setModelType("xgboost");

        when(mockService.predictDropout(any(DropoutPredictionRequest.class))).thenReturn(mockResponse);

        DropoutPredictionRequest req = new DropoutPredictionRequest("xgboost", Map.of("age", 50.0));
        ResponseEntity<DropoutPredictionResponse> responseEntity = controller.predict(req);

        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(0.65, responseEntity.getBody().getDropoutProbability());
        assertEquals("HIGH", responseEntity.getBody().getRiskTier());
    }

    @Test
    void testPredictPropagatesServiceError() {
        when(mockService.predictDropout(any(DropoutPredictionRequest.class)))
                .thenThrow(new ApplicationError("ML_SERVICE_UNAVAILABLE", "Dropout prediction ML service is currently unavailable", 503));

        DropoutPredictionRequest req = new DropoutPredictionRequest("xgboost", Map.of("age", 50.0));
        ApplicationError error = assertThrows(ApplicationError.class, () -> controller.predict(req));

        assertEquals(503, error.getStatusCode());
        assertEquals("ML_SERVICE_UNAVAILABLE", error.getCode());
    }
}
