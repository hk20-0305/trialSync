-- V20260802_0012__initial_schema.sql
-- Base schema for TrialSync: core enums, tables, foreign keys, and indexes.

-- 1. PostgreSQL ENUMs
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'fact_type') THEN
        CREATE TYPE fact_type AS ENUM ('condition', 'medication', 'observation', 'demographic');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'fact_assertion') THEN
        CREATE TYPE fact_assertion AS ENUM ('present', 'absent', 'unknown');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'version_status') THEN
        CREATE TYPE version_status AS ENUM ('draft', 'approved');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'criterion_kind') THEN
        CREATE TYPE criterion_kind AS ENUM ('inclusion', 'exclusion');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'overall_state') THEN
        CREATE TYPE overall_state AS ENUM ('potentially_eligible', 'likely_ineligible', 'needs_review');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'evaluation_result') THEN
        CREATE TYPE evaluation_result AS ENUM ('pass', 'fail', 'unknown');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'document_kind') THEN
        CREATE TYPE document_kind AS ENUM ('patient', 'trial');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'document_source_type') THEN
        CREATE TYPE document_source_type AS ENUM ('text', 'pdf');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'document_status') THEN
        CREATE TYPE document_status AS ENUM ('needs_review', 'approved', 'rejected');
    END IF;
END $$;

-- 2. Users (Tenancy Anchor)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_catalog_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_users_email ON users(email);

-- 3. Patients
CREATE TABLE IF NOT EXISTS patients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    external_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    date_of_birth DATE,
    sex VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_patients_owner_external UNIQUE (owner_id, external_id),
    CONSTRAINT ck_patients_biological_sex CHECK (sex IS NULL OR sex IN ('male', 'female'))
);

CREATE INDEX IF NOT EXISTS ix_patients_owner_id ON patients(owner_id);

-- 4. Patient Facts
CREATE TABLE IF NOT EXISTS patient_facts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    fact_type fact_type NOT NULL,
    concept VARCHAR(160) NOT NULL,
    value_numeric NUMERIC(18, 6),
    value_text VARCHAR(500),
    unit VARCHAR(40),
    assertion fact_assertion NOT NULL DEFAULT 'present',
    effective_date DATE,
    source_label VARCHAR(120) NOT NULL DEFAULT 'Manual entry',
    voided_at TIMESTAMPTZ,
    void_reason VARCHAR(500),
    voided_by_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_patient_facts_patient_id ON patient_facts(patient_id);
CREATE INDEX IF NOT EXISTS ix_patient_facts_patient_type ON patient_facts(patient_id, fact_type);

-- 5. Patient Unsupported Details
CREATE TABLE IF NOT EXISTS patient_unsupported_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    category VARCHAR(24) NOT NULL,
    label VARCHAR(160) NOT NULL,
    context VARCHAR(500),
    source_label VARCHAR(120) NOT NULL DEFAULT 'Manual review item',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_patient_unsupported_detail_category CHECK (category IN ('condition', 'medication', 'observation', 'other'))
);

CREATE INDEX IF NOT EXISTS ix_patient_unsupported_details_patient_id ON patient_unsupported_details(patient_id);
CREATE INDEX IF NOT EXISTS ix_patient_unsupported_details_patient_category ON patient_unsupported_details(patient_id, category);

-- 6. Patient Change Events
CREATE TABLE IF NOT EXISTS patient_change_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type VARCHAR(32) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id UUID,
    reason VARCHAR(500),
    before_json JSON,
    after_json JSON,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_patient_change_events_patient_id ON patient_change_events(patient_id);
CREATE INDEX IF NOT EXISTS ix_patient_change_events_actor_id ON patient_change_events(actor_id);
CREATE INDEX IF NOT EXISTS ix_patient_change_events_patient_created ON patient_change_events(patient_id, created_at);

