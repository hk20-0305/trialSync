package com.trialsync.backend.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Port of {@code trialsync.reports.assembler.ScreeningReportTrial}. */
public record ScreeningReportTrial(
        @JsonProperty("id") String id,
        @JsonProperty("registry_id") String registryId,
        @JsonProperty("title") String title,
        @JsonProperty("version") int version) {}
