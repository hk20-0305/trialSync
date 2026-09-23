package com.trialsync.backend.research.dropout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Individual feature contribution entry within a local SHAP explanation.
 */
public class ShapContributionDto {

    private String feature;
    private double value;

    @JsonProperty("shap_value")
    private double shapValue;

    @JsonProperty("abs_magnitude")
    private double absMagnitude;

    private String direction;

    public ShapContributionDto() {}

    public ShapContributionDto(String feature, double value, double shapValue, double absMagnitude, String direction) {
        this.feature = feature;
        this.value = value;
        this.shapValue = shapValue;
        this.absMagnitude = absMagnitude;
        this.direction = direction;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public double getShapValue() {
        return shapValue;
    }

    public void setShapValue(double shapValue) {
        this.shapValue = shapValue;
    }

    public double getAbsMagnitude() {
        return absMagnitude;
    }

    public void setAbsMagnitude(double absMagnitude) {
        this.absMagnitude = absMagnitude;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }
}