-- 7. Trials
CREATE TABLE IF NOT EXISTS trials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    registry_id VARCHAR(64) NOT NULL,
    title VARCHAR(240) NOT NULL,
    condition VARCHAR(160) NOT NULL,
    phase VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_trials_owner_registry UNIQUE (owner_id, registry_id)
);

CREATE INDEX IF NOT EXISTS ix_trials_owner_id ON trials(owner_id);

-- 8. Trial Versions
CREATE TABLE IF NOT EXISTS trial_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trial_id UUID NOT NULL REFERENCES trials(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    status version_status NOT NULL DEFAULT 'draft',
    source_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_trial_versions_trial_version UNIQUE (trial_id, version)
);

CREATE INDEX IF NOT EXISTS ix_trial_versions_trial_id ON trial_versions(trial_id);

-- 9. Criteria
CREATE TABLE IF NOT EXISTS criteria (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trial_version_id UUID NOT NULL REFERENCES trial_versions(id) ON DELETE CASCADE,
    kind criterion_kind NOT NULL,
    "order" INTEGER NOT NULL,
    source_text TEXT NOT NULL,
    normalized_rule JSON,
    required BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_criteria_version_order UNIQUE (trial_version_id, "order")
);

CREATE INDEX IF NOT EXISTS ix_criteria_trial_version_id ON criteria(trial_version_id);

-- 10. Patient Snapshots
CREATE TABLE IF NOT EXISTS patient_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    patient_id UUID REFERENCES patients(id) ON DELETE SET NULL,
    content_hash VARCHAR(64) NOT NULL,
    snapshot_version VARCHAR(64) NOT NULL,
    date_of_birth DATE,
    facts_json JSON NOT NULL,
    source_summary JSON NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_patient_snapshots_patient_content UNIQUE (patient_id, content_hash)
);

CREATE INDEX IF NOT EXISTS ix_patient_snapshots_owner_id ON patient_snapshots(owner_id);
CREATE INDEX IF NOT EXISTS ix_patient_snapshots_patient_id ON patient_snapshots(patient_id);

-- 11. Screening Batches
CREATE TABLE IF NOT EXISTS screening_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label VARCHAR(120),
    pair_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_screening_batches_owner_id ON screening_batches(owner_id);

-- 12. Screenings
CREATE TABLE IF NOT EXISTS screenings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    batch_id UUID REFERENCES screening_batches(id) ON DELETE SET NULL,
    patient_snapshot_id UUID NOT NULL REFERENCES patient_snapshots(id) ON DELETE RESTRICT,
    trial_version_id UUID NOT NULL REFERENCES trial_versions(id) ON DELETE RESTRICT,
    trial_registry_id VARCHAR(64) NOT NULL,
    trial_title VARCHAR(240) NOT NULL,
    trial_version_number INTEGER NOT NULL,
    overall_state overall_state NOT NULL,
    screening_date DATE NOT NULL,
    engine_version VARCHAR(40) NOT NULL,
    dsl_version VARCHAR(20) NOT NULL,
    terminology_version VARCHAR(40) NOT NULL,
    unit_version VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_screenings_owner_id ON screenings(owner_id);
CREATE INDEX IF NOT EXISTS ix_screenings_batch_id ON screenings(batch_id);
CREATE INDEX IF NOT EXISTS ix_screenings_patient_snapshot_id ON screenings(patient_snapshot_id);
CREATE INDEX IF NOT EXISTS ix_screenings_trial_version_id ON screenings(trial_version_id);

-- 13. Criterion Evaluations
CREATE TABLE IF NOT EXISTS criterion_evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    screening_id UUID NOT NULL REFERENCES screenings(id) ON DELETE CASCADE,
    criterion_id UUID NOT NULL REFERENCES criteria(id) ON DELETE RESTRICT,
    criterion_order INTEGER NOT NULL,
    criterion_kind criterion_kind NOT NULL,
    criterion_source_text TEXT NOT NULL,
    result evaluation_result NOT NULL,
    truth VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    canonical_explanation TEXT NOT NULL,
    evidence_json JSON NOT NULL,
    rejected_evidence_json JSON NOT NULL,
    missing_information_json JSON NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_criterion_evaluations_screening_criterion UNIQUE (screening_id, criterion_id)
);

