-- V20260802_0015__research_database_foundation.sql
-- Phase R3: Database foundation for longitudinal research modules.
-- Creates 7 tables: research_participants, research_enrollments, research_dose_events,
-- research_visit_events, research_measurements, research_adverse_events, and research_outcomes.

-- 1. research_participants
CREATE TABLE research_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    patient_id UUID REFERENCES patients(id) ON DELETE SET NULL,
    participant_code VARCHAR(64) NOT NULL,
    site_id VARCHAR(64),
    birth_year INTEGER,
    sex VARCHAR(32),
    demographics JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_research_participants_owner_code UNIQUE (owner_id, participant_code)
);

CREATE INDEX ix_research_participants_owner_id ON research_participants(owner_id);
CREATE INDEX ix_research_participants_patient_id ON research_participants(patient_id);
CREATE INDEX ix_research_participants_code ON research_participants(participant_code);

-- 2. research_enrollments
CREATE TABLE research_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES research_participants(id) ON DELETE CASCADE,
    patient_snapshot_id UUID REFERENCES patient_snapshots(id) ON DELETE SET NULL,
    trial_id UUID REFERENCES trials(id) ON DELETE SET NULL,
    trial_version_id UUID REFERENCES trial_versions(id) ON DELETE SET NULL,
    screening_id UUID REFERENCES screenings(id) ON DELETE SET NULL,
    enrollment_code VARCHAR(64) NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    arm VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_research_enrollments_owner_code UNIQUE (owner_id, enrollment_code)
);

CREATE INDEX ix_research_enrollments_owner_id ON research_enrollments(owner_id);
CREATE INDEX ix_research_enrollments_participant_id ON research_enrollments(participant_id);
CREATE INDEX ix_research_enrollments_snapshot_id ON research_enrollments(patient_snapshot_id);
CREATE INDEX ix_research_enrollments_trial_version_id ON research_enrollments(trial_version_id);
CREATE INDEX ix_research_enrollments_screening_id ON research_enrollments(screening_id);

-- 3. research_dose_events
CREATE TABLE research_dose_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES research_enrollments(id) ON DELETE CASCADE,
    dose_number INTEGER,
    scheduled_day INTEGER NOT NULL,
    scheduled_at TIMESTAMPTZ,
    administered_at TIMESTAMPTZ,
    prescribed_dose_amount NUMERIC(10, 2),
    actual_dose_amount NUMERIC(10, 2),
    unit VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'ADMINISTERED',
    adherence_ratio NUMERIC(5, 4),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_research_dose_events_enrollment_id ON research_dose_events(enrollment_id);
CREATE INDEX ix_research_dose_events_day ON research_dose_events(enrollment_id, scheduled_day);

-- 4. research_visit_events
CREATE TABLE research_visit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES research_enrollments(id) ON DELETE CASCADE,
    visit_number INTEGER,
    visit_name VARCHAR(64),
    scheduled_day INTEGER NOT NULL,
    actual_day INTEGER,
    scheduled_at TIMESTAMPTZ,
    attended_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL DEFAULT 'ATTENDED',
    visit_type VARCHAR(32),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_research_visit_events_enrollment_id ON research_visit_events(enrollment_id);
CREATE INDEX ix_research_visit_events_day ON research_visit_events(enrollment_id, scheduled_day);

-- 5. research_measurements
CREATE TABLE research_measurements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES research_enrollments(id) ON DELETE CASCADE,
    visit_event_id UUID REFERENCES research_visit_events(id) ON DELETE SET NULL,
    measurement_day INTEGER NOT NULL,
    measured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120),
    numeric_value NUMERIC(14, 4),
    text_value VARCHAR(255),
    unit VARCHAR(32),
    reference_range_low NUMERIC(14, 4),
    reference_range_high NUMERIC(14, 4),
    is_abnormal BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_research_measurements_enrollment_id ON research_measurements(enrollment_id);
CREATE INDEX ix_research_measurements_code ON research_measurements(code);
CREATE INDEX ix_research_measurements_day ON research_measurements(enrollment_id, measurement_day);
CREATE INDEX ix_research_measurements_measured_at ON research_measurements(measured_at);

-- 6. research_adverse_events
CREATE TABLE research_adverse_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES research_enrollments(id) ON DELETE CASCADE,
    event_day INTEGER NOT NULL,
    onset_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    term VARCHAR(200) NOT NULL,
    ctcae_grade INTEGER NOT NULL DEFAULT 1,
    is_serious BOOLEAN NOT NULL DEFAULT FALSE,
    relatedness VARCHAR(32) NOT NULL DEFAULT 'NOT_RELATED',
    action_taken VARCHAR(64),
    outcome VARCHAR(64),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_research_adverse_events_enrollment_id ON research_adverse_events(enrollment_id);
CREATE INDEX ix_research_adverse_events_day ON research_adverse_events(enrollment_id, event_day);
CREATE INDEX ix_research_adverse_events_severity ON research_adverse_events(enrollment_id, ctcae_grade);

-- 7. research_outcomes
CREATE TABLE research_outcomes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES research_enrollments(id) ON DELETE CASCADE,
    horizon_days INTEGER NOT NULL DEFAULT 90,
    dropout_within_horizon BOOLEAN NOT NULL,
    dropout_day INTEGER,
    dropout_reason VARCHAR(64),
    is_censored BOOLEAN NOT NULL DEFAULT FALSE,
    censoring_day INTEGER,
    follow_up_days INTEGER NOT NULL,
    completed_study BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_research_outcomes_enrollment_horizon UNIQUE (enrollment_id, horizon_days)
);

CREATE INDEX ix_research_outcomes_enrollment_id ON research_outcomes(enrollment_id);
CREATE INDEX ix_research_outcomes_dropout ON research_outcomes(dropout_within_horizon, dropout_day);
