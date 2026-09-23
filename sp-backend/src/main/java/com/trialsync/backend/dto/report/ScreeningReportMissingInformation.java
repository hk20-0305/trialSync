package com.trialsync.backend.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Port of {@code trialsync.reports.assembler.ScreeningReportMissingInformation}: a complete
 * requirement that prevented a deterministic conclusion.
 */
public record ScreeningReportMissingInformation(
        @JsonProperty("fact") String fact,
        @JsonProperty("reason") String reason,
        @JsonProperty("detail") String detail) {}
