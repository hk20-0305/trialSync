package com.trialsync.backend.report;

import com.trialsync.backend.dto.report.ScreeningReportCriterion;
import com.trialsync.backend.dto.report.ScreeningReportDocument;
import com.trialsync.backend.dto.report.ScreeningReportEvidence;
import com.trialsync.backend.dto.report.ScreeningReportMissingInformation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Component;

/**
 * Renders a report document into a bounded, multi-page PDF. Port of
 * {@code trialsync.reports.pdf.render_screening_report_pdf}.
 *
 * <p>The library is different - PDFBox rather than reportlab - so the two files cannot be identical
 * byte for byte. Everything that a reader or an auditor can observe is: page size and margins, the
 * palette, every heading and label, the column widths of every table, the footer rule and page
 * numbering, the document metadata, and above all the evidence, which is printed exactly as the
 * engine recorded it and never re-derived.
 *
 * <p>The story is assembled first and laid out second, the same two-phase arrangement platypus uses,
 * which is what keeps page breaks from being hand-placed: a criterion that no longer fits is pushed
 * whole onto the next page, and a table that no longer fits is cut between rows with its header
 * repeated.
 */
@Component
public class ScreeningReportPdfRenderer {

    private static final float INCH = 72f;
    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
    private static final float LEFT_MARGIN = 0.62f * INCH;
    private static final float RIGHT_MARGIN = 0.62f * INCH;
    private static final float TOP_MARGIN = 0.55f * INCH;
    private static final float BOTTOM_MARGIN = 0.75f * INCH;

    /**
     * {@code SimpleDocTemplate} pads its frame by 6 points on every side; reproducing the padding
     * keeps the text block in the same place on the page.
     */
    private static final float FRAME_PADDING = 6f;

    private static final float CONTENT_X = LEFT_MARGIN + FRAME_PADDING;
    private static final float CONTENT_WIDTH =
            PAGE_WIDTH - LEFT_MARGIN - RIGHT_MARGIN - 2 * FRAME_PADDING;
    private static final float CONTENT_TOP = PAGE_HEIGHT - TOP_MARGIN - FRAME_PADDING;
    private static final float CONTENT_BOTTOM = BOTTOM_MARGIN + FRAME_PADDING;

    private static final float FOOTER_RULE_Y = 0.57f * INCH;
    private static final float FOOTER_TEXT_Y = 0.36f * INCH;
    private static final String FOOTER_LABEL = "TrialSync · Canonical screening report";

    private static final String DISCLAIMER =
            "Educational synthetic-data prototype. This report records one stored deterministic "
                    + "screening result; it is not clinical advice, a diagnosis, or an enrollment decision.";

    private static final float[] METADATA_COLUMNS = {1.7f * INCH, 4.85f * INCH};
    private static final float[] EVIDENCE_COLUMNS = {
        0.8f * INCH, 1.55f * INCH, 0.7f * INCH, 1.05f * INCH, 2.45f * INCH
    };
    private static final float[] MISSING_COLUMNS = {1.45f * INCH, 1.15f * INCH, 4.0f * INCH};
    private static final float[] CRITERION_HEADER_COLUMNS = {5.45f * INCH, 1.1f * INCH};

    private static final TableFlowable.Style METADATA_STYLE =
            new TableFlowable.Style(
                    null,
                    ReportPalette.SURFACE,
                    null,
                    true,
                    false,
                    0.35f,
                    ReportPalette.LINE,
                    7f,
                    7f,
                    6f,
                    6f,
                    false,
                    false);

    private static final TableFlowable.Style DATA_TABLE_STYLE =
            new TableFlowable.Style(
                    ReportPalette.INK,
                    null,
                    null,
                    true,
                    false,
                    0.35f,
                    ReportPalette.LINE,
                    6f,
                    6f,
                    5f,
                    5f,
                    true,
                    false);

    /** A label/value pair of a metadata table. */
    private record MetadataRow(String label, Object value) {}

