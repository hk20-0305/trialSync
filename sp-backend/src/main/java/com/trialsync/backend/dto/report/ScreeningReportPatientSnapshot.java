package com.trialsync.backend.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Port of {@code trialsync.reports.assembler.ScreeningReportPatientSnapshot}.
 *
 * <p>Dates are carried as pre-formatted ISO strings, not as date objects, because the report is a
 * rendering contract: the assembler decides the textual form once and both the PDF and any JSON
 * consumer see the identical characters.
 */
public record ScreeningReportPatientSnapshot(
        @JsonProperty("id") String id,
        @JsonProperty("external_id") String externalId,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("date_of_birth") String dateOfBirth,
        @JsonProperty("sex") String sex,
        @JsonProperty("snapshot_version") String snapshotVersion,
        @JsonProperty("content_hash") String contentHash,
        @JsonProperty("as_of_date") String asOfDate) {}
