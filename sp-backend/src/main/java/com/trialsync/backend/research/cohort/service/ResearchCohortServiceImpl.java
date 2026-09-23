package com.trialsync.backend.research.cohort.service;

import com.trialsync.backend.research.cohort.client.ResearchCohortClient;
import com.trialsync.backend.research.cohort.dto.CohortClusterItem;
import com.trialsync.backend.research.cohort.dto.CohortHealthResponse;
import com.trialsync.backend.research.cohort.dto.CohortNearestRequest;
import com.trialsync.backend.research.cohort.dto.CohortNearestResponse;
import com.trialsync.backend.research.cohort.dto.CohortProjectionPageResponse;
import com.trialsync.backend.research.cohort.dto.CohortSummaryResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link ResearchCohortService} delegating to {@link ResearchCohortClient}.
 */
@Service
public class ResearchCohortServiceImpl implements ResearchCohortService {

    private static final Logger log = LoggerFactory.getLogger(ResearchCohortServiceImpl.class);

    private final ResearchCohortClient cohortClient;

    public ResearchCohortServiceImpl(ResearchCohortClient cohortClient) {
        this.cohortClient = cohortClient;
    }

    @Override
    public CohortHealthResponse getCohortHealth() {
        return cohortClient.getHealth();
    }

    @Override
    public CohortSummaryResponse getCohortSummary() {
        return cohortClient.getSummary();
    }

    @Override
    public CohortProjectionPageResponse getCohortProjection(int page, int pageSize, Integer clusterLabel) {
        int validatedPage = Math.max(1, page);
        int validatedPageSize = Math.min(500, Math.max(1, pageSize));
        return cohortClient.getProjection(validatedPage, validatedPageSize, clusterLabel);
    }

    @Override
    public List<CohortClusterItem> getCohortClusters() {
        return cohortClient.getClusters();
    }

    @Override
    public CohortNearestResponse getNearestNeighbors(CohortNearestRequest request) {
        return cohortClient.findNearest(request);
    }
}
