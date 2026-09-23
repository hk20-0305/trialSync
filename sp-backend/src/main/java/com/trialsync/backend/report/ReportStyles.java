package com.trialsync.backend.report;

/**
 * The named styles of {@code _report_styles}, with the same sizes, leadings, colours and spacing.
 *
 * <p>The reportlab styles inherit from a sample stylesheet, but every attribute the template relies
 * on is overridden there, so nothing is lost by declaring them outright.
 */
public final class ReportStyles {

    public static final ReportStyle TITLE =
            new ReportStyle(true, 24f, 29f, ReportPalette.INK, false, 0f, 5f);
    public static final ReportStyle SUBTITLE =
            new ReportStyle(false, 11f, 15f, ReportPalette.MUTED, false, 0f, 12f);
    public static final ReportStyle DISCLAIMER =
            new ReportStyle(false, 9f, 13f, ReportPalette.DISCLAIMER, false, 0f, 14f);
    public static final ReportStyle SECTION =
            new ReportStyle(true, 14f, 18f, ReportPalette.INK, false, 16f, 8f);
    public static final ReportStyle CRITERION =
            new ReportStyle(true, 11f, 14f, ReportPalette.INK, false, 10f, 6f);
    public static final ReportStyle BODY =
            new ReportStyle(false, 9f, 13f, ReportPalette.INK, false, 0f, 6f);
    public static final ReportStyle SMALL =
            new ReportStyle(false, 8f, 11f, ReportPalette.MUTED, false, 0f, 4f);
    public static final ReportStyle TABLE_HEADER =
            new ReportStyle(true, 7.5f, 9f, ReportPalette.WHITE);
    public static final ReportStyle TABLE_CELL = new ReportStyle(false, 7.5f, 10f, ReportPalette.INK);
    public static final ReportStyle METADATA_LABEL =
            new ReportStyle(true, 7.5f, 10f, ReportPalette.INK);
    public static final ReportStyle RESULT =
            new ReportStyle(true, 10f, 13f, ReportPalette.INK, true, 0f, 0f);
    /** The 8pt muted footer drawn by {@code _draw_page}. */
    public static final ReportStyle FOOTER = new ReportStyle(false, 8f, 8f, ReportPalette.MUTED);

    private ReportStyles() {}
}
