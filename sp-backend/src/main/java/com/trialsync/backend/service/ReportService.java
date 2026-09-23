package com.trialsync.backend.service;

import com.trialsync.backend.dto.report.ScreeningReportCounts;
import com.trialsync.backend.dto.report.ScreeningReportCriterion;
import com.trialsync.backend.dto.report.ScreeningReportDocument;
import com.trialsync.backend.dto.report.ScreeningReportEvidence;
import com.trialsync.backend.dto.report.ScreeningReportMissingInformation;
import com.trialsync.backend.dto.report.ScreeningReportPatientSnapshot;
import com.trialsync.backend.dto.report.ScreeningReportTrial;
import com.trialsync.backend.entity.CriterionEvaluation;
import com.trialsync.backend.entity.PatientSnapshot;
import com.trialsync.backend.entity.Screening;
import com.trialsync.backend.entity.User;
import com.trialsync.backend.report.ScreeningReportPdfRenderer;
import com.trialsync.backend.security.SecurityContext;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles a screening report and renders it. Port of {@code trialsync.reports.assembler} plus the
 * service half of the report download endpoint.
 *
 * <p>A report is built from immutable rows only: the screening, its snapshot and its stored criterion
 * evaluations. Nothing here re-evaluates a criterion, consults a provider or reads the mutable
 * patient record, so re-downloading a screening from last month prints last month's evidence. The
 * only value that moves between two renders of the same screening is {@code generated_at}.
 */
@Service
public class ReportService {

    private static final String SYNTHETIC_PATIENT = "Synthetic patient";

    private final ScreeningService screeningService;
    private final SnapshotService snapshotService;
    private final ScreeningReportPdfRenderer renderer;

    public ReportService(
            ScreeningService screeningService,
            SnapshotService snapshotService,
            ScreeningReportPdfRenderer renderer) {
        this.screeningService = screeningService;
        this.snapshotService = snapshotService;
        this.renderer = renderer;
    }

    /**
     * Port of {@code download_screening_report}: authorise, assemble, render.
     *
     * <p>The transaction is read-only and spans the render because the assembler walks the
     * screening's evaluation collection, which is lazy. Rendering is pure computation over the
     * assembled document, so holding the connection for it costs nothing that a second query would
     * not cost more.
     */
    @Transactional(readOnly = true)
    public byte[] renderScreeningReport(UUID screeningId) {
        User user = SecurityContext.require();
        Screening screening = screeningService.ownedScreening(user.getId(), screeningId);
        return renderer.render(assemble(screening, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    /**
     * Port of {@code assemble_screening_report}.
     *
     * <p>The patient labels come from the snapshot's frozen {@code source_summary} with Python's
     * {@code dict.get(key, default)} semantics: an absent key yields {@code "Synthetic patient"},
     * while a key that is present and null yields the string {@code "None"} - not the default. The
     * distinction is preserved rather than tidied, because the printed report is a compatibility
     * surface and the label is what a reviewer compares against the Python original.
     */
    public ScreeningReportDocument assemble(Screening screening, OffsetDateTime generatedAt) {
        PatientSnapshot snapshot = screeningService.resolveSnapshot(screening);
        Map<String, Object> source = snapshotService.readSource(snapshot);
        String screeningDate = iso(screening.getScreeningDate());

        int passCount = 0;
        int failCount = 0;
        int unknownCount = 0;
        List<ScreeningReportCriterion> criteria = new ArrayList<>();
        for (CriterionEvaluation item : screening.getEvaluations()) {
            String result = item.getResult().value();
            if ("pass".equals(result)) {
                passCount++;
            } else if ("fail".equals(result)) {
                failCount++;
            } else if ("unknown".equals(result)) {
                unknownCount++;
            }
            criteria.add(
                    new ScreeningReportCriterion(
                            item.getId().toString(),
                            item.getCriterionId().toString(),
                            item.getCriterionOrder(),
                            item.getCriterionKind().value(),
                            item.getCriterionSourceText(),
                            result,
                            item.getTruth(),
                            item.getReasonCode(),
                            item.getCanonicalExplanation(),
                            evidence(item.getEvidenceJson()),
                            evidence(item.getRejectedEvidenceJson()),
                            missingInformation(item.getMissingInformationJson())));
        }

        Object externalId =
                source.containsKey("external_id") ? source.get("external_id") : SYNTHETIC_PATIENT;
        Object displayName =
                source.containsKey("display_name") ? source.get("display_name") : SYNTHETIC_PATIENT;
        Object sex = source.get("sex");

        return new ScreeningReportDocument(
                ScreeningReportDocument.SCHEMA_VERSION,
                ScreeningReportDocument.TEMPLATE_VERSION,
                asUtc(generatedAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : generatedAt),
                screening.getId().toString(),
                asUtc(screening.getCreatedAt()),
                screeningDate,
                screening.getOverallState().value(),
                new ScreeningReportPatientSnapshot(
                        snapshot.getId().toString(),
                        String.valueOf(externalId),
                        String.valueOf(displayName),
                        snapshot.getDateOfBirth() == null ? null : iso(snapshot.getDateOfBirth()),
                        sex == null ? null : String.valueOf(sex),
                        snapshot.getSnapshotVersion(),
                        snapshot.getContentHash(),
                        screeningDate),
                new ScreeningReportTrial(
                        screening.getTrialVersionId().toString(),
                        screening.getTrialRegistryId(),
                        screening.getTrialTitle(),
                        screening.getTrialVersionNumber()),
                screening.getEngineVersion(),
                screening.getDslVersion(),
                screening.getTerminologyVersion(),
                screening.getUnitVersion(),
                new ScreeningReportCounts(passCount, failCount, unknownCount),
                criteria);
    }

    /**
     * Reads a stored evidence column into report records.
     *
     * <p>{@code value} stays untyped, as it is in Python: whatever the engine wrote is what the PDF
     * prints. Nothing is added here - an evidence list is only ever produced by the engine, and this
     * method may not lengthen, shorten or rewrite one.
     */
    private List<ScreeningReportEvidence> evidence(String json) {
        List<Map<String, Object>> stored = screeningService.readJsonArray(json);
        List<ScreeningReportEvidence> items = new ArrayList<>(stored.size());
        for (Map<String, Object> entry : stored) {
            items.add(
                    new ScreeningReportEvidence(
                            text(entry.get("fact_id")),
                            text(entry.get("source_label")),
                            entry.get("value"),
                            text(entry.get("unit")),
                            text(entry.get("effective_date"))));
        }
        return items;
    }

    /** Reads a stored missing-information column into report records. */
    private List<ScreeningReportMissingInformation> missingInformation(String json) {
        List<Map<String, Object>> stored = screeningService.readJsonArray(json);
        List<ScreeningReportMissingInformation> items = new ArrayList<>(stored.size());
        for (Map<String, Object> entry : stored) {
            items.add(
                    new ScreeningReportMissingInformation(
                            text(entry.get("fact")),
                            text(entry.get("reason")),
                            text(entry.get("detail"))));
        }
        return items;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Port of {@code _iso}: an object with {@code isoformat} prints it, anything else prints itself. */
    private static String iso(Object value) {
        return String.valueOf(value);
    }

    /**
     * Port of {@code _as_utc}.
     *
     * <p>Python only has to defend against a naive datetime; a Java {@link OffsetDateTime} always
     * carries an offset, so the equivalent guarantee is that the offset is UTC. Normalising here
     * means the printed timestamp reads {@code +00:00} whatever the JDBC session or JVM default
     * happens to be, which is exactly what the Python report prints for a {@code timestamptz}.
     */
    private static OffsetDateTime asUtc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC);
    }
}
