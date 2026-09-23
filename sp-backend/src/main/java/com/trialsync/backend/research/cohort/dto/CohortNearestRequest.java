package com.trialsync.backend.research.cohort.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class CohortNearestRequest {

    @NotBlank(message = "Participant ID is required")
    @JsonProperty("participant_id")
    private String participantId;

    @Min(value = 1, message = "k must be at least 1")
    @Max(value = 50, message = "k cannot exceed 50")
    @JsonProperty("k")
    private int k = 5;

    public CohortNearestRequest() {}

    public CohortNearestRequest(String participantId, int k) {
        this.participantId = participantId;
        this.k = k;
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
}
