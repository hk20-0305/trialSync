package com.trialsync.backend.research.cohort.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

public class CohortProjectionPageResponse {

    @JsonProperty("total")
    private int total;

    @JsonProperty("page")
    private int page;

    @JsonProperty("page_size")
    private int pageSize;

    @JsonProperty("total_pages")
    private int totalPages;

    @JsonProperty("cluster_filter")
    private Integer clusterFilter;

    @JsonProperty("items")
    private List<CohortProjectionItem> items = Collections.emptyList();

    public CohortProjectionPageResponse() {}

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public Integer getClusterFilter() {
        return clusterFilter;
    }

    public void setClusterFilter(Integer clusterFilter) {
        this.clusterFilter = clusterFilter;
    }

    public List<CohortProjectionItem> getItems() {
        return items;
    }

    public void setItems(List<CohortProjectionItem> items) {
        this.items = items;
    }
}
