package com.trialsync.backend.research.cohort.client;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.research.cohort.dto.CohortClusterItem;
import com.trialsync.backend.research.cohort.dto.CohortHealthResponse;
import com.trialsync.backend.research.cohort.dto.CohortNearestRequest;
import com.trialsync.backend.research.cohort.dto.CohortNearestResponse;
import com.trialsync.backend.research.cohort.dto.CohortProjectionPageResponse;
import com.trialsync.backend.research.cohort.dto.CohortSummaryResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Spring WebClient-based implementation of {@link ResearchCohortClient}.
 */
@Component
public class ResearchCohortWebClient implements ResearchCohortClient {

    private static final Logger log = LoggerFactory.getLogger(ResearchCohortWebClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient;

    public ResearchCohortWebClient(WebClient researchMlWebClient) {
        this.webClient = researchMlWebClient;
    }

    @Override
    public CohortHealthResponse getHealth() {
        log.debug("Calling Python ML service GET /cohort/health");
        try {
            CohortHealthResponse response = webClient.get()
                    .uri("/cohort/health")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(CohortHealthResponse.class)
                    .block(TIMEOUT);

            if (response == null) {
                throw new ApplicationError("MALFORMED_ML_RESPONSE", "Cohort service returned empty body for health check", 502);
            }
            return response;
        } catch (Exception ex) {
            throw handleException("health", ex);
        }
    }

    @Override
    public CohortSummaryResponse getSummary() {
        log.debug("Calling Python ML service GET /cohort/summary");
        try {
            CohortSummaryResponse response = webClient.get()
                    .uri("/cohort/summary")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(CohortSummaryResponse.class)
                    .block(TIMEOUT);

            if (response == null) {
                throw new ApplicationError("MALFORMED_ML_RESPONSE", "Cohort service returned empty body for summary", 502);
            }
            return response;
        } catch (Exception ex) {
            throw handleException("summary", ex);
        }
    }

    @Override
    public CohortProjectionPageResponse getProjection(int page, int pageSize, Integer clusterLabel) {
        log.debug("Calling Python ML service GET /cohort/projection page={}, size={}, cluster={}", page, pageSize, clusterLabel);
        try {
            CohortProjectionPageResponse response = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/cohort/projection")
                                .queryParam("page", page)
                                .queryParam("page_size", pageSize);
                        if (clusterLabel != null) {
                            uriBuilder.queryParam("cluster_label", clusterLabel);
                        }
                        return uriBuilder.build();
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(CohortProjectionPageResponse.class)
                    .block(TIMEOUT);

            if (response == null) {
                throw new ApplicationError("MALFORMED_ML_RESPONSE", "Cohort service returned empty body for projection", 502);
            }
            return response;
        } catch (Exception ex) {
            throw handleException("projection", ex);
        }
    }

    @Override
    public List<CohortClusterItem> getClusters() {
        log.debug("Calling Python ML service GET /cohort/clusters");
        try {
            List<CohortClusterItem> response = webClient.get()
                    .uri("/cohort/clusters")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<CohortClusterItem>>() {})
                    .block(TIMEOUT);

            if (response == null) {
                throw new ApplicationError("MALFORMED_ML_RESPONSE", "Cohort service returned empty body for clusters", 502);
            }
            return response;
        } catch (Exception ex) {
            throw handleException("clusters", ex);
        }
    }

    @Override
    public CohortNearestResponse findNearest(CohortNearestRequest request) {
        log.info("Calling Python ML service POST /cohort/nearest for participant {} (k={})", request.getParticipantId(), request.getK());
        try {
            CohortNearestResponse response = webClient.post()
                    .uri("/cohort/nearest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CohortNearestResponse.class)
                    .block(TIMEOUT);

            if (response == null) {
                throw new ApplicationError("MALFORMED_ML_RESPONSE", "Cohort service returned empty body for nearest neighbors", 502);
            }
            return response;
        } catch (Exception ex) {
            throw handleException("nearest", ex);
        }
    }

    private RuntimeException handleException(String operation, Exception ex) {
        if (ex instanceof ApplicationError) {
            return (ApplicationError) ex;
        }

        if (ex instanceof WebClientResponseException respEx) {
            log.warn("Cohort ML service [{}] returned HTTP {}: {}", operation, respEx.getStatusCode(), respEx.getResponseBodyAsString());
            if (respEx.getStatusCode() == HttpStatus.NOT_FOUND) {
                return new ApplicationError("COHORT_PARTICIPANT_NOT_FOUND", "Participant not found in cohort index: " + respEx.getResponseBodyAsString(), 404);
            }
            if (respEx.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY || respEx.getStatusCode().is4xxClientError()) {
                return new ApplicationError("INVALID_COHORT_REQUEST", "Cohort ML service rejected input: " + respEx.getResponseBodyAsString(), 422);
            }
            return new ApplicationError("ML_SERVICE_ERROR", "Cohort ML service returned error: " + respEx.getStatusCode(), 502);
        }

        if (ex instanceof WebClientRequestException) {
            log.error("Failed to connect to Cohort ML microservice during [{}]: {}", operation, ex.getMessage());
            return new ApplicationError("ML_SERVICE_UNAVAILABLE", "Cohort Atlas ML service is currently unavailable", 503);
        }

        if (ex.getCause() instanceof TimeoutException || ex instanceof IllegalStateException) {
            log.error("Timeout awaiting response from Cohort ML service during [{}]: {}", operation, ex.getMessage());
            return new ApplicationError("ML_SERVICE_TIMEOUT", "Timeout awaiting response from Cohort ML service", 504);
        }

        log.error("Unexpected error in Cohort ML client during [{}]: {}", operation, ex.getMessage());
        return new ApplicationError("ML_INTEGRATION_ERROR", "Failed to communicate with Cohort Atlas ML service: " + ex.getMessage(), 502);
    }
}
