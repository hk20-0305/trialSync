package com.trialsync.backend.report;

/**
 * A paragraph style: the Java shape of a reportlab {@code ParagraphStyle}.
 *
 * <p>Only the attributes the template actually sets are modelled. {@code leading} is the baseline-to-
 * baseline distance and therefore the height of one line, which is what the frame layout measures.
 *
 * @param bold whether the run's default font is the bold face
 * @param fontSize point size
 * @param leading line height
 * @param color text colour
 * @param centered {@code TA_CENTER} when true, {@code TA_LEFT} otherwise
 * @param spaceBefore vertical space inserted above, unless the flowable starts a frame
 * @param spaceAfter vertical space inserted below
 */
public record ReportStyle(
        boolean bold,
        float fontSize,
        float leading,
        ReportColor color,
        boolean centered,
        float spaceBefore,
        float spaceAfter) {

    public ReportStyle(boolean bold, float fontSize, float leading, ReportColor color) {
        this(bold, fontSize, leading, color, false, 0f, 0f);
    }
}
