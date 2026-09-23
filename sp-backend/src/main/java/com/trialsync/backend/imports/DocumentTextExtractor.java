package com.trialsync.backend.imports;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Port of the extraction half of {@code trialsync.imports.parser}: pasted text and uploaded PDFs
 * become normalised source text, a page map and a quality report.
 *
 * <p>The page map is the backbone of the whole import feature. Every candidate the extractors
 * produce points at a page number and a pair of page-local offsets, those become
 * {@code document_spans} rows, and the reviewer's edits are validated against them. Offsets are
 * therefore counted in <em>code points</em>, matching Python's {@code str} indexing, so a document
 * containing an emoji or a CJK extension character yields the same numbers under both
 * implementations.
 *
 * <p>PDF text comes from Apache PDFBox rather than pypdf. The two libraries lay out extracted text
 * slightly differently - PDFBox is generally more faithful about column and word spacing - so the
 * exact characters a given scanned form yields can differ, while everything the API contract
 * depends on (page boundaries, offsets, checksums, limits, error codes, the OCR fallback trigger)
 * behaves identically.
 */
@Component
public class DocumentTextExtractor {

    /** Pasted text is capped by encoded size, not character count, as in Python. */
    public static final int MAX_TEXT_BYTES = 1_000_000;

    public static final int MAX_PDF_BYTES = 5_000_000;
    public static final int MAX_PDF_PAGES = 10;

    /** Below this many non-whitespace characters a PDF is treated as a scan and sent to OCR. */
    public static final int MIN_MACHINE_TEXT_CHARS = 40;

    /**
     * Recorded in {@code quality.extractor} for machine-extracted PDF text.
     *
     * <p>This is a frozen wire value, not a description of the library in use: the string is
     * asserted by the Python test suite, stored in {@code documents.quality_json} for every import
     * already in the database, and displayed by the review UI. Changing it would split the corpus
     * into "before" and "after" halves for no benefit, so the Java service keeps emitting it even
     * though the text now comes from PDFBox.
     */
    public static final String MACHINE_EXTRACTOR = "pypdf-6.14.2";

    public static final String OCR_EXTRACTOR = "tesseract-ocr";

    private final TesseractOcrRunner ocrRunner;

    public DocumentTextExtractor(TesseractOcrRunner ocrRunner) {
        this.ocrRunner = ocrRunner;
    }

