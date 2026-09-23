package com.trialsync.backend.domain.model;

import java.time.LocalDate;

/**
 * Everything time- and version-dependent that the engine is allowed to know.
 *
 * <p>Port of {@code trialsync.domain.types.ScreeningContext}. The screening date is passed in rather
 * than read from a clock, which is what keeps {@code screen(...)} reproducible.
 */
public record ScreeningContext(
        LocalDate screeningDate, String engineVersion, String terminologyVersion, String unitVersion) {

    public static final String DEFAULT_ENGINE_VERSION = "0.1.0";
    public static final String DEFAULT_TERMINOLOGY_VERSION = "local-1";
    public static final String DEFAULT_UNIT_VERSION = "units-1";

    public ScreeningContext {
        engineVersion = engineVersion == null ? DEFAULT_ENGINE_VERSION : engineVersion;
        terminologyVersion = terminologyVersion == null ? DEFAULT_TERMINOLOGY_VERSION : terminologyVersion;
        unitVersion = unitVersion == null ? DEFAULT_UNIT_VERSION : unitVersion;
    }

    public ScreeningContext(LocalDate screeningDate) {
        this(screeningDate, DEFAULT_ENGINE_VERSION, DEFAULT_TERMINOLOGY_VERSION, DEFAULT_UNIT_VERSION);
    }
}
