package com.trialsync.backend.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest {

    private static String jdbcUrl = "jdbc:postgresql://localhost:5432/trialsync_java";
    private static String username = "postgres";
    private static String password = "";
    private static boolean postgresAvailable = false;

    @BeforeAll
    static void setUp() {
        File envFile = new File(".env");
        if (!envFile.exists()) {
            envFile = new File("sp-backend/.env");
        }
        if (envFile.exists()) {
            try (FileInputStream fis = new FileInputStream(envFile)) {
                Properties props = new Properties();
                props.load(fis);
                if (props.getProperty("DATABASE_URL") != null) {
                    jdbcUrl = props.getProperty("DATABASE_URL");
                }
                if (props.getProperty("DATABASE_USERNAME") != null) {
                    username = props.getProperty("DATABASE_USERNAME");
                }
                if (props.getProperty("DATABASE_PASSWORD") != null) {
                    password = props.getProperty("DATABASE_PASSWORD");
                }
            } catch (Exception ignored) {
            }
        }

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            postgresAvailable = true;
        } catch (Exception ex) {
            postgresAvailable = false;
        }
    }

    @Test
    void testFlywayBaselineMigration() throws Exception {
        if (!postgresAvailable) {
            System.out.println("PostgreSQL not available at " + jdbcUrl + " - skipping FlywayMigrationTest");
            return;
        }

        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("20260802.0013")
                .validateOnMigrate(false)
                .load();

        MigrateResult result = flyway.migrate();
        System.out.println("Flyway migration completed. Success: " + result.success);
        assertTrue(result.success, "Flyway migrations should succeed");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            DatabaseMetaData meta = conn.getMetaData();

            // 1. Verify tables
            Set<String> tables = new HashSet<>();
            try (ResultSet rs = meta.getTables(null, "public", "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }

            Set<String> expectedTables = Set.of(
                    "users",
                    "patients",
                    "patient_facts",
                    "trials",
                    "trial_versions",
                    "criteria",
                    "patient_snapshots",
                    "screenings",
                    "screening_batches",
                    "criterion_evaluations",
                    "screening_chat_messages",
                    "documents",
                    "document_spans",
                    "patient_unsupported_details",
                    "clinical_concepts",
                    "patient_change_events",
                    "flyway_schema_history",
                    "eligibility_rag_indexes",
                    "eligibility_rag_runs",
                    "eligibility_rag_results");

            for (String expected : expectedTables) {
                assertTrue(tables.contains(expected), "Missing expected table: " + expected);
            }

            Set<String> removedLegacyTables = Set.of(
                    "alembic_version",
                    "research_participants",
                    "research_enrollments",
                    "research_dose_events",
                    "research_visit_events",
                    "research_measurements",
                    "research_adverse_events",
                    "research_outcomes");

            for (String removed : removedLegacyTables) {
                assertFalse(tables.contains(removed), "Legacy table should have been dropped: " + removed);
            }

            // 2. Verify PostgreSQL enums exist
            Set<String> enums = new HashSet<>();
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT t.typname FROM pg_type t JOIN pg_namespace n ON 1=1 WHERE t.typtype = 'e' AND n.nspname = 'public'")) {
                while (rs.next()) {
                    enums.add(rs.getString("typname"));
                }
            }

            Set<String> expectedEnums = Set.of(
                    "fact_type",
                    "fact_assertion",
                    "version_status",
                    "criterion_kind",
                    "overall_state",
                    "evaluation_result",
                    "document_kind",
                    "document_source_type",
                    "document_status");

            for (String expected : expectedEnums) {
                assertTrue(enums.contains(expected), "Missing expected PostgreSQL enum: " + expected);
            }

            // 3. Verify latest flyway version is the research RAG migration
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1")) {
                assertTrue(rs.next(), "flyway_schema_history must have at least one record");
                assertEquals("20260802.0017", rs.getString("version"),
                        "Latest schema version must be 20260802.0017 (Cleanup Legacy Research and Alembic)");
            }
        }
    }
}