CREATE INDEX IF NOT EXISTS ix_criterion_evaluations_screening_id ON criterion_evaluations(screening_id);
CREATE INDEX IF NOT EXISTS ix_criterion_evaluations_criterion_id ON criterion_evaluations(criterion_id);

-- 14. Screening Chat Messages
CREATE TABLE IF NOT EXISTS screening_chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    screening_id UUID NOT NULL REFERENCES screenings(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    answer_state VARCHAR(32),
    citations_json JSON NOT NULL DEFAULT '[]',
    provider VARCHAR(40),
    model_id VARCHAR(120),
    prompt_version VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chat_message_role CHECK (role IN ('user', 'assistant')),
    CONSTRAINT ck_chat_message_answer_state CHECK (answer_state IS NULL OR answer_state IN ('supported', 'insufficient_evidence', 'refused')),
    CONSTRAINT ck_chat_message_role_state CHECK ((role = 'user' AND answer_state IS NULL) OR (role = 'assistant' AND answer_state IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS ix_screening_chat_messages_screening_id ON screening_chat_messages(screening_id);
CREATE INDEX IF NOT EXISTS ix_screening_chat_messages_screening_created ON screening_chat_messages(screening_id, created_at);

-- 15. Documents
CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind document_kind NOT NULL,
    source_type document_source_type NOT NULL,
    status document_status NOT NULL DEFAULT 'needs_review',
    filename VARCHAR(255),
    mime_type VARCHAR(100) NOT NULL,
    size_bytes INTEGER NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    original_content BYTEA,
    source_text TEXT NOT NULL,
    pages_json JSON NOT NULL,
    candidates_json JSON NOT NULL,
    warnings_json JSON NOT NULL,
    quality_json JSON NOT NULL,
    approved_resource_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_documents_owner_id ON documents(owner_id);
CREATE INDEX IF NOT EXISTS ix_documents_checksum ON documents(checksum);
CREATE INDEX IF NOT EXISTS ix_documents_owner_status ON documents(owner_id, status);

-- 16. Document Spans
CREATE TABLE IF NOT EXISTS document_spans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    page INTEGER NOT NULL,
    start_offset INTEGER NOT NULL,
    end_offset INTEGER NOT NULL,
    exact_text TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_document_spans_document_id ON document_spans(document_id);

-- 17. Clinical Concepts
CREATE TABLE IF NOT EXISTS clinical_concepts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key VARCHAR(80) NOT NULL UNIQUE,
    fact_type fact_type NOT NULL,
    concept VARCHAR(160) NOT NULL,
    display_label VARCHAR(120) NOT NULL,
    concept_group VARCHAR(24) NOT NULL,
    input_kind VARCHAR(24) NOT NULL,
    allowed_assertions_json JSON NOT NULL,
    fixed_unit VARCHAR(40),
    effective_date_required BOOLEAN NOT NULL DEFAULT false,
    screening_supported BOOLEAN NOT NULL DEFAULT true,
    help_text VARCHAR(300) NOT NULL,
    display_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    terminology_system VARCHAR(32),
    terminology_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_clinical_concepts_group CHECK (concept_group IN ('conditions', 'medications', 'observations')),
    CONSTRAINT ck_clinical_concepts_input_kind CHECK (input_kind IN ('status', 'pregnancy_status', 'numeric')),
    CONSTRAINT uq_clinical_concepts_fact_concept UNIQUE (fact_type, concept)
);

CREATE INDEX IF NOT EXISTS ix_clinical_concepts_key ON clinical_concepts(key);
CREATE INDEX IF NOT EXISTS ix_clinical_concepts_active ON clinical_concepts(active);
