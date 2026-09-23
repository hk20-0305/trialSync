package com.trialsync.backend.report;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Resolves the regular/bold pair the report is typeset in. Port of {@code _font_names}.
 *
 * <p>The candidate list, and its order, is copied from the Python module: Liberation Sans from the
 * three paths different distributions use, then DejaVu Sans, then the built-in Helvetica. That order
 * matters for more than aesthetics - the first four options are embedded TrueType faces with full
 * Unicode coverage, while Helvetica is a standard-14 font limited to WinAnsi, which cannot render
 * characters that appear routinely in eligibility rules such as {@code >=} written as U+2265.
 *
 * <p>Fonts are resolved per document because a {@link PDType0Font} is bound to the document that
 * embeds it and cannot be shared between renders.
 */
public final class ReportFonts {

    private static final List<String[]> CANDIDATES =
            List.of(
                    new String[] {
                        "/usr/share/fonts/liberation/LiberationSans-Regular.ttf",
                        "/usr/share/fonts/liberation/LiberationSans-Bold.ttf"
                    },
                    new String[] {
                        "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
                        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf"
                    },
                    new String[] {
                        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
                        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"
                    },
                    new String[] {
                        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
                    });

    private final PDFont regular;
    private final PDFont bold;
    private final boolean embedded;

    private ReportFonts(PDFont regular, PDFont bold, boolean embedded) {
        this.regular = regular;
        this.bold = bold;
        this.embedded = embedded;
    }

    /**
     * Loads the first candidate pair that exists on disk, falling back to Helvetica.
     *
     * <p>A font file that exists but cannot be parsed is treated as absent rather than fatal: a
     * broken system font should degrade the typeface, not fail the download.
     */
    public static ReportFonts load(PDDocument document) {
        for (String[] candidate : CANDIDATES) {
            File regularFile = new File(candidate[0]);
            File boldFile = new File(candidate[1]);
            if (!regularFile.isFile() || !boldFile.isFile()) {
                continue;
            }
            try {
                PDFont loadedRegular = PDType0Font.load(document, regularFile);
                PDFont loadedBold = PDType0Font.load(document, boldFile);
                return new ReportFonts(loadedRegular, loadedBold, true);
            } catch (IOException | RuntimeException exception) {
                // Unreadable font file: try the next candidate.
            }
        }
        return new ReportFonts(
                new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD),
                false);
    }

    public PDFont regular() {
        return regular;
    }

    public PDFont bold() {
        return bold;
    }

    public PDFont font(boolean useBold) {
        return useBold ? bold : regular;
    }

    /** True when an embedded TrueType face was found, i.e. when full Unicode text is renderable. */
    public boolean isEmbedded() {
        return embedded;
    }

    /** Width of {@code text} in points at {@code fontSize}. */
    public float width(boolean useBold, String text, float fontSize) {
        if (text.isEmpty()) {
            return 0f;
        }
        try {
            return font(useBold).getStringWidth(text) / 1000f * fontSize;
        } catch (IOException | IllegalArgumentException exception) {
            // Only reachable for text that skipped sanitisation; approximate rather than fail.
            return text.length() * fontSize * 0.5f;
        }
    }

    /**
     * Replaces characters neither face can encode, so an exotic glyph in synthetic data cannot break
     * the download.
     *
     * <p>With an embedded face this is a no-op for anything the font covers. With the Helvetica
     * fallback it substitutes {@code ?} for non-WinAnsi characters, which is the counterpart of
     * reportlab drawing a missing-glyph box: the layout survives and the rest of the text is intact.
     * Both faces must accept the character, because the same string may be drawn bold in one place
     * and regular in another.
     */
    public String sanitize(String text) {
        if (text.isEmpty() || isEncodable(text)) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            String single = text.substring(index, index + charCount);
            out.append(isEncodable(single) ? single : "?");
            index += charCount;
        }
        return out.toString();
    }

    private boolean isEncodable(String text) {
        try {
            regular.getStringWidth(text);
            bold.getStringWidth(text);
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }
}
