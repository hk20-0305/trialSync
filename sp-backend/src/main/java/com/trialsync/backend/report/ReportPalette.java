package com.trialsync.backend.report;

/** The report palette, taken literally from {@code trialsync.reports.pdf}. */
public final class ReportPalette {

    public static final ReportColor INK = ReportColor.ofHex("#203136");
    public static final ReportColor MUTED = ReportColor.ofHex("#5D6B6F");
    public static final ReportColor LINE = ReportColor.ofHex("#CDD8D6");
    public static final ReportColor SURFACE = ReportColor.ofHex("#F5F8F7");
    public static final ReportColor PASS = ReportColor.ofHex("#E5F3E9");
    public static final ReportColor FAIL = ReportColor.ofHex("#FAE8E9");
    public static final ReportColor UNKNOWN = ReportColor.ofHex("#F9F0D9");
    public static final ReportColor DISCLAIMER = ReportColor.ofHex("#68551C");
    public static final ReportColor WHITE = new ReportColor(1f, 1f, 1f);

    private ReportPalette() {}

    /** Port of {@code _result_background}: anything unrecognised falls back to the surface tint. */
    public static ReportColor resultBackground(String result) {
        return switch (result == null ? "" : result) {
            case "pass" -> PASS;
            case "fail" -> FAIL;
            case "unknown" -> UNKNOWN;
            default -> SURFACE;
        };
    }
}
