package com.trialsync.backend.research.dropout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.research.dropout.client.ResearchDropoutClient;
import com.trialsync.backend.research.dropout.dto.DropoutPredictionRequest;
import com.trialsync.backend.research.dropout.dto.DropoutPredictionResponse;
import com.trialsync.backend.research.dropout.service.ResearchDropoutService;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResearchDropoutServiceTest {

    private ResearchDropoutClient mockClient;
    private ResearchDropoutService service;

    @BeforeEach
    void setUp() {
        mockClient = mock(ResearchDropoutClient.class);
        service = new ResearchDropoutService(mockClient);
    }

    @Test
    void testPredictSuccessfulDispatch() {
        DropoutPredictionResponse dummyResponse = new DropoutPredictionResponse();
        dummyResponse.setDropoutProbability(0.75);
        dummyResponse.setRiskTier("HIGH");
        dummyResponse.setModelType("xgboost");

        when(mockClient.predict(any(DropoutPredictionRequest.class))).thenReturn(dummyResponse);

        DropoutPredictionRequest request = new DropoutPredictionRequest("xgboost", Map.of("age", 60.0));
        DropoutPredictionResponse result = service.predictDropout(request);

        assertNotNull(result);
        assertEquals(0.75, result.getDropoutProbability());
        assertEquals("HIGH", result.getRiskTier());
        verify(mockClient).predict(request);
    }

    @Test
    void testRejectsUnsupportedModel() {
        DropoutPredictionRequest request = new DropoutPredictionRequest("random_forest", Map.of("age", 60.0));

        ApplicationError error = assertThrows(ApplicationError.class, () -> service.predictDropout(request));
        assertEquals(422, error.getStatusCode());
        assertEquals("UNSUPPORTED_MODEL", error.getCode());
    }

    @Test
    void testRejectsEmptyFeatures() {
        DropoutPredictionRequest request = new DropoutPredictionRequest("xgboost", Collections.emptyMap());

        ApplicationError error = assertThrows(ApplicationError.class, () -> service.predictDropout(request));
        assertEquals(422, error.getStatusCode());
        assertEquals("MISSING_FEATURES", error.getCode());
    }
}