    /** Renders the document, returning the PDF bytes the endpoint streams. */
    public byte[] render(ScreeningReportDocument report) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            ReportFonts fonts = ReportFonts.load(document);

            List<Flowable> story = new ArrayList<>(summaryStory(report, fonts));
            story.addAll(criterionStory(report, fonts));
            layout(document, fonts, story);
            applyMetadata(document);

            document.save(buffer);
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Screening report could not be rendered", exception);
        }
    }

    // ------------------------------------------------------------------ story

    /** Port of {@code _summary_story}. */
    private List<Flowable> summaryStory(ScreeningReportDocument report, ReportFonts fonts) {
        List<Flowable> story = new ArrayList<>();
        story.add(literal(fonts, "TrialSync", ReportStyles.TITLE));
        story.add(literal(fonts, "Canonical screening report", ReportStyles.SUBTITLE));
        story.add(literal(fonts, DISCLAIMER, ReportStyles.DISCLAIMER));
        story.add(
                metadataTable(
                        fonts,
                        List.of(
                                new MetadataRow("Screening ID", report.screeningId()),
                                new MetadataRow(
                                        "Overall result", report.overallState().replace("_", " ")),
                                new MetadataRow("Screening date", report.screeningDate()),
                                new MetadataRow("Created at", isoFormat(report.createdAt())),
                                new MetadataRow(
                                        "Report generated at", isoFormat(report.generatedAt())))));
        story.add(new Flowable.Spacer(9f));

        story.add(literal(fonts, "Patient snapshot", ReportStyles.SECTION));
        story.add(
                metadataTable(
                        fonts,
                        List.of(
                                new MetadataRow(
                                        "Display name", report.patientSnapshot().displayName()),
                                new MetadataRow(
                                        "Synthetic patient ID", report.patientSnapshot().externalId()),
                                new MetadataRow("Snapshot ID", report.patientSnapshot().id()),
                                new MetadataRow(
                                        "Snapshot version", report.patientSnapshot().snapshotVersion()),
                                new MetadataRow(
                                        "Content hash", report.patientSnapshot().contentHash()),
                                new MetadataRow(
                                        "Date of birth", report.patientSnapshot().dateOfBirth()),
                                new MetadataRow("Biological sex", report.patientSnapshot().sex()),
                                new MetadataRow(
                                        "Snapshot as of", report.patientSnapshot().asOfDate()))));
        story.add(new Flowable.Spacer(8f));

        story.add(literal(fonts, "Trial version", ReportStyles.SECTION));
        story.add(
                metadataTable(
                        fonts,
                        List.of(
                                new MetadataRow("Registry label", report.trial().registryId()),
                                new MetadataRow("Trial title", report.trial().title()),
                                new MetadataRow("Approved version", report.trial().version()),
                                new MetadataRow("Immutable trial version ID", report.trial().id()))));
        story.add(new Flowable.Spacer(8f));

        story.add(literal(fonts, "Engine and result summary", ReportStyles.SECTION));
        story.add(
                metadataTable(
                        fonts,
                        List.of(
                                new MetadataRow("Engine version", report.engineVersion()),
                                new MetadataRow("Rule DSL version", report.dslVersion()),
                                new MetadataRow("Terminology version", report.terminologyVersion()),
                                new MetadataRow("Unit version", report.unitVersion()),
                                new MetadataRow("Pass criteria", report.counts().passCount()),
                                new MetadataRow("Fail criteria", report.counts().failCount()),
                                new MetadataRow("Unknown criteria", report.counts().unknownCount()),
                                new MetadataRow("Report schema", report.schemaVersion()),
                                new MetadataRow("Report template", report.templateVersion()))));

        story.add(literal(fonts, "Criterion evidence", ReportStyles.SECTION));
        return story;
    }

    /** Port of {@code _criterion_story}. */
    private List<Flowable> criterionStory(ScreeningReportDocument report, ReportFonts fonts) {
        List<Flowable> story = new ArrayList<>();
        for (ScreeningReportCriterion criterion : report.criteria()) {
            String result = criterion.result() == null ? "" : criterion.result().replace("_", " ");

            TableFlowable.Style headingStyle =
                    new TableFlowable.Style(
                            null,
                            null,
                            ReportPalette.resultBackground(criterion.result()),
                            false,
                            true,
                            0.35f,
                            ReportPalette.LINE,
                            8f,
                            8f,
                            6f,
                            6f,
                            false,
                            true);
            story.add(
                    new TableFlowable(
                            List.of(
                                    List.of(
                                            paragraph(
                                                    fonts,
                                                    "Criterion "
                                                            + criterion.order()
                                                            + " · "
                                                            + criterion.kind(),
                                                    ReportStyles.CRITERION),
                                            paragraph(fonts, result, ReportStyles.RESULT))),
                            CRITERION_HEADER_COLUMNS,
                            headingStyle));
            story.add(paragraph(fonts, criterion.sourceText(), ReportStyles.BODY));
            story.add(
                    runs(
                            fonts,
                            ReportStyles.SMALL,
                            "Evaluation ID:",
                            criterion.id(),
                            "Criterion ID:",
                            criterion.criterionId()));
            story.add(
                    runs(
                            fonts,
                            ReportStyles.BODY,
                            "Reason code:",
                            criterion.reasonCode(),
                            "Truth:",
                            criterion.truth()));
            story.add(paragraph(fonts, criterion.canonicalExplanation(), ReportStyles.BODY));
            story.add(literal(fonts, "Recorded evidence", ReportStyles.SMALL));

            if (criterion.evidence().isEmpty()) {
                story.add(
                        literal(fonts, "No supporting evidence was recorded.", ReportStyles.SMALL));
            } else {
                story.add(evidenceTable(fonts, criterion.evidence()));
            }
            if (!criterion.missingInformation().isEmpty()) {
                story.add(literal(fonts, "Missing information", ReportStyles.SMALL));
                story.add(missingInformationTable(fonts, criterion.missingInformation()));
            }
            if (!criterion.rejectedEvidence().isEmpty()) {
                story.add(literal(fonts, "Rejected or stale evidence", ReportStyles.SMALL));
                story.add(evidenceTable(fonts, criterion.rejectedEvidence()));
            }
            story.add(new Flowable.Spacer(5f));
        }
        return story;
    }

    /** Port of {@code _evidence_table}. */
    private TableFlowable evidenceTable(ReportFonts fonts, List<ScreeningReportEvidence> items) {
        List<List<ParagraphFlowable>> rows = new ArrayList<>();
        rows.add(
                headerRow(fonts, List.of("Fact ID", "Value", "Unit", "Effective date", "Source")));
        for (ScreeningReportEvidence item : items) {
            Object value = item.value() == null ? "Recorded fact" : item.value();
            rows.add(
                    List.of(
                            paragraph(fonts, item.factId(), ReportStyles.TABLE_CELL),
                            paragraph(fonts, value, ReportStyles.TABLE_CELL),
                            paragraph(fonts, item.unit(), ReportStyles.TABLE_CELL),
                            paragraph(fonts, item.effectiveDate(), ReportStyles.TABLE_CELL),
                            paragraph(fonts, item.sourceLabel(), ReportStyles.TABLE_CELL)));
        }
        return new TableFlowable(rows, EVIDENCE_COLUMNS, DATA_TABLE_STYLE);
    }

    /** Port of {@code _missing_information_table}. */
    private TableFlowable missingInformationTable(
            ReportFonts fonts, List<ScreeningReportMissingInformation> items) {
        List<List<ParagraphFlowable>> rows = new ArrayList<>();
        rows.add(headerRow(fonts, List.of("Required fact", "Reason", "What is needed")));
        for (ScreeningReportMissingInformation item : items) {
            rows.add(
                    List.of(
                            paragraph(fonts, item.fact(), ReportStyles.TABLE_CELL),
                            paragraph(fonts, item.reason(), ReportStyles.TABLE_CELL),
                            paragraph(fonts, item.detail(), ReportStyles.TABLE_CELL)));
        }
        return new TableFlowable(rows, MISSING_COLUMNS, DATA_TABLE_STYLE);
    }

    /** Port of {@code _metadata_table}. */
    private TableFlowable metadataTable(ReportFonts fonts, List<MetadataRow> entries) {
        List<List<ParagraphFlowable>> rows = new ArrayList<>();
        for (MetadataRow entry : entries) {
            rows.add(
                    List.of(
                            paragraph(fonts, entry.label(), ReportStyles.METADATA_LABEL),
                            paragraph(fonts, entry.value(), ReportStyles.TABLE_CELL)));
        }
        return new TableFlowable(rows, METADATA_COLUMNS, METADATA_STYLE);
    }

    private List<ParagraphFlowable> headerRow(ReportFonts fonts, List<String> headers) {
        List<ParagraphFlowable> cells = new ArrayList<>(headers.size());
        for (String header : headers) {
            cells.add(paragraph(fonts, header, ReportStyles.TABLE_HEADER));
        }
        return List.copyOf(cells);
    }

    // ------------------------------------------------------------------ layout

    /**
     * Places the story into frames, one per page, the way platypus fills a
     * {@code SimpleDocTemplate}.
     *
     * <p>{@code spaceBefore} is suppressed at the top of a page, as in reportlab, so a section
     * heading that lands on a page break does not print with a stray gap above it.
     */
    private void layout(PDDocument document, ReportFonts fonts, List<Flowable> story)
            throws IOException {
        Deque<Flowable> pending = new ArrayDeque<>(story);
        PDPageContentStream stream = null;
        int pageNumber = 0;
        float cursor = CONTENT_TOP;
        boolean atTop = true;

        while (!pending.isEmpty()) {
            if (stream == null) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                stream = new PDPageContentStream(document, page);
                pageNumber++;
                cursor = CONTENT_TOP;
                atTop = true;
            }

            Flowable flowable = pending.removeFirst();
            float before = atTop ? 0f : flowable.spaceBefore();
            float height = flowable.height(fonts, CONTENT_WIDTH);

            if (cursor - before - height >= CONTENT_BOTTOM) {
                cursor -= before;
                flowable.draw(stream, fonts, CONTENT_X, cursor, CONTENT_WIDTH);
                cursor -= height + flowable.spaceAfter();
                atTop = false;
                continue;
            }

            List<Flowable> parts =
                    flowable.split(fonts, CONTENT_WIDTH, cursor - before - CONTENT_BOTTOM);
            if (parts.size() == 2) {
                cursor -= before;
                parts.get(0).draw(stream, fonts, CONTENT_X, cursor, CONTENT_WIDTH);
                drawFooter(stream, fonts, pageNumber);
                stream.close();
                stream = null;
                pending.addFirst(parts.get(1));
                continue;
            }

            if (atTop) {
                // Taller than an empty frame and unsplittable: draw it rather than loop for ever.
                flowable.draw(stream, fonts, CONTENT_X, cursor, CONTENT_WIDTH);
                cursor -= height + flowable.spaceAfter();
                atTop = false;
                continue;
            }

            drawFooter(stream, fonts, pageNumber);
            stream.close();
            stream = null;
            pending.addFirst(flowable);
        }

        if (stream != null) {
            drawFooter(stream, fonts, pageNumber);
            stream.close();
        }
        if (document.getNumberOfPages() == 0) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream empty = new PDPageContentStream(document, page)) {
                drawFooter(empty, fonts, 1);
            }
        }
    }

    /** Port of {@code _draw_page}: the rule, the wordmark and the page number. */
    private void drawFooter(PDPageContentStream stream, ReportFonts fonts, int pageNumber)
            throws IOException {
        ReportPalette.LINE.applyStroke(stream);
        stream.setLineWidth(0.5f);
        stream.moveTo(LEFT_MARGIN, FOOTER_RULE_Y);
        stream.lineTo(PAGE_WIDTH - RIGHT_MARGIN, FOOTER_RULE_Y);
        stream.stroke();

        String label = fonts.sanitize(FOOTER_LABEL);
        drawFooterText(stream, fonts, label, LEFT_MARGIN);

        String pageLabel = fonts.sanitize("Page " + pageNumber);
        float width = fonts.width(false, pageLabel, ReportStyles.FOOTER.fontSize());
        drawFooterText(stream, fonts, pageLabel, PAGE_WIDTH - RIGHT_MARGIN - width);
    }

    private void drawFooterText(
            PDPageContentStream stream, ReportFonts fonts, String text, float x) throws IOException {
        stream.beginText();
        stream.setFont(fonts.regular(), ReportStyles.FOOTER.fontSize());
        ReportStyles.FOOTER.color().applyFill(stream);
        stream.newLineAtOffset(x, FOOTER_TEXT_Y);
        stream.showText(text);
        stream.endText();
    }

    private void applyMetadata(PDDocument document) {
        PDDocumentInformation information = document.getDocumentInformation();
        information.setTitle("TrialSync canonical screening report");
        information.setAuthor("TrialSync");
        information.setSubject("Evidence-backed synthetic screening result");
        information.setCreator("TrialSync");
    }

    // ------------------------------------------------------------------ helpers

    private ParagraphFlowable paragraph(ReportFonts fonts, Object value, ReportStyle style) {
        return ParagraphFlowable.of(fonts.sanitize(ReportText.safeText(value)), style);
    }

    private ParagraphFlowable literal(ReportFonts fonts, String text, ReportStyle style) {
        return ParagraphFlowable.of(fonts.sanitize(text), style);
    }

    /** The two label/value lines the template writes with {@code <b>} markup. */
    private ParagraphFlowable runs(
            ReportFonts fonts,
            ReportStyle style,
            String firstLabel,
            Object firstValue,
            String secondLabel,
            Object secondValue) {
        return new ParagraphFlowable(
                List.of(
                        ParagraphFlowable.Run.strong(fonts.sanitize(firstLabel)),
                        ParagraphFlowable.Run.plain(
                                fonts.sanitize(" " + ReportText.safeText(firstValue) + " · ")),
                        ParagraphFlowable.Run.strong(fonts.sanitize(secondLabel)),
                        ParagraphFlowable.Run.plain(
                                fonts.sanitize(" " + ReportText.safeText(secondValue)))),
                style);
    }

    /**
     * Python's {@code datetime.isoformat()}, which is what the template prints.
     *
     * <p>{@link OffsetDateTime#toString()} is not the same function: it drops zero seconds, prints
     * fractions in groups of three digits and writes {@code Z} for UTC, where Python always prints
     * seconds, prints exactly six fractional digits when there are any, and writes {@code +00:00}.
     * The report is a compatibility surface, so the Python spelling wins.
     */
    static String isoFormat(OffsetDateTime value) {
        StringBuilder out = new StringBuilder(32);
        out.append(String.format("%04d-%02d-%02d", value.getYear(), value.getMonthValue(), value.getDayOfMonth()));
        out.append('T');
        out.append(String.format("%02d:%02d:%02d", value.getHour(), value.getMinute(), value.getSecond()));
        int microseconds = value.getNano() / 1000;
        if (microseconds != 0) {
            out.append(String.format(".%06d", microseconds));
        }
        ZoneOffset offset = value.getOffset();
        int totalSeconds = offset.getTotalSeconds();
        char sign = totalSeconds < 0 ? '-' : '+';
        int absolute = Math.abs(totalSeconds);
        out.append(sign);
        out.append(String.format("%02d:%02d", absolute / 3600, (absolute % 3600) / 60));
        if (absolute % 60 != 0) {
            out.append(String.format(":%02d", absolute % 60));
        }
        return out.toString();
    }
}
