package com.trialsync.backend.research.dropout.service;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.research.dropout.client.ResearchDropoutClient;
import com.trialsync.backend.research.dropout.dto.DropoutPredictionRequest;
import com.trialsync.backend.research.dropout.dto.DropoutPredictionResponse;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service orchestrating research dropout predictions and delegating to the Python ML pipeline.
 */
@Service
public class ResearchDropoutService {

    private static final Logger log = LoggerFactory.getLogger(ResearchDropoutService.class);
    private static final Set<String> SUPPORTED_MODELS = Set.of("xgboost", "logistic_regression");

    private final ResearchDropoutClient dropoutClient;

    public ResearchDropoutService(ResearchDropoutClient dropoutClient) {
        this.dropoutClient = dropoutClient;
    }

    /**
     * Executes validated dropout risk inference.
     *
     * @param request the prediction request
     * @return the resulting dropout probability and SHAP attributions
     */
    public DropoutPredictionResponse predictDropout(DropoutPredictionRequest request) {
        if (request.getModelType() == null || !SUPPORTED_MODELS.contains(request.getModelType().trim().toLowerCase())) {
            throw new ApplicationError("UNSUPPORTED_MODEL", "Unsupported model_type: " + request.getModelType() + ". Expected 'xgboost' or 'logistic_regression'", 422);
        }

        if (request.getFeatures() == null || request.getFeatures().isEmpty()) {
            throw new ApplicationError("MISSING_FEATURES", "Features payload cannot be null or empty", 422);
        }

        log.info("Requesting dropout risk estimation via model: {}", request.getModelType());
        DropoutPredictionResponse response = dropoutClient.predict(request);
        log.info("Dropout prediction completed: proba={}, tier={}, model={}",
                response.getDropoutProbability(), response.getRiskTier(), response.getModelType());

        return response;
    }
}
