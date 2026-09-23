package com.trialsync.backend.research.dropout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Encapsulates the local SHAP prediction attribution for XGBoost.
 */
public class ShapExplanationDto {

    @JsonProperty("base_value")
    private double baseValue;

    @JsonProperty("predicted_probability")
    private double predictedProbability;

    @JsonProperty("top_contributions")
    private List<ShapContributionDto> topContributions;

    public ShapExplanationDto() {}

    public ShapExplanationDto(double baseValue, double predictedProbability, List<ShapContributionDto> topContributions) {
        this.baseValue = baseValue;
        this.predictedProbability = predictedProbability;
        this.topContributions = topContributions;
    }

    public double getBaseValue() {
        return baseValue;
    }

    public void setBaseValue(double baseValue) {
        this.baseValue = baseValue;
    }

    public double getPredictedProbability() {
        return predictedProbability;
    }

    public void setPredictedProbability(double predictedProbability) {
        this.predictedProbability = predictedProbability;
    }

    public List<ShapContributionDto> getTopContributions() {
        return topContributions;
    }

    public void setTopContributions(List<ShapContributionDto> topContributions) {
        this.topContributions = topContributions;
    }
}
