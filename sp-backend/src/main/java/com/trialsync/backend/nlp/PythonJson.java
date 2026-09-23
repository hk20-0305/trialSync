package com.trialsync.backend.nlp;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.util.MinimalPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Serialises prompt fragments the way {@code json.dumps(...)} does.
 *
 * <p>The authoritative context and the untrusted history are embedded in the Groq prompt as JSON
 * text. CPython's default dump puts a space after {@code :} and {@code ,} and escapes every
 * non-ASCII character, and Jackson does neither, so a plain {@code writeValueAsString} would send a
 * different prompt than the Python service sent for the same screening. This writer restores both
 * conventions so the model sees byte-identical input.
 *
 * <p>Only outbound prompt text uses this writer. API responses keep the application-wide Jackson
 * configuration.
 */
public final class PythonJson {

    private static final JsonFactory FACTORY =
            JsonFactory.builder().enable(JsonWriteFeature.ESCAPE_NON_ASCII).build();

    private static final ObjectWriter WRITER =
            new ObjectMapper(FACTORY).writer(new PythonSeparators());

    private PythonJson() {}

    /** {@code json.dumps(value)} with CPython's default separators and ASCII escaping. */
    public static String dumps(Object value) {
        try {
            return WRITER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Prompt context could not be serialised", exception);
        }
    }

    /** Reproduces the {@code (", ", ": ")} separator pair CPython uses when no indent is given. */
    private static final class PythonSeparators extends MinimalPrettyPrinter {

        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator generator) throws IOException {
            generator.writeRaw(": ");
        }

        @Override
        public void writeObjectEntrySeparator(JsonGenerator generator) throws IOException {
            generator.writeRaw(", ");
        }

        @Override
        public void writeArrayValueSeparator(JsonGenerator generator) throws IOException {
            generator.writeRaw(", ");
        }
    }
}
