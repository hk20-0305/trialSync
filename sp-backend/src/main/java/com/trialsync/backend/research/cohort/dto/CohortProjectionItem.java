package com.trialsync.backend.research.cohort.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CohortProjectionItem {

    @JsonProperty("participant_id")
    private String participantId;

    @JsonProperty("pc1")
    private double pc1;

    @JsonProperty("pc2")
    private double pc2;

    @JsonProperty("cluster_label")
    private int clusterLabel;

    @JsonProperty("is_noise")
    private boolean isNoise;

    public CohortProjectionItem() {}

    public CohortProjectionItem(String participantId, double pc1, double pc2, int clusterLabel, boolean isNoise) {
        this.participantId = participantId;
        this.pc1 = pc1;
        this.pc2 = pc2;
        this.clusterLabel = clusterLabel;
        this.isNoise = isNoise;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public double getPc1() {
        return pc1;
    }

    public void setPc1(double pc1) {
        this.pc1 = pc1;
    }

    public double getPc2() {
        return pc2;
    }

    public void setPc2(double pc2) {
        this.pc2 = pc2;
    }

    public int getClusterLabel() {
        return clusterLabel;
    }

    public void setClusterLabel(int clusterLabel) {
        this.clusterLabel = clusterLabel;
    }

    public boolean isNoise() {
        return isNoise;
    }

    public void setNoise(boolean noise) {
        isNoise = noise;
    }
}
