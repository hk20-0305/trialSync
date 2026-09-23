package com.trialsync.backend.report;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * A wrapped block of text, the counterpart of a reportlab {@code Paragraph}.
 *
 * <p>Text is held as a sequence of runs so the two lines the template writes with {@code <b>} markup
 * - "Evaluation ID / Criterion ID" and "Reason code / Truth" - keep their bold labels without a
 * markup parser. Runs wrap as one stream, so a bold label and the value after it break across lines
 * exactly where the text does.
 *
 * <p>Whitespace is collapsed to single spaces and newlines become hard breaks, which is how
 * reportlab treats paragraph text: its input is XML, where runs of whitespace collapse and the
 * template's {@code <br/>} substitution introduces the breaks.
 */
public final class ParagraphFlowable implements Flowable {

    /** A stretch of text in one face. */
    public record Run(String text, boolean bold) {

        public static Run plain(String text) {
            return new Run(text, false);
        }

        public static Run strong(String text) {
            return new Run(text, true);
        }
    }

    private enum Kind {
        WORD,
        SPACE,
        BREAK
    }

    private record Token(String text, boolean bold, Kind kind) {}

    private record Line(List<Token> tokens, float width) {}

    private final List<Run> runs;
    private final ReportStyle style;
    private final float spaceBefore;
    private final float spaceAfter;
    private final List<Line> fixedLines;

    private float cachedWidth = -1f;
    private List<Line> cachedLines;

    public ParagraphFlowable(List<Run> runs, ReportStyle style) {
        this(runs, style, style.spaceBefore(), style.spaceAfter(), null);
    }

    private ParagraphFlowable(
            List<Run> runs,
            ReportStyle style,
            float spaceBefore,
            float spaceAfter,
            List<Line> fixedLines) {
        this.runs = List.copyOf(runs);
        this.style = style;
        this.spaceBefore = spaceBefore;
        this.spaceAfter = spaceAfter;
        this.fixedLines = fixedLines;
    }

    public static ParagraphFlowable of(String text, ReportStyle style) {
        return new ParagraphFlowable(List.of(Run.plain(text)), style);
    }

    @Override
    public float spaceBefore() {
        return spaceBefore;
    }

    @Override
    public float spaceAfter() {
        return spaceAfter;
    }

    @Override
    public float height(ReportFonts fonts, float width) {
        return Math.max(1, layout(fonts, width).size()) * style.leading();
    }

    @Override
    public void draw(PDPageContentStream stream, ReportFonts fonts, float x, float top, float width)
            throws IOException {
        List<Line> lines = layout(fonts, width);
        float baseline = top - style.fontSize();
        for (Line line : lines) {
            float cursor = style.centered() ? x + (width - line.width()) / 2f : x;
            for (Token token : line.tokens()) {
                String text = token.kind() == Kind.SPACE ? " " : token.text();
                if (!text.isEmpty()) {
                    stream.beginText();
                    stream.setFont(fonts.font(token.bold()), style.fontSize());
                    style.color().applyFill(stream);
                    stream.newLineAtOffset(cursor, baseline);
                    stream.showText(text);
                    stream.endText();
                    cursor += fonts.width(token.bold(), text, style.fontSize());
                }
            }
            baseline -= style.leading();
        }
    }

    /**
     * Splits between whole lines, which is what platypus does when a paragraph straddles a page
     * break. Nothing is split off unless at least one line fits and at least one line remains.
     */
    @Override
    public List<Flowable> split(ReportFonts fonts, float width, float available) {
        List<Line> lines = layout(fonts, width);
        int fitting = (int) Math.floor(available / style.leading());
        if (fitting < 1 || fitting >= lines.size()) {
            return List.of();
        }
        ParagraphFlowable head =
                new ParagraphFlowable(
                        runs, style, spaceBefore, 0f, List.copyOf(lines.subList(0, fitting)));
        ParagraphFlowable tail =
                new ParagraphFlowable(
                        runs,
                        style,
                        0f,
                        spaceAfter,
                        List.copyOf(lines.subList(fitting, lines.size())));
        return List.of(head, tail);
    }

