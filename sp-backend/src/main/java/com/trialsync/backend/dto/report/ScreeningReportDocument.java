package com.trialsync.backend.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Port of {@code trialsync.reports.assembler.ScreeningReportDocument}.
 *
 * <p>The whole document is assembled from immutable screening rows. Nothing here re-evaluates a
 * criterion, reads a provider, or touches the mutable patient record, so re-rendering an old
 * screening tomorrow produces the same document it produced the day it ran - only
 * {@code generated_at} moves.
 */
public record ScreeningReportDocument(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("template_version") String templateVersion,
        @JsonProperty("generated_at") OffsetDateTime generatedAt,
        @JsonProperty("screening_id") String screeningId,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("screening_date") String screeningDate,
        @JsonProperty("overall_state") String overallState,
        @JsonProperty("patient_snapshot") ScreeningReportPatientSnapshot patientSnapshot,
        @JsonProperty("trial") ScreeningReportTrial trial,
        @JsonProperty("engine_version") String engineVersion,
        @JsonProperty("dsl_version") String dslVersion,
        @JsonProperty("terminology_version") String terminologyVersion,
        @JsonProperty("unit_version") String unitVersion,
        @JsonProperty("counts") ScreeningReportCounts counts,
        @JsonProperty("criteria") List<ScreeningReportCriterion> criteria) {

    /** Port of {@code REPORT_SCHEMA_VERSION}. */
    public static final String SCHEMA_VERSION = "r1-report-v1";

    /** Port of {@code REPORT_TEMPLATE_VERSION}. */
    public static final String TEMPLATE_VERSION = "r1-pdf-template-v1";

    public ScreeningReportDocument {
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
    }
}
