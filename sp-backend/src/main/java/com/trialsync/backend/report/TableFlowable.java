package com.trialsync.backend.report;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * A fixed-column table of paragraph cells, the counterpart of a reportlab {@code LongTable}.
 *
 * <p>The three tables the report draws - metadata, evidence and the criterion heading strip - differ
 * only in decoration, so one implementation carries all three through {@link Style}. Column widths
 * are absolute, as in the template, and every table is left-aligned in the frame ({@code hAlign
 * ="LEFT"}).
 *
 * <p>Rows split across pages one whole row at a time ({@code splitByRow=1}), and a table whose style
 * repeats its header ({@code repeatRows=1}) carries row zero onto the continuation. Only row zero is
 * ever repeated: a data row appears exactly once in the document, which is what makes the printed
 * evidence a faithful list of what the engine recorded.
 */
public final class TableFlowable implements Flowable {

    /**
     * The decoration of a table.
     *
     * @param headerBackground fill behind row zero, or null
     * @param firstColumnBackground fill behind column zero, or null
     * @param wholeBackground fill behind every cell, or null
     * @param grid draw a rule around every cell
     * @param box draw a rule around the table only
     * @param lineWidth rule width
     * @param lineColor rule colour
     * @param repeatHeader repeat row zero after a page break
     * @param middleValign centre cell text vertically instead of aligning it to the top
     */
    public record Style(
            ReportColor headerBackground,
            ReportColor firstColumnBackground,
            ReportColor wholeBackground,
            boolean grid,
            boolean box,
            float lineWidth,
            ReportColor lineColor,
            float leftPadding,
            float rightPadding,
            float topPadding,
            float bottomPadding,
            boolean repeatHeader,
            boolean middleValign) {}

    private final List<List<ParagraphFlowable>> rows;
    private final float[] columnWidths;
    private final Style style;

    public TableFlowable(List<List<ParagraphFlowable>> rows, float[] columnWidths, Style style) {
        this.rows = List.copyOf(rows);
        this.columnWidths = columnWidths.clone();
        this.style = style;
    }

    @Override
    public float height(ReportFonts fonts, float width) {
        float total = 0f;
        for (List<ParagraphFlowable> row : rows) {
            total += rowHeight(fonts, row);
        }
        return total;
    }

    @Override
    public void draw(PDPageContentStream stream, ReportFonts fonts, float x, float top, float width)
            throws IOException {
        float tableWidth = tableWidth();
        float totalHeight = height(fonts, width);

        // Backgrounds first, then rules, then text - the order reportlab paints table commands in.
        float cursorTop = top;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            float height = rowHeight(fonts, rows.get(rowIndex));
            ReportColor background = background(rowIndex);
            if (background != null) {
                background.applyFill(stream);
                stream.addRect(x, cursorTop - height, tableWidth, height);
                stream.fill();
            } else if (style.firstColumnBackground() != null) {
                style.firstColumnBackground().applyFill(stream);
                stream.addRect(x, cursorTop - height, columnWidths[0], height);
                stream.fill();
            }
            cursorTop -= height;
        }

        if (style.grid() || style.box()) {
            style.lineColor().applyStroke(stream);
            stream.setLineWidth(style.lineWidth());
            if (style.grid()) {
                cursorTop = top;
                for (List<ParagraphFlowable> row : rows) {
                    float height = rowHeight(fonts, row);
                    float columnX = x;
                    for (float columnWidth : columnWidths) {
                        stream.addRect(columnX, cursorTop - height, columnWidth, height);
                        columnX += columnWidth;
                    }
                    cursorTop -= height;
                }
            } else {
                stream.addRect(x, top - totalHeight, tableWidth, totalHeight);
            }
            stream.stroke();
        }

        cursorTop = top;
        for (List<ParagraphFlowable> row : rows) {
            float height = rowHeight(fonts, row);
            float columnX = x;
            for (int column = 0; column < columnWidths.length && column < row.size(); column++) {
                ParagraphFlowable cell = row.get(column);
                float cellWidth = columnWidths[column] - style.leftPadding() - style.rightPadding();
                float textTop =
                        style.middleValign()
                                ? cursorTop - (height - cell.height(fonts, cellWidth)) / 2f
                                : cursorTop - style.topPadding();
                cell.draw(stream, fonts, columnX + style.leftPadding(), textTop, cellWidth);
                columnX += columnWidths[column];
            }
            cursorTop -= height;
        }
    }

    /**
     * Splits after the last whole row that fits, carrying the header onto the continuation.
     *
     * <p>The header's height is charged to the continuation as well as to the first part, because a
     * split that leaves no room for the repeated header plus one data row is not a split worth
     * making - the caller is better off starting the table on a fresh page.
     */
    @Override
    public List<Flowable> split(ReportFonts fonts, float width, float available) {
        boolean hasHeader = style.repeatHeader() && rows.size() > 1;
        int firstDataRow = hasHeader ? 1 : 0;

        float used = hasHeader ? rowHeight(fonts, rows.get(0)) : 0f;
        int lastRow = firstDataRow;
        for (int index = firstDataRow; index < rows.size(); index++) {
            float height = rowHeight(fonts, rows.get(index));
            if (used + height > available) {
                break;
            }
            used += height;
            lastRow = index + 1;
        }
        if (lastRow <= firstDataRow || lastRow >= rows.size()) {
            return List.of();
        }

        List<List<ParagraphFlowable>> head = new ArrayList<>(rows.subList(0, lastRow));
        List<List<ParagraphFlowable>> tail = new ArrayList<>();
        if (hasHeader) {
            tail.add(rows.get(0));
        }
        tail.addAll(rows.subList(lastRow, rows.size()));
        return List.of(
                new TableFlowable(head, columnWidths, style),
                new TableFlowable(tail, columnWidths, style));
    }

    private ReportColor background(int rowIndex) {
        if (style.wholeBackground() != null) {
            return style.wholeBackground();
        }
        if (style.headerBackground() != null && rowIndex == 0) {
            return style.headerBackground();
        }
        return null;
    }

    private float rowHeight(ReportFonts fonts, List<ParagraphFlowable> row) {
        float tallest = 0f;
        for (int column = 0; column < columnWidths.length && column < row.size(); column++) {
            float cellWidth = columnWidths[column] - style.leftPadding() - style.rightPadding();
            tallest = Math.max(tallest, row.get(column).height(fonts, cellWidth));
        }
        return tallest + style.topPadding() + style.bottomPadding();
    }

    private float tableWidth() {
        float total = 0f;
        for (float columnWidth : columnWidths) {
            total += columnWidth;
        }
        return total;
    }
}
