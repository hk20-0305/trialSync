-- Java/Flyway baseline marker.
--
-- This database was originally managed by Python/Alembic through revision
-- 20260802_0012. The full schema was bulk-loaded into flyway_schema_history via:
--   V20260802_0012__initial_schema.sql  (all tables, enums, indexes, constraints)
--   V20260802_0013__seed_clinical_concepts.sql  (25 clinical concept seed rows)
--
-- Java V1-V12 were fine-grained ports of the same 12 Alembic revisions,
-- intended to be applied to a fresh database. Because every table, enum,
-- column, constraint, index, and seed row those files would create already
-- exists, they have been removed.
--
-- This no-op marker advances Flyway's recorded head to a Java-owned version
-- number (20260802.0014) without altering any existing database objects or data.
--
-- Flyway is configured with:
--   baseline-on-migrate: true
--   baseline-version:    20260802.0013
-- so it treats the two existing Alembic-era history rows as the baseline and
-- only applies this file and any future migrations.
--
-- The alembic_version table (version_num = '20260802_0012') is left untouched
-- to preserve the Python backend contract.
DO $$
BEGIN
    NULL;
END
$$;