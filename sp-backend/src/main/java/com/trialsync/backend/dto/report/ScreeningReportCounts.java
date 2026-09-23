package com.trialsync.backend.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Port of {@code trialsync.reports.assembler.ScreeningReportCounts}. */
public record ScreeningReportCounts(
        @JsonProperty("pass_count") int passCount,
        @JsonProperty("fail_count") int failCount,
        @JsonProperty("unknown_count") int unknownCount) {}
