package com.trialsync.backend.research.cohort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.research.cohort.controller.ResearchCohortController;
import com.trialsync.backend.research.cohort.dto.CohortClusterItem;
import com.trialsync.backend.research.cohort.dto.CohortHealthResponse;
import com.trialsync.backend.research.cohort.dto.CohortNearestRequest;
import com.trialsync.backend.research.cohort.dto.CohortNearestResponse;
import com.trialsync.backend.research.cohort.dto.CohortNeighborItem;
import com.trialsync.backend.research.cohort.dto.CohortProjectionItem;
import com.trialsync.backend.research.cohort.dto.CohortProjectionPageResponse;
import com.trialsync.backend.research.cohort.dto.CohortSummaryResponse;
import com.trialsync.backend.research.cohort.service.ResearchCohortService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ResearchCohortControllerTest {

    private ResearchCohortService mockService;
    private ResearchCohortController controller;

    @BeforeEach
    void setUp() {
        mockService = mock(ResearchCohortService.class);
        controller = new ResearchCohortController(mockService);
    }

    @Test
    void testGetHealthReturns200() {
        CohortHealthResponse health = new CohortHealthResponse("UP", 400, 400, 0);
        when(mockService.getCohortHealth()).thenReturn(health);

        ResponseEntity<CohortHealthResponse> response = controller.getHealth();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().getStatus());
        assertEquals(400, response.getBody().getParticipantCount());
    }

    @Test
    void testGetSummaryReturns200() {
        CohortSummaryResponse summary = new CohortSummaryResponse();
        summary.setParticipantCount(400);
        summary.setFeatureDimension(33);
        summary.setClusterCount(0);
        summary.setNoiseCount(400);
        summary.setDbscanParameters(Map.of("eps", 0.6, "min_samples", 10));

        when(mockService.getCohortSummary()).thenReturn(summary);

        ResponseEntity<CohortSummaryResponse> response = controller.getSummary();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getParticipantCount());
        assertEquals(33, response.getBody().getFeatureDimension());
    }

    @Test
    void testGetProjectionReturns200() {
        CohortProjectionPageResponse pageResp = new CohortProjectionPageResponse();
        pageResp.setTotal(400);
        pageResp.setPage(1);
        pageResp.setPageSize(50);
        pageResp.setTotalPages(8);
        pageResp.setItems(List.of(new CohortProjectionItem("p1", 1.23, -0.45, -1, true)));

        when(mockService.getCohortProjection(anyInt(), anyInt(), any())).thenReturn(pageResp);

        ResponseEntity<CohortProjectionPageResponse> response = controller.getProjection(1, 50, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getTotal());
        assertEquals(1, response.getBody().getItems().size());
        assertEquals("p1", response.getBody().getItems().get(0).getParticipantId());
    }

    @Test
    void testGetClustersReturns200() {
        CohortClusterItem cluster = new CohortClusterItem();
        cluster.setClusterLabel(-1);
        cluster.setSize(400);
        cluster.setSizePct(1.0);
        cluster.setNoise(true);

        when(mockService.getCohortClusters()).thenReturn(List.of(cluster));

        ResponseEntity<List<CohortClusterItem>> response = controller.getClusters();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(-1, response.getBody().get(0).getClusterLabel());
    }

    @Test
    void testGetNearestReturns200() {
        CohortNeighborItem neighbor = new CohortNeighborItem(1, "peer-1", 10, 1.45, 0.408);
        CohortNearestResponse nearestResp = new CohortNearestResponse("p-target", 5, List.of(neighbor));

        when(mockService.getNearestNeighbors(any(CohortNearestRequest.class))).thenReturn(nearestResp);

        CohortNearestRequest request = new CohortNearestRequest("p-target", 5);
        ResponseEntity<CohortNearestResponse> response = controller.getNearest(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("p-target", response.getBody().getParticipantId());
        assertEquals(1, response.getBody().getNeighbors().size());
        assertEquals("peer-1", response.getBody().getNeighbors().get(0).getParticipantId());
    }

    @Test
    void testControllerPropagatesApplicationError() {
        when(mockService.getCohortHealth())
                .thenThrow(new ApplicationError("ML_SERVICE_UNAVAILABLE", "Cohort service unavailable", 503));

        ApplicationError error = assertThrows(ApplicationError.class, () -> controller.getHealth());
        assertEquals(503, error.getStatusCode());
        assertEquals("ML_SERVICE_UNAVAILABLE", error.getCode());
    }
}