    private List<Line> layout(ReportFonts fonts, float width) {
        if (fixedLines != null) {
            return fixedLines;
        }
        if (cachedLines != null && cachedWidth == width) {
            return cachedLines;
        }
        cachedLines = wrap(fonts, width);
        cachedWidth = width;
        return cachedLines;
    }

    private List<Line> wrap(ReportFonts fonts, float width) {
        List<Line> lines = new ArrayList<>();
        List<Token> current = new ArrayList<>();
        float currentWidth = 0f;
        Token pendingSpace = null;

        for (Token token : tokenize()) {
            if (token.kind() == Kind.BREAK) {
                lines.add(new Line(List.copyOf(current), currentWidth));
                current.clear();
                currentWidth = 0f;
                pendingSpace = null;
                continue;
            }
            if (token.kind() == Kind.SPACE) {
                if (!current.isEmpty()) {
                    pendingSpace = token;
                }
                continue;
            }
            for (Token chunk : fit(fonts, token, width)) {
                float chunkWidth = fonts.width(chunk.bold(), chunk.text(), style.fontSize());
                float spaceWidth =
                        pendingSpace == null ? 0f : fonts.width(pendingSpace.bold(), " ", style.fontSize());
                if (!current.isEmpty() && currentWidth + spaceWidth + chunkWidth > width) {
                    lines.add(new Line(List.copyOf(current), currentWidth));
                    current.clear();
                    currentWidth = 0f;
                    pendingSpace = null;
                    spaceWidth = 0f;
                }
                if (pendingSpace != null) {
                    current.add(pendingSpace);
                    currentWidth += spaceWidth;
                    pendingSpace = null;
                }
                current.add(chunk);
                currentWidth += chunkWidth;
            }
        }
        if (!current.isEmpty() || lines.isEmpty()) {
            lines.add(new Line(List.copyOf(current), currentWidth));
        }
        return List.copyOf(lines);
    }

    /**
     * Breaks a word that is wider than the column into pieces that fit.
     *
     * <p>Content hashes, identifiers and source labels have no spaces to break at; without this a
     * single long token would run past the cell border instead of wrapping inside it.
     */
    private List<Token> fit(ReportFonts fonts, Token token, float width) {
        if (fonts.width(token.bold(), token.text(), style.fontSize()) <= width || width <= 0f) {
            return List.of(token);
        }
        List<Token> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (int index = 0; index < token.text().length(); ) {
            int codePoint = token.text().codePointAt(index);
            int charCount = Character.charCount(codePoint);
            String next = token.text().substring(index, index + charCount);
            if (chunk.length() > 0
                    && fonts.width(token.bold(), chunk.toString() + next, style.fontSize()) > width) {
                chunks.add(new Token(chunk.toString(), token.bold(), Kind.WORD));
                chunk.setLength(0);
            }
            chunk.append(next);
            index += charCount;
        }
        if (chunk.length() > 0) {
            chunks.add(new Token(chunk.toString(), token.bold(), Kind.WORD));
        }
        return chunks;
    }

    private List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        for (Run run : runs) {
            String text = run.text();
            int index = 0;
            while (index < text.length()) {
                char character = text.charAt(index);
                if (character == '\n' || character == '\r') {
                    tokens.add(new Token("", run.bold(), Kind.BREAK));
                    // Treat CRLF as one break.
                    if (character == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                        index++;
                    }
                    index++;
                } else if (Character.isWhitespace(character)) {
                    while (index < text.length()
                            && Character.isWhitespace(text.charAt(index))
                            && text.charAt(index) != '\n'
                            && text.charAt(index) != '\r') {
                        index++;
                    }
                    tokens.add(new Token(" ", run.bold(), Kind.SPACE));
                } else {
                    int start = index;
                    while (index < text.length() && !Character.isWhitespace(text.charAt(index))) {
                        index++;
                    }
                    tokens.add(new Token(text.substring(start, index), run.bold(), Kind.WORD));
                }
            }
        }
        return tokens;
    }
}
