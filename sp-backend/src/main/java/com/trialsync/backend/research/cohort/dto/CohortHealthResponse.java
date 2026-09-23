package com.trialsync.backend.research.cohort.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CohortHealthResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("participant_count")
    private int participantCount;

    @JsonProperty("faiss_total_indexed")
    private int faissTotalIndexed;

    @JsonProperty("cluster_count")
    private int clusterCount;

    public CohortHealthResponse() {}

    public CohortHealthResponse(String status, int participantCount, int faissTotalIndexed, int clusterCount) {
        this.status = status;
        this.participantCount = participantCount;
        this.faissTotalIndexed = faissTotalIndexed;
        this.clusterCount = clusterCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(int participantCount) {
        this.participantCount = participantCount;
    }

    public int getFaissTotalIndexed() {
        return faissTotalIndexed;
    }

    public void setFaissTotalIndexed(int faissTotalIndexed) {
        this.faissTotalIndexed = faissTotalIndexed;
    }

    public int getClusterCount() {
        return clusterCount;
    }

    public void setClusterCount(int clusterCount) {
        this.clusterCount = clusterCount;
    }
}
