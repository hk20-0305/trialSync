package com.trialsync.backend.research.cohort.service;

import com.trialsync.backend.research.cohort.dto.CohortClusterItem;
import com.trialsync.backend.research.cohort.dto.CohortHealthResponse;
import com.trialsync.backend.research.cohort.dto.CohortNearestRequest;
import com.trialsync.backend.research.cohort.dto.CohortNearestResponse;
import com.trialsync.backend.research.cohort.dto.CohortProjectionPageResponse;
import com.trialsync.backend.research.cohort.dto.CohortSummaryResponse;
import java.util.List;

/**
 * Service interface for Cohort Atlas business logic and ML service coordination.
 */
public interface ResearchCohortService {

    CohortHealthResponse getCohortHealth();

    CohortSummaryResponse getCohortSummary();

    CohortProjectionPageResponse getCohortProjection(int page, int pageSize, Integer clusterLabel);

    List<CohortClusterItem> getCohortClusters();

    CohortNearestResponse getNearestNeighbors(CohortNearestRequest request);
}
