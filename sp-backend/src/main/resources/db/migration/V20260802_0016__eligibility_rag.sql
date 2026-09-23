-- V20260802_0016__eligibility_rag.sql
-- Phase R7: Research Trial Criteria RAG: LangChain4j + Gemini
-- Creates 3 persistence tables: eligibility_rag_indexes, eligibility_rag_runs, and eligibility_rag_results.

-- 1. eligibility_rag_indexes
CREATE TABLE eligibility_rag_indexes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trial_version_id UUID NOT NULL REFERENCES trial_versions(id) ON DELETE CASCADE,
    corpus_checksum VARCHAR(64) NOT NULL,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    index_version VARCHAR(32) NOT NULL DEFAULT 'v1',
    embedding_model VARCHAR(64) NOT NULL DEFAULT 'all-minilm-l6-v2',
    status VARCHAR(32) NOT NULL DEFAULT 'INDEXED',
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_eligibility_rag_indexes_version UNIQUE (trial_version_id, index_version)
);

CREATE INDEX ix_eligibility_rag_indexes_trial_version_id ON eligibility_rag_indexes(trial_version_id);
CREATE INDEX ix_eligibility_rag_indexes_status ON eligibility_rag_indexes(status);

-- 2. eligibility_rag_runs
CREATE TABLE eligibility_rag_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trial_version_id UUID NOT NULL REFERENCES trial_versions(id) ON DELETE CASCADE,
    owner_id UUID REFERENCES users(id) ON DELETE SET NULL,
    query_text TEXT NOT NULL,
    run_type VARCHAR(32) NOT NULL DEFAULT 'EXPLAIN',
    model VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    insufficient_evidence BOOLEAN NOT NULL DEFAULT FALSE,
    summary_text TEXT,
    latency_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_eligibility_rag_runs_trial_version_id ON eligibility_rag_runs(trial_version_id);
CREATE INDEX ix_eligibility_rag_runs_owner_id ON eligibility_rag_runs(owner_id);
CREATE INDEX ix_eligibility_rag_runs_created_at ON eligibility_rag_runs(created_at);

-- 3. eligibility_rag_results
CREATE TABLE eligibility_rag_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rag_run_id UUID NOT NULL REFERENCES eligibility_rag_runs(id) ON DELETE CASCADE,
    criterion_id UUID REFERENCES criteria(id) ON DELETE SET NULL,
    rank INTEGER NOT NULL,
    similarity_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    criterion_kind VARCHAR(64),
    source_text TEXT NOT NULL,
    explanation TEXT,
    citation VARCHAR(255),
    provenance_valid BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_eligibility_rag_results_rag_run_id ON eligibility_rag_results(rag_run_id);
CREATE INDEX ix_eligibility_rag_results_criterion_id ON eligibility_rag_results(criterion_id);
