-- One screening per patient and trial.
--
-- Business rule: selecting the same patient and the same trial again must not create a second
-- screening - POST /api/v1/screenings returns the existing row instead.
--
-- The pair spans two joins: a screening only references its patient through patient_snapshots and
-- its trial through trial_versions, and PostgreSQL cannot put a subquery in an index expression. The
-- patient is therefore denormalised onto the screening row, the same way patient_snapshots already
-- denormalised the patient identity onto its (patient_id, content_hash) unique key.
--
-- Scope: single screenings. Batch runs stay exempt - a batch re-screens the grid it was given and
-- must keep behaving exactly as before - so the unique index below is partial (WHERE batch_id IS
-- NULL). The service additionally treats a batch screening as an existing screening for that pair,
-- so the New Screening form never adds a second row to history either.
--
-- Duplicates written before this rule existed are removed, keeping the newest row of each pair.
-- criterion_evaluations and screening_chat_messages reference screenings ON DELETE CASCADE, so a
-- removed duplicate takes its own rows with it. The pre-migration probe found no duplicated
-- standalone pair, so this DELETE is a safety net rather than a data change today.

ALTER TABLE screenings
    ADD COLUMN IF NOT EXISTS patient_id UUID REFERENCES patients(id) ON DELETE SET NULL;

UPDATE screenings s
   SET patient_id = ps.patient_id
  FROM patient_snapshots ps
 WHERE ps.id = s.patient_snapshot_id
   AND s.patient_id IS NULL;

CREATE INDEX IF NOT EXISTS ix_screenings_patient_id ON screenings(patient_id);

DELETE FROM screenings s
 USING screenings keep
 WHERE s.batch_id IS NULL
   AND keep.batch_id IS NULL
   AND s.patient_id IS NOT NULL
   AND keep.patient_id = s.patient_id
   AND keep.trial_version_id = s.trial_version_id
   AND (keep.created_at > s.created_at
        OR (keep.created_at = s.created_at AND keep.id > s.id));

CREATE UNIQUE INDEX IF NOT EXISTS ux_screenings_patient_trial_version
    ON screenings(patient_id, trial_version_id)
    WHERE batch_id IS NULL;
