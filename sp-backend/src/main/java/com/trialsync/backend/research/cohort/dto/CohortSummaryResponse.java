package com.trialsync.backend.research.cohort.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class CohortSummaryResponse {

    @JsonProperty("participant_count")
    private int participantCount;

    @JsonProperty("feature_dimension")
    private int featureDimension;

    @JsonProperty("cluster_count")
    private int clusterCount;

    @JsonProperty("noise_count")
    private int noiseCount;

    @JsonProperty("noise_percentage")
    private double noisePercentage;

    @JsonProperty("dbscan_parameters")
    private Map<String, Object> dbscanParameters;

    @JsonProperty("pca_explained_variance")
    private Map<String, Object> pcaExplainedVariance;

    @JsonProperty("artifact_metadata")
    private Map<String, Object> artifactMetadata;

    public CohortSummaryResponse() {}

    public int getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(int participantCount) {
        this.participantCount = participantCount;
    }

    public int getFeatureDimension() {
        return featureDimension;
    }

    public void setFeatureDimension(int featureDimension) {
        this.featureDimension = featureDimension;
    }

    public int getClusterCount() {
        return clusterCount;
    }

    public void setClusterCount(int clusterCount) {
        this.clusterCount = clusterCount;
    }

    public int getNoiseCount() {
        return noiseCount;
    }

    public void setNoiseCount(int noiseCount) {
        this.noiseCount = noiseCount;
    }

    public double getNoisePercentage() {
        return noisePercentage;
    }

    public void setNoisePercentage(double noisePercentage) {
        this.noisePercentage = noisePercentage;
    }

    public Map<String, Object> getDbscanParameters() {
        return dbscanParameters;
    }

    public void setDbscanParameters(Map<String, Object> dbscanParameters) {
        this.dbscanParameters = dbscanParameters;
    }

    public Map<String, Object> getPcaExplainedVariance() {
        return pcaExplainedVariance;
    }

    public void setPcaExplainedVariance(Map<String, Object> pcaExplainedVariance) {
        this.pcaExplainedVariance = pcaExplainedVariance;
    }

    public Map<String, Object> getArtifactMetadata() {
        return artifactMetadata;
    }

    public void setArtifactMetadata(Map<String, Object> artifactMetadata) {
        this.artifactMetadata = artifactMetadata;
    }
}
