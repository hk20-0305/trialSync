package com.trialsync.backend.imports;

/**
 * Port of {@code trialsync.imports.parser.ImportParseError}.
 *
 * <p>Carries the stable code the API surfaces verbatim - {@code IMPORT_TOO_LARGE},
 * {@code PDF_ENCRYPTED}, {@code OCR_NO_TEXT} and the rest - which the import endpoint turns into a
 * 422 {@code ApplicationError} with the same code and message.
 */
public class ImportParseException extends RuntimeException {

    private final String code;

    public ImportParseException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ImportParseException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
