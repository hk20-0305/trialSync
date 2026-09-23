package com.trialsync.backend.research.dropout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO returned by both the Python ML service and the Spring Boot research controller.
 */
public class DropoutPredictionResponse {

    @JsonProperty("dropout_probability")
    private double dropoutProbability;

    @JsonProperty("predicted_dropout")
    private boolean predictedDropout;

    @JsonProperty("risk_tier")
    private String riskTier;

    @JsonProperty("model_type")
    private String modelType;

    @JsonProperty("model_version")
    private String modelVersion;

    @JsonProperty("predicted_at")
    private String predictedAt;

    @JsonProperty("threshold")
    private double threshold = 0.5;

    @JsonProperty("shap_explanation")
    private ShapExplanationDto shapExplanation;

    @JsonProperty("note")
    private String note;

    public DropoutPredictionResponse() {}

    public double getDropoutProbability() {
        return dropoutProbability;
    }

    public void setDropoutProbability(double dropoutProbability) {
        this.dropoutProbability = dropoutProbability;
    }

    public boolean isPredictedDropout() {
        return predictedDropout;
    }

    public void setPredictedDropout(boolean predictedDropout) {
        this.predictedDropout = predictedDropout;
    }

    public String getRiskTier() {
        return riskTier;
    }

    public void setRiskTier(String riskTier) {
        this.riskTier = riskTier;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getPredictedAt() {
        return predictedAt;
    }

    public void setPredictedAt(String predictedAt) {
        this.predictedAt = predictedAt;
    }

    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public ShapExplanationDto getShapExplanation() {
        return shapExplanation;
    }

    public void setShapExplanation(ShapExplanationDto shapExplanation) {
        this.shapExplanation = shapExplanation;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
