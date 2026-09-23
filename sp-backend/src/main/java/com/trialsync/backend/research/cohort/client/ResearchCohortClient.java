package com.trialsync.backend.research.cohort.client;

import com.trialsync.backend.research.cohort.dto.CohortClusterItem;
import com.trialsync.backend.research.cohort.dto.CohortHealthResponse;
import com.trialsync.backend.research.cohort.dto.CohortNearestRequest;
import com.trialsync.backend.research.cohort.dto.CohortNearestResponse;
import com.trialsync.backend.research.cohort.dto.CohortProjectionPageResponse;
import com.trialsync.backend.research.cohort.dto.CohortSummaryResponse;
import java.util.List;

/**
 * Service-to-service client interface for querying the Python Cohort Atlas ML service.
 */
public interface ResearchCohortClient {

    /**
     * Checks health and artifact availability of the Python Cohort ML service.
     */
    CohortHealthResponse getHealth();

    /**
     * Retrieves overall cohort summary, DBSCAN hyperparameters, and PCA explained variance.
     */
    CohortSummaryResponse getSummary();

    /**
     * Retrieves paginated 2D PCA projection coordinates with optional cluster filtering.
     */
    CohortProjectionPageResponse getProjection(int page, int pageSize, Integer clusterLabel);

    /**
     * Retrieves cluster breakdowns, sizes, percentages, and characteristic feature means.
     */
    List<CohortClusterItem> getClusters();

    /**
     * Finds top-k nearest participant peers using the exact CPU FAISS index.
     */
    CohortNearestResponse findNearest(CohortNearestRequest request);
}
