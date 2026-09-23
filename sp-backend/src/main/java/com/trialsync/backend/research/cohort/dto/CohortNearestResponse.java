package com.trialsync.backend.research.cohort.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

public class CohortNearestResponse {

    @JsonProperty("participant_id")
    private String participantId;

    @JsonProperty("k")
    private int k;

    @JsonProperty("neighbors")
    private List<CohortNeighborItem> neighbors = Collections.emptyList();

    public CohortNearestResponse() {}

    public CohortNearestResponse(String participantId, int k, List<CohortNeighborItem> neighbors) {
        this.participantId = participantId;
        this.k = k;
        this.neighbors = neighbors;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public int getK() {
        return k;
    }

    public void setK(int k) {
        this.k = k;
    }

    public List<CohortNeighborItem> getNeighbors() {
        return neighbors;
    }

    public void setNeighbors(List<CohortNeighborItem> neighbors) {
        this.neighbors = neighbors;
    }
}
