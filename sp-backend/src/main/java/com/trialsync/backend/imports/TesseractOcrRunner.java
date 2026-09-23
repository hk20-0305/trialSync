package com.trialsync.backend.imports;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

/**
 * Port of {@code trialsync.imports.parser._extract_with_tesseract}.
 *
 * <p>Python shells out to two external binaries for raster OCR, and so does this: Poppler's
 * {@code pdftoppm} renders every page to a 200 dpi PNG, then {@code tesseract} recognises each PNG
 * and writes the text to standard output. There is no configuration switch on either side - the
 * caller invokes OCR whenever machine-extracted text is unusable - and no bundled fallback engine.
 *
 * <p>The four failure modes and their codes are carried over exactly:
 *
 * <ul>
 *   <li>a binary is not installed - Python's {@code FileNotFoundError}, Java's failure to start the
 *       process - becomes {@code OCR_UNAVAILABLE};
 *   <li>either binary exceeding its budget becomes {@code OCR_TIMEOUT};
 *   <li>a non-zero render exit status, or a page count that does not match the number of rendered
 *       images, becomes {@code OCR_RENDER_FAILED};
 *   <li>a non-zero recognition exit status becomes {@code OCR_FAILED}.
 * </ul>
 *
 * <p>Python captures both streams with {@code capture_output=True}; here each child's output is
 * redirected to a file in the scratch directory instead of a pipe, which is equivalent but cannot
 * deadlock if a page produces more text than a pipe buffer holds.
 */
@Component
public class TesseractOcrRunner {

    private static final int RENDER_DPI = 200;
    /** Exposed for the quality metadata that DocumentTextExtractor records after OCR. */
    public static final int RENDER_DPI_REPORTED = RENDER_DPI;
    private static final long RENDER_TIMEOUT_SECONDS = 20;
    private static final long RECOGNIZE_TIMEOUT_SECONDS = 15;

    /**
     * Renders and recognises every page, returning the raw recognised text per page in page order.
     *
     * @throws ImportParseException with one of the four OCR codes above
     */
    public List<String> extract(byte[] content, int pageCount) {
        Path directory = createScratchDirectory();
        try {
            Path source = directory.resolve("source.pdf");
            Path outputPrefix = directory.resolve("page");
            writeBytes(source, content);

            int renderStatus =
                    run(
                            List.of(
                                    "pdftoppm",
                                    "-f",
                                    "1",
                                    "-l",
                                    Integer.toString(pageCount),
                                    "-r",
                                    Integer.toString(RENDER_DPI),
                                    "-png",
                                    source.toString(),
                                    outputPrefix.toString()),
                            directory.resolve("render.out"),
                            RENDER_TIMEOUT_SECONDS);
            List<Path> images = renderedImages(directory);
            if (renderStatus != 0 || images.size() != pageCount) {
                throw new ImportParseException(
                        "OCR_RENDER_FAILED", "The PDF could not be prepared for local OCR.");
            }

            List<String> pages = new ArrayList<>(images.size());
            for (Path image : images) {
                Path recognized = directory.resolve(image.getFileName() + ".txt");
                int status =
                        run(
                                List.of(
                                        "tesseract",
                                        image.toString(),
                                        "stdout",
                                        "-l",
                                        "eng",
                                        "--psm",
                                        "6"),
                                recognized,
                                RECOGNIZE_TIMEOUT_SECONDS);
                if (status != 0) {
                    throw new ImportParseException(
                            "OCR_FAILED", "Tesseract could not read this PDF page.");
                }
                pages.add(readText(recognized));
            }
            return pages;
        } finally {
            deleteRecursively(directory);
        }
    }

    /**
     * Starts a child process, waits for it within the given budget, and returns its exit status.
     *
     * <p>The child's standard output goes to {@code outputFile} and its standard error is
     * discarded, matching Python's captured-and-unused stderr.
     */
    private int run(List<String> command, Path outputFile, long timeoutSeconds) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectOutput(ProcessBuilder.Redirect.to(outputFile.toFile()));
        builder.redirectError(ProcessBuilder.Redirect.to(new File(nullDevice())));
        Process process;
        try {
            process = builder.start();
        } catch (IOException exception) {
            // The binary is missing or not executable - Python raises FileNotFoundError here.
            throw new ImportParseException(
                    "OCR_UNAVAILABLE",
                    "OCR requires local Tesseract and Poppler installation. "
                            + "Use manual entry or install them.",
                    exception);
        }
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ImportParseException(
                        "OCR_TIMEOUT", "Local OCR timed out; use manual entry or a smaller PDF.");
            }
            return process.exitValue();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ImportParseException(
                    "OCR_TIMEOUT",
                    "Local OCR timed out; use manual entry or a smaller PDF.",
                    exception);
        }
    }

    private static String nullDevice() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? "NUL"
                : "/dev/null";
    }

    /** {@code sorted(directory.glob("page-*.png"))}: lexicographic order over the rendered pages. */
    private static List<Path> renderedImages(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(
                            path -> {
                                String name = path.getFileName().toString();
                                return name.startsWith("page-") && name.endsWith(".png");
                            })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path createScratchDirectory() {
        try {
            return Files.createTempDirectory("trialsync-ocr-");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void writeBytes(Path path, byte[] content) {
        try {
            Files.write(path, content);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** The equivalent of leaving Python's {@code TemporaryDirectory} context manager. */
    private static void deleteRecursively(Path directory) {
        try (Stream<Path> entries = Files.walk(directory)) {
            entries.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException ignored) {
                                    // A leftover scratch file must not mask the extraction result.
                                }
                            });
        } catch (IOException ignored) {
            // Same reasoning: cleanup is best effort.
        }
    }
}
