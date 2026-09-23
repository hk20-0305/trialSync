package com.trialsync.backend.research.dropout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Incoming request DTO for research dropout prediction.
 */
public class DropoutPredictionRequest {

    @NotBlank(message = "model_type is required ('xgboost' or 'logistic_regression')")
    @JsonProperty("model_type")
    private String modelType = "xgboost";

    @NotNull(message = "features map is required")
    @NotEmpty(message = "features map cannot be empty")
    @JsonProperty("features")
    private Map<String, Object> features;

    public DropoutPredictionRequest() {}

    public DropoutPredictionRequest(String modelType, Map<String, Object> features) {
        this.modelType = modelType;
        this.features = features;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public Map<String, Object> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, Object> features) {
        this.features = features;
    }
}