    /** Port of {@code extract_text_input}. */
    public ExtractedInput extractTextInput(String text) {
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_TEXT_BYTES) {
            throw new ImportParseException("IMPORT_TOO_LARGE", "Pasted text exceeds the 1 MB limit.");
        }
        String normalized = PythonText.strip(PythonText.normalizeNewlines(text));
        if (normalized.isEmpty()) {
            throw new ImportParseException("IMPORT_EMPTY", "The pasted text is empty.");
        }
        return new ExtractedInput(
                normalized,
                List.of(new ExtractedPage(1, 0, PythonText.length(normalized), normalized)),
                quality(normalized, 1));
    }

    /**
     * Port of {@code extract_pdf_input}.
     *
     * <p>The guard order is significant and preserved: emptiness, then size, then the {@code %PDF-}
     * magic, then parsing. That is why a 5 MB blob whose first bytes are not a PDF header still
     * reports {@code IMPORT_TOO_LARGE} rather than {@code IMPORT_WRONG_TYPE}.
     */
    public ExtractedInput extractPdfInput(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ImportParseException("IMPORT_EMPTY", "The uploaded PDF is empty.");
        }
        if (content.length > MAX_PDF_BYTES) {
            throw new ImportParseException("IMPORT_TOO_LARGE", "The PDF exceeds the 5 MB limit.");
        }
        if (!startsWithPdfHeader(content)) {
            throw new ImportParseException(
                    "IMPORT_WRONG_TYPE", "The uploaded file is not a valid PDF.");
        }

        List<String> rawPages = readPages(content);
        if (rawPages.isEmpty()) {
            throw new ImportParseException("PDF_EMPTY", "The PDF does not contain any pages.");
        }
        if (rawPages.size() > MAX_PDF_PAGES) {
            throw new ImportParseException(
                    "PDF_TOO_MANY_PAGES", "PDF import is limited to " + MAX_PDF_PAGES + " pages.");
        }

        ExtractedInput extracted = compose(rawPages, MACHINE_EXTRACTOR);
        if (hasUsableText(extracted)) {
            return extracted;
        }

        // Nothing readable came out of the content stream, so the file is almost certainly a scan.
        // Python has no configuration switch here and neither does this: OCR is simply the second
        // attempt, and its failures surface as their own error codes.
        List<String> ocrPages = ocrRunner.extract(content, rawPages.size());
        ExtractedInput recognized = compose(ocrPages, OCR_EXTRACTOR);
        if (!hasUsableText(recognized)) {
            throw new ImportParseException(
                    "OCR_NO_TEXT",
                    "OCR could not recover enough readable text from this PDF. "
                            + "Use manual entry or a clearer scan.");
        }
        Map<String, Object> ocr = new LinkedHashMap<>();
        ocr.put("used", true);
        ocr.put("engine", "tesseract");
        ocr.put("language", "eng");
        ocr.put("render_dpi", TesseractOcrRunner.RENDER_DPI_REPORTED);
        recognized.quality().put("ocr", ocr);
        return recognized;
    }

    private static boolean startsWithPdfHeader(byte[] content) {
        byte[] header = {'%', 'P', 'D', 'F', '-'};
        if (content.length < header.length) {
            return false;
        }
        for (int index = 0; index < header.length; index++) {
            if (content[index] != header[index]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads every page's text, mapping the failure modes onto Python's codes.
     *
     * <p>An encrypted file is rejected before any text is read. PDFBox reports the two shapes of
     * encryption differently: a user password it cannot satisfy raises
     * {@link InvalidPasswordException} during load, while a file protected only by an owner password
     * loads and reports {@code isEncrypted()}. pypdf's {@code is_encrypted} covers both, so both are
     * checked.
     */
    private static List<String> readPages(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new ImportParseException(
                        "PDF_ENCRYPTED", "Encrypted PDFs are not supported.");
            }
            int pageCount = document.getNumberOfPages();
            List<String> pages = new ArrayList<>(pageCount);
            PDFTextStripper stripper = new PDFTextStripper();
            // Defaults are platform dependent; pinning them keeps extraction identical on every
            // host and avoids CRLF creeping into the stored source text.
            stripper.setLineSeparator("\n");
            stripper.setParagraphEnd("\n");
            stripper.setPageEnd("\n");
            stripper.setWordSeparator(" ");
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document));
            }
            return pages;
        } catch (InvalidPasswordException exception) {
            throw new ImportParseException(
                    "PDF_ENCRYPTED", "Encrypted PDFs are not supported.", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ImportParseException parseException) {
                throw parseException;
            }
            throw new ImportParseException(
                    "PDF_MALFORMED", "The PDF is malformed and could not be read.", exception);
        }
    }

    /**
     * Port of {@code _compose_pdf_input}: concatenates the stripped page texts with a blank line
     * between them and records where each page landed.
     */
    private static ExtractedInput compose(List<String> rawPages, String extractor) {
        StringBuilder text = new StringBuilder();
        List<ExtractedPage> pages = new ArrayList<>(rawPages.size());
        int offset = 0;
        boolean first = true;
        int number = 0;
        for (String raw : rawPages) {
            number++;
            String pageText = PythonText.strip(PythonText.normalizeNewlines(raw));
            if (!first) {
                text.append("\n\n");
                offset += 2;
            }
            first = false;
            int start = offset;
            text.append(pageText);
            offset += PythonText.length(pageText);
            pages.add(new ExtractedPage(number, start, offset, pageText));
        }
        String composed = text.toString();
        Map<String, Object> quality = quality(composed, pages.size());
        // Overwrites the placeholder written by `quality`, keeping the key in its original position.
        quality.put("extractor", extractor);
        return new ExtractedInput(composed, pages, quality);
    }

    /**
     * Port of {@code _has_usable_text}: enough non-whitespace characters overall, and enough of them
     * per page. Both have to hold, so a ten-page scan with one readable cover page still goes to
     * OCR.
     */
    private static boolean hasUsableText(ExtractedInput extracted) {
        String dense = PythonText.removeWhitespace(extracted.text());
        double perPage = ((Number) extracted.quality().get("characters_per_page")).doubleValue();
        return PythonText.length(dense) >= MIN_MACHINE_TEXT_CHARS && perPage >= 20;
    }

    /**
     * Port of {@code _quality}.
     *
     * <p>{@code printable_ratio} counts the code points Python's {@code str.isprintable()} accepts,
     * plus newline and tab, which it does not. The {@code max(..., 1)} denominators keep an empty
     * document from dividing by zero, exactly as in Python, which is why an empty extraction reports
     * a ratio of {@code 0.0} rather than failing.
     */
    private static Map<String, Object> quality(String text, int pageCount) {
        int length = PythonText.length(text);
        int printable =
                (int)
                        text.codePoints()
                                .filter(
                                        codePoint ->
                                                PythonText.isPrintable(codePoint)
                                                        || codePoint == '\n'
                                                        || codePoint == '\t')
                                .count();
        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("page_count", pageCount);
        quality.put("character_count", length);
        quality.put(
                "characters_per_page", PythonText.round((double) length / Math.max(pageCount, 1), 2));
        quality.put("printable_ratio", PythonText.round((double) printable / Math.max(length, 1), 4));
        quality.put("extractor", "deterministic-text-1");
        return quality;
    }
}
