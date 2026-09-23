package com.trialsync.backend.report;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * One item in the report's story, the Java counterpart of a reportlab platypus flowable.
 *
 * <p>The frame loop in {@link ScreeningReportPdfRenderer} only needs three things from an item: how
 * tall it is at a given width, how to draw it, and whether it can be cut in half when the page runs
 * out. That is deliberately the same contract platypus uses, which is why the Python story
 * translates across without being restructured.
 *
 * <p>Coordinates follow PDF conventions: the origin is the bottom-left of the page and {@code top}
 * is the y of the item's upper edge.
 */
public interface Flowable {

    /** Vertical space above the item, suppressed when it is the first item on a page. */
    default float spaceBefore() {
        return 0f;
    }

    /** Vertical space below the item. */
    default float spaceAfter() {
        return 0f;
    }

    float height(ReportFonts fonts, float width);

    void draw(PDPageContentStream stream, ReportFonts fonts, float x, float top, float width)
            throws IOException;

    /**
     * Splits the item so its first part is no taller than {@code available}.
     *
     * @return exactly two flowables - the part that fits and the remainder - or an empty list when
     *     the item cannot usefully be split, in which case the caller moves it to the next page
     */
    default List<Flowable> split(ReportFonts fonts, float width, float available) {
        return List.of();
    }

    /** Port of {@code Spacer}: vertical whitespace, drawn as nothing. */
    record Spacer(float amount) implements Flowable {

        @Override
        public float height(ReportFonts fonts, float width) {
            return amount;
        }

        @Override
        public void draw(PDPageContentStream stream, ReportFonts fonts, float x, float top, float width) {
            // A spacer occupies height and paints nothing.
        }
    }
}
