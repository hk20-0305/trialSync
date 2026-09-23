package com.trialsync.backend.research.cohort.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CohortNeighborItem {

    @JsonProperty("rank")
    private int rank;

    @JsonProperty("participant_id")
    private String participantId;

    @JsonProperty("faiss_index")
    private int faissIndex;

    @JsonProperty("l2_distance")
    private double l2Distance;

    @JsonProperty("similarity_score")
    private double similarityScore;

    public CohortNeighborItem() {}

    public CohortNeighborItem(int rank, String participantId, int faissIndex, double l2Distance, double similarityScore) {
        this.rank = rank;
        this.participantId = participantId;
        this.faissIndex = faissIndex;
        this.l2Distance = l2Distance;
        this.similarityScore = similarityScore;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public int getFaissIndex() {
        return faissIndex;
    }

    public void setFaissIndex(int faissIndex) {
        this.faissIndex = faissIndex;
    }

    public double getL2Distance() {
        return l2Distance;
    }

    public void setL2Distance(double l2Distance) {
        this.l2Distance = l2Distance;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }
}
