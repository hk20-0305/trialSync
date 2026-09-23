package com.trialsync.backend.dto.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Port of {@code trialsync.reports.assembler.ScreeningReportCriterion}. */
public record ScreeningReportCriterion(
        @JsonProperty("id") String id,
        @JsonProperty("criterion_id") String criterionId,
        @JsonProperty("order") int order,
        @JsonProperty("kind") String kind,
        @JsonProperty("source_text") String sourceText,
        @JsonProperty("result") String result,
        @JsonProperty("truth") String truth,
        @JsonProperty("reason_code") String reasonCode,
        @JsonProperty("canonical_explanation") String canonicalExplanation,
        @JsonProperty("evidence") List<ScreeningReportEvidence> evidence,
        @JsonProperty("rejected_evidence") List<ScreeningReportEvidence> rejectedEvidence,
        @JsonProperty("missing_information") List<ScreeningReportMissingInformation>
                missingInformation) {

    public ScreeningReportCriterion {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        rejectedEvidence = rejectedEvidence == null ? List.of() : List.copyOf(rejectedEvidence);
        missingInformation =
                missingInformation == null ? List.of() : List.copyOf(missingInformation);
    }
}
