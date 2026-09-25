package com.trialsync.backend.controller;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.dto.common.HealthResponse;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of {@code trialsync.api.health}.
 *
 * <p>Liveness answers as long as the process is up. Readiness additionally proves the database is
 * reachable <em>and</em> at the schema revision this build expects, which is what stops a
 * half-migrated deployment from being handed traffic and writing rows the code and the schema
 * disagree about.
 *
 * <p><strong>Migration-tool substitution.</strong> The Python service used Alembic and pinned
 * {@code EXPECTED_SCHEMA_REVISION = "20260802_0012"}, read from the {@code alembic_version} table.
 * This build uses Flyway, which keeps its history in {@code flyway_schema_history}. The existing
 * database was originally managed by Python/Alembic and loaded into Flyway history as two bulk
 * scripts ({@code V20260802_0012} and {@code V20260802_0013}). Flyway V1–V12 (fine-grained ports
 * of the Alembic revisions) were removed because the schema they would create already exists in
 * full. A single no-op marker migration {@code V20260802_0014__java_baseline_marker.sql} advances
 * Flyway's recorded head to a Java-owned version number.
 *
 * <p>Flyway is configured with {@code baseline-on-migrate=true} and
 * {@code baseline-version=20260802.0013} so the two Alembic-era history rows are treated as the
 * baseline and only the marker (and any future migrations) are applied.
 *
 * <p>{@link #EXPECTED_SCHEMA_VERSION} is pinned deliberately rather than derived from the migration
 * files at runtime: a constant is what makes the check meaningful, since a value read from whatever
 * happens to be on the classpath would agree with itself and always pass.
 */
@RestController
@RequestMapping({"/health", "/api/v1/health"})
public class HealthController {

    /** Version of the newest Flyway migration this build requires. See the class comment. */
    static final String EXPECTED_SCHEMA_VERSION = "20260802.0017";

    private static final String LATEST_APPLIED_VERSION =
            """
            select version
              from flyway_schema_history
             where success = true
               and version is not null
             order by installed_rank desc
             limit 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/live")
    public HealthResponse live() {
        return new HealthResponse("ok");
    }

    @GetMapping("/ready")
    public HealthResponse ready() {
        String applied;
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            List<String> head = jdbcTemplate.queryForList(LATEST_APPLIED_VERSION, String.class);
            applied = head.isEmpty() ? null : head.get(0);
        } catch (DataAccessException exception) {
            // Covers both an unreachable database and a database that has never been migrated, where
            // the history table itself is absent - the same two cases the Python probe folded into
            // one SQLAlchemyError.
            throw new ApplicationError(
                    "SERVICE_NOT_READY", "The database is unavailable or has not been migrated.", 503);
        }

        if (!EXPECTED_SCHEMA_VERSION.equals(applied)) {
            throw new ApplicationError(
                    "DATABASE_MIGRATION_REQUIRED",
                    "The database schema is not at the required revision.",
                    503);
        }
        return new HealthResponse("ready");
    }
}
