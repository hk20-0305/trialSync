package com.trialsync.backend.research.cohort.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class CohortClusterItem {

    @JsonProperty("cluster_label")
    private int clusterLabel;

    @JsonProperty("size")
    private int size;

    @JsonProperty("size_pct")
    private double sizePct;

    @JsonProperty("is_noise")
    private boolean isNoise;

    @JsonProperty("means")
    private Map<String, Double> means;

    public CohortClusterItem() {}

    public int getClusterLabel() {
        return clusterLabel;
    }

    public void setClusterLabel(int clusterLabel) {
        this.clusterLabel = clusterLabel;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public double getSizePct() {
        return sizePct;
    }

    public void setSizePct(double sizePct) {
        this.sizePct = sizePct;
    }

    public boolean isNoise() {
        return isNoise;
    }

    public void setNoise(boolean noise) {
        isNoise = noise;
    }

    public Map<String, Double> getMeans() {
        return means;
    }

    public void setMeans(Map<String, Double> means) {
        this.means = means;
    }
}
