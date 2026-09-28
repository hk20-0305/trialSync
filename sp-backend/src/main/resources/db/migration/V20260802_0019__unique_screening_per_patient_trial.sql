-- One screening per patient and trial version - batches included.
--
-- Business rule: the same patient and the same approved trial version have exactly one screening,
-- whichever entry point produced it. POST /api/v1/screenings, every cell of POST
-- /api/v1/screening-batches and the demo seed all reuse the stored screening, together with its
-- criterion evaluations, instead of writing a second row.
--
-- This migration replaces the partial rule from V20260802_0018, which only covered standalone
-- screenings (WHERE batch_id IS NULL) and left batch rows exempt. That exemption is gone: the key
-- below covers every screening of the pair, so a batch cannot add a second row either. The applied
-- migration is left untouched - it is superseded here rather than edited.
--
-- Duplicates written before the rule became strict are removed first, keeping the newest row of each
-- pair. criterion_evaluations and screening_chat_messages reference screenings ON DELETE CASCADE, so
-- a removed duplicate takes its own rows with it while the surviving screening keeps its evidence
-- untouched. The pre-migration probe on the shared database found no duplicated pair, so this DELETE
-- is a safety net rather than a data change today.
--
-- patient_id stays nullable (deleting a patient leaves the snapshot, and this row, without one) and
-- PostgreSQL treats NULLs as distinct, so those rows are outside the key and are skipped below.

-- 1. Remove duplicate screenings, keeping the newest row of each patient + trial version pair.
--    A tie on created_at is broken by id, so the surviving row is always the same one.
DELETE FROM screenings s
 USING screenings keep
 WHERE s.patient_id IS NOT NULL
   AND keep.patient_id = s.patient_id
   AND keep.trial_version_id = s.trial_version_id
   AND (keep.created_at > s.created_at
        OR (keep.created_at = s.created_at AND keep.id > s.id));

-- 2. Drop whichever spelling of the pair rule a database already carries, so that exactly one unique
--    key remains and it is the full one. The partial index created by V20260802_0018 is dropped by
--    name; a UNIQUE(patient_id, trial_version_id) added by hand is dropped whether it was declared
--    as a constraint - under the migration's name or PostgreSQL's default name - or as an index.
--    Constraints go first: an index owned by a constraint cannot be dropped on its own.
ALTER TABLE screenings
    DROP CONSTRAINT IF EXISTS ux_screenings_patient_trial_version;

ALTER TABLE screenings
    DROP CONSTRAINT IF EXISTS screenings_patient_id_trial_version_id_key;

DROP INDEX IF EXISTS ux_screenings_patient_trial_version;
DROP INDEX IF EXISTS screenings_patient_id_trial_version_id_key;

-- 3. Create the full unique key, under the name ScreeningService looks for when it decides whether a
--    unique violation is this rule (409 SCREENING_ALREADY_EXISTS) or an unrelated failure. No WHERE
--    clause: batch rows are part of the rule now.
CREATE UNIQUE INDEX IF NOT EXISTS ux_screenings_patient_trial_version
    ON screenings(patient_id, trial_version_id);
