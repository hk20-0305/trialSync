package com.trialsync.backend.research.dropout.client;

import com.trialsync.backend.research.dropout.dto.DropoutPredictionRequest;
import com.trialsync.backend.research.dropout.dto.DropoutPredictionResponse;

/**
 * Service-to-service interface for invoking the Python Dropout ML pipeline.
 */
public interface ResearchDropoutClient {

    /**
     * Sends feature vector to Python FastAPI microservice and returns dropout risk & SHAP attributions.
     *
     * @param request the prediction request with model type and features
     * @return the prediction response with probability, risk tier, and SHAP explanation
     */
    DropoutPredictionResponse predict(DropoutPredictionRequest request);
}
