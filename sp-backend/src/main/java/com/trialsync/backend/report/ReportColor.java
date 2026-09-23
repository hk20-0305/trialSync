package com.trialsync.backend.report;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * An RGB colour, expressed the way PDFBox wants it.
 *
 * <p>{@code java.awt.Color} is deliberately avoided: PDFBox 3 dropped its AWT colour overloads, and
 * the report has no reason to pull a graphics toolkit into a headless service.
 */
public record ReportColor(float red, float green, float blue) {

    /** Parses the {@code #RRGGBB} literals the reportlab template used, so the palette reads the same. */
    public static ReportColor ofHex(String hex) {
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        if (digits.length() != 6) {
            throw new IllegalArgumentException("Expected #RRGGBB, got " + hex);
        }
        int packed = Integer.parseInt(digits, 16);
        return new ReportColor(
                ((packed >> 16) & 0xFF) / 255f, ((packed >> 8) & 0xFF) / 255f, (packed & 0xFF) / 255f);
    }

    public void applyFill(PDPageContentStream stream) throws IOException {
        stream.setNonStrokingColor(red, green, blue);
    }

    public void applyStroke(PDPageContentStream stream) throws IOException {
        stream.setStrokingColor(red, green, blue);
    }
}
