-- V20260802_0017__cleanup_legacy_research_and_alembic.sql
-- Removes legacy research/ML tables and the old Python Alembic migration tracker.
-- The final architecture removed the Python ML microservice, XGBoost, dropout prediction,
-- and longitudinal research tracking.
-- All 7 research tables and alembic_version are obsolete.

DROP TABLE IF EXISTS research_measurements CASCADE;
DROP TABLE IF EXISTS research_visit_events CASCADE;
DROP TABLE IF EXISTS research_dose_events CASCADE;
DROP TABLE IF EXISTS research_adverse_events CASCADE;
DROP TABLE IF EXISTS research_outcomes CASCADE;
DROP TABLE IF EXISTS research_enrollments CASCADE;
DROP TABLE IF EXISTS research_participants CASCADE;
DROP TABLE IF EXISTS alembic_version CASCADE;
