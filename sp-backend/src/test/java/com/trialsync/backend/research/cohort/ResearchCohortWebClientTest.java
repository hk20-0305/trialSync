package com.trialsync.backend.research.cohort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.research.cohort.client.ResearchCohortWebClient;
import com.trialsync.backend.research.cohort.dto.CohortClusterItem;
import com.trialsync.backend.research.cohort.dto.CohortHealthResponse;
import com.trialsync.backend.research.cohort.dto.CohortNearestRequest;
import com.trialsync.backend.research.cohort.dto.CohortNearestResponse;
import com.trialsync.backend.research.cohort.dto.CohortProjectionPageResponse;
import com.trialsync.backend.research.cohort.dto.CohortSummaryResponse;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

class ResearchCohortWebClientTest {

    @Test
    void testGetHealthSuccess() {
        String jsonResponse = """
                {
                    "status": "UP",
                    "participant_count": 400,
                    "faiss_total_indexed": 400,
                    "cluster_count": 0
                }
                """;

        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(jsonResponse)
                        .build()
        );

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .baseUrl("http://localhost:8001")
                .build();

        ResearchCohortWebClient client = new ResearchCohortWebClient(webClient);
        CohortHealthResponse health = client.getHealth();

        assertNotNull(health);
        assertEquals("UP", health.getStatus());
        assertEquals(400, health.getParticipantCount());
        assertEquals(400, health.getFaissTotalIndexed());
    }

    @Test
    void testGetSummarySuccess() {
        String jsonResponse = """
                {
                    "participant_count": 400,
                    "feature_dimension": 33,
                    "cluster_count": 0,
                    "noise_count": 400,
                    "noise_percentage": 1.0,
                    "dbscan_parameters": {
                        "eps": 0.6,
                        "min_samples": 10,
                        "metric": "euclidean"
                    },
                    "pca_explained_variance": {
                        "explained_variance_ratio": {"PC1": 0.1825, "PC2": 0.12115},
                        "cumulative_explained_variance": 0.30365
                    },
                    "artifact_metadata": {
                        "faiss_index_type": "IndexFlatL2",
                        "faiss_metric": "L2_EUCLIDEAN"
                    }
                }
                """;

        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(jsonResponse)
                        .build()
        );

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .baseUrl("http://localhost:8001")
                .build();

        ResearchCohortWebClient client = new ResearchCohortWebClient(webClient);
        CohortSummaryResponse summary = client.getSummary();

        assertNotNull(summary);
        assertEquals(400, summary.getParticipantCount());
        assertEquals(33, summary.getFeatureDimension());
        assertEquals(0.6, summary.getDbscanParameters().get("eps"));
    }

    @Test
    void testGetProjectionSuccess() {
        String jsonResponse = """
                {
                    "total": 400,
                    "page": 1,
                    "page_size": 2,
                    "total_pages": 200,
                    "cluster_filter": null,
                    "items": [
                        {
                            "participant_id": "p-1",
                            "pc1": 1.5,
                            "pc2": -0.8,
                            "cluster_label": -1,
                            "is_noise": true
                        },
                        {
                            "participant_id": "p-2",
                            "pc1": -2.1,
                            "pc2": 0.4,
                            "cluster_label": -1,
                            "is_noise": true
                        }
                    ]
                }
                """;

        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(jsonResponse)
                        .build()
        );

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .baseUrl("http://localhost:8001")
                .build();

        ResearchCohortWebClient client = new ResearchCohortWebClient(webClient);
        CohortProjectionPageResponse proj = client.getProjection(1, 2, null);

        assertNotNull(proj);
        assertEquals(400, proj.getTotal());
        assertEquals(2, proj.getItems().size());
        assertEquals("p-1", proj.getItems().get(0).getParticipantId());
        assertTrue(proj.getItems().get(0).isNoise());
    }

    @Test
    void testGetClustersSuccess() {
        String jsonResponse = """
                [
                    {
                        "cluster_label": -1,
                        "size": 400,
                        "size_pct": 1.0,
                        "is_noise": true,
                        "means": {
                            "age": 47.1975
                        }
                    }
                ]
                """;

        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(jsonResponse)
                        .build()
        );

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .baseUrl("http://localhost:8001")
                .build();

        ResearchCohortWebClient client = new ResearchCohortWebClient(webClient);
        List<CohortClusterItem> clusters = client.getClusters();

        assertNotNull(clusters);
        assertEquals(1, clusters.size());
        assertEquals(-1, clusters.get(0).getClusterLabel());
        assertTrue(clusters.get(0).isNoise());
        assertEquals(47.1975, clusters.get(0).getMeans().get("age"), 0.001);
    }

    @Test
    void testFindNearestSuccess() {
        String jsonResponse = """
                {
                    "participant_id": "p-query",
                    "k": 2,
                    "neighbors": [
                        {
                            "rank": 1,
                            "participant_id": "p-peer1",
                            "faiss_index": 5,
                            "l2_distance": 1.234,
                            "similarity_score": 0.44763
                        },
                        {
                            "rank": 2,
                            "participant_id": "p-peer2",
                            "faiss_index": 12,
                            "l2_distance": 2.345,
                            "similarity_score": 0.29895
                        }
                    ]
                }
                """;

        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(jsonResponse)
                        .build()
        );

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .baseUrl("http://localhost:8001")
                .build();

        ResearchCohortWebClient client = new ResearchCohortWebClient(webClient);
        CohortNearestResponse resp = client.findNearest(new CohortNearestRequest("p-query", 2));

        assertNotNull(resp);
        assertEquals("p-query", resp.getParticipantId());
        assertEquals(2, resp.getNeighbors().size());
        assertEquals("p-peer1", resp.getNeighbors().get(0).getParticipantId());
        assertEquals(1.234, resp.getNeighbors().get(0).getL2Distance(), 0.001);
    }

    @Test
    void testConnectionRefusedThrows503() {
        ExchangeFunction exchangeFunction = request -> Mono.error(
                new WebClientRequestException(
                        new RuntimeException("Connection refused"),
                        HttpMethod.GET,
                        URI.create("http://localhost:8001/cohort/health"),
                        HttpHeaders.EMPTY
                )
        );

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .baseUrl("http://localhost:8001")
                .build();

        ResearchCohortWebClient client = new ResearchCohortWebClient(webClient);
        ApplicationError error = assertThrows(ApplicationError.class, () -> client.getHealth());
        assertEquals(503, error.getStatusCode());
        assertEquals("ML_SERVICE_UNAVAILABLE", error.getCode());
    }

    @Test
    void testParticipantNotFoundThrows404() {
        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.NOT_FOUND)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"detail\": \"Participant ID not found\"}")
                        .build()
        );

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .baseUrl("http://localhost:8001")
                .build();

        ResearchCohortWebClient client = new ResearchCohortWebClient(webClient);
        ApplicationError error = assertThrows(ApplicationError.class, () ->
                client.findNearest(new CohortNearestRequest("unknown-id", 5))
        );
        assertEquals(404, error.getStatusCode());
        assertEquals("COHORT_PARTICIPANT_NOT_FOUND", error.getCode());
    }

    @Test
    void testInvalidRequestThrows422() {
        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"detail\": \"Validation error on k\"}")
                        .build()
        );

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .baseUrl("http://localhost:8001")
                .build();

        ResearchCohortWebClient client = new ResearchCohortWebClient(webClient);
        ApplicationError error = assertThrows(ApplicationError.class, () ->
                client.findNearest(new CohortNearestRequest("pid", 0))
        );
        assertEquals(422, error.getStatusCode());
        assertEquals("INVALID_COHORT_REQUEST", error.getCode());
    }
}
