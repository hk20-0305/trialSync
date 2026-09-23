package com.trialsync.backend.research.cohort.controller;

import com.trialsync.backend.research.cohort.dto.CohortClusterItem;
import com.trialsync.backend.research.cohort.dto.CohortHealthResponse;
import com.trialsync.backend.research.cohort.dto.CohortNearestRequest;
import com.trialsync.backend.research.cohort.dto.CohortNearestResponse;
import com.trialsync.backend.research.cohort.dto.CohortProjectionPageResponse;
import com.trialsync.backend.research.cohort.dto.CohortSummaryResponse;
import com.trialsync.backend.research.cohort.service.ResearchCohortService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing research endpoints for Cohort Atlas exploration, DBSCAN clusters,
 * PCA 2D projections, and FAISS exact CPU peer similarity search.
 */
@RestController
@RequestMapping("/api/v1/research/cohort")
@Tag(name = "Research - Cohort Atlas", description = "Endpoints for Cohort Atlas discovery, clustering, PCA visualization, and similarity search")
public class ResearchCohortController {

    private final ResearchCohortService cohortService;

    public ResearchCohortController(ResearchCohortService cohortService) {
        this.cohortService = cohortService;
    }

    @GetMapping("/health")
    @Operation(summary = "Cohort Atlas health and readiness check",
               description = "Verifies readiness of Python Cohort Atlas ML service and loaded artifacts")
    public ResponseEntity<CohortHealthResponse> getHealth() {
        return ResponseEntity.ok(cohortService.getCohortHealth());
    }

    @GetMapping("/summary")
    @Operation(summary = "Retrieve cohort metrics, DBSCAN hyperparameters, and PCA variance",
               description = "Returns sample counts, dimension, noise counts, clustering hyperparameters, and PCA explained variance")
    public ResponseEntity<CohortSummaryResponse> getSummary() {
        return ResponseEntity.ok(cohortService.getCohortSummary());
    }

    @GetMapping("/projection")
    @Operation(summary = "Retrieve 2D PCA projection coordinates",
               description = "Returns paginated 2D PC1 and PC2 coordinates with optional cluster filtering")
    public ResponseEntity<CohortProjectionPageResponse> getProjection(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) Integer clusterLabel) {
        return ResponseEntity.ok(cohortService.getCohortProjection(page, pageSize, clusterLabel));
    }

    @GetMapping("/clusters")
    @Operation(summary = "Retrieve cluster summary profiles and participant counts",
               description = "Returns cluster sizes, percentages, noise indicators, and characteristic feature means")
    public ResponseEntity<List<CohortClusterItem>> getClusters() {
        return ResponseEntity.ok(cohortService.getCohortClusters());
    }

    @PostMapping("/nearest")
    @Operation(summary = "Query top-k nearest participant peers",
               description = "Performs exact CPU L2 nearest neighbor query via FAISS index for a known participant ID")
    public ResponseEntity<CohortNearestResponse> getNearest(
            @Valid @RequestBody CohortNearestRequest request) {
        return ResponseEntity.ok(cohortService.getNearestNeighbors(request));
    }
}
