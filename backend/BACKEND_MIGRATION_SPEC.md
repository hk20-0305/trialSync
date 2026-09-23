# TrialSync Backend Migration Specification
## Python/FastAPI → Java/Spring Boot

**Source analysed:** `backend/` directory as of migration commit (schema revision `20260802_0012`)  
**Scope:** Complete specification for rebuilding the backend in Java/Spring Boot while keeping the React frontend, PostgreSQL database, and all API contracts identical.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack & Dependencies](#2-technology-stack--dependencies)
3. [Configuration & Environment Variables](#3-configuration--environment-variables)
4. [Database Schema](#4-database-schema)
5. [Security: Password Hashing & JWT](#5-security-password-hashing--jwt)
6. [API Endpoints – Complete Reference](#6-api-endpoints--complete-reference)
7. [Request & Response Schemas](#7-request--response-schemas)
8. [Business Rules & Domain Invariants](#8-business-rules--domain-invariants)
9. [Screening Engine DSL](#9-screening-engine-dsl)
10. [NLP Integration (Groq)](#10-nlp-integration-groq)
11. [Document Import Pipeline](#11-document-import-pipeline)
12. [Terminology Suggestions Service](#12-terminology-suggestions-service)
13. [Patient Fact Catalog](#13-patient-fact-catalog)
14. [PDF Report Generation](#14-pdf-report-generation)
15. [Error Response Format](#15-error-response-format)
16. [Middleware](#16-middleware)
17. [Health Endpoints](#17-health-endpoints)
18. [Docker & Deployment](#18-docker--deployment)
19. [Migration Notes & Gotchas](#19-migration-notes--gotchas)

---

## 1. Project Overview

TrialSync is an academic research prototype for clinical trial patient matching.  
The backend provides a REST API consumed by a React/Vite frontend (`web/`).  
The database is PostgreSQL; the ORM in Python is SQLAlchemy async; the framework is FastAPI.

**Key architectural invariants (must preserve in Java):**
- Screening is **deterministic**: no LLM may approve inputs or change a screening outcome.
- A screening always evaluates one **immutable patient snapshot** against one **approved trial version**.
- `unknown` is a valid, first-class result. Missing evidence never automatically becomes a pass.
- Batch screening is a synchronous bounded wrapper that calls single-screening logic for every pair.
- All patient changes are logged immutably in `patient_change_events`.
- Approved trial versions and screening results are immutable records.

---

## 2. Technology Stack & Dependencies

### Python packages to replicate (pick Java equivalents)

| Python Dep | Version | Java Equivalent |
|---|---|---|
| fastapi | 0.139.0 | Spring Boot (spring-boot-starter-web) |
| sqlalchemy[asyncio] | 2.0.51 | Spring Data JPA + Hibernate |
| alembic | 1.16.4 | Flyway or Liquibase |
| psycopg[binary] | 3.3.4 | PostgreSQL JDBC / R2DBC |
| pydantic / pydantic-settings | 2.x | Jakarta Bean Validation, Spring @ConfigurationProperties |
| uvicorn | 0.35.0 | Spring Boot embedded Tomcat/Netty |
| httpx | 0.28.1 | Spring WebClient or OkHttp |
| pypdf | 6.14.2 | Apache PDFBox or iText |
| reportlab | 5.0.0 | Apache PDFBox (iText for layout) |
| pillow | 12.3.0 | (for image embedding in PDFs – Java: PDFBox ImageIO) |
| email-validator | 2.2.0 | Hibernate Validator `@Email` |

---

## 3. Configuration & Environment Variables

All Python settings use the prefix `TRIALSYNC_` (except DATABASE_URL and GROQ_API_KEY which are bare env vars). The `Settings` class reads from `.env` files and environment.

### Full Settings Reference

| Env Var | Type | Default | Notes |
|---|---|---|---|
| `DATABASE_URL` | String | required | Must start with `postgresql+psycopg://` or `postgresql://` |
| `TRIALSYNC_APP_NAME` | String | `TrialSync API` | |
| `TRIALSYNC_ENVIRONMENT` | Enum | `development` | `development`, `test`, `production` |
| `TRIALSYNC_DEBUG` | Boolean | `false` | |
| `TRIALSYNC_AUTH_SECRET` | String | required | Min 32 chars; secret for JWT HMAC-SHA256 |
| `TRIALSYNC_ACCESS_TOKEN_MINUTES` | Int | `480` | 5 <= value <= 1440 |
| `TRIALSYNC_CORS_ORIGINS` | JSON Array | `[]` | List of allowed origins |
| `TRIALSYNC_SCREENING_BATCH_MAX_PATIENTS` | Int | `50` | 1 <= value <= 100 |
| `TRIALSYNC_SCREENING_BATCH_MAX_TRIALS` | Int | `10` | 1 <= value <= 50 |
| `TRIALSYNC_SCREENING_BATCH_MAX_PAIRS` | Int | `500` | 1 <= value <= 1000 |
| `GROQ_API_KEY` | String | `""` | Optional; empty disables Groq |
| `TRIALSYNC_GROQ_MODEL` | String | `openai/gpt-oss-20b` | 1-120 chars |
| `TRIALSYNC_EXTRACTION_PROVIDER` | Enum | `groq` | `auto`, `rule_based`, `groq`, `disabled` |
| `TRIALSYNC_SCREENING_CHAT_PROVIDER` | Enum | `auto` | `auto`, `canonical`, `groq`, `disabled` |
| `TRIALSYNC_PROVIDER_TIMEOUT_SECONDS` | Float | `12.0` | 1.0-30.0 |
| `TRIALSYNC_PROVIDER_MAX_RETRIES` | Int | `1` | 0-2 |
| `TRIALSYNC_PROVIDER_MAX_INPUT_CHARS` | Int | `100000` | 1000-200000 |
| `TRIALSYNC_SCREENING_CHAT_MESSAGE_MAX_CHARS` | Int | `1000` | 100-4000 |
| `TRIALSYNC_SCREENING_CHAT_MAX_MESSAGES` | Int | `10` | 2-20, must be even |
| `TRIALSYNC_SCREENING_CHAT_MAX_ANSWER_CHARS` | Int | `2000` | 200-4000 |
| `TRIALSYNC_TERMINOLOGY_SUGGESTIONS_ENABLED` | Boolean | `true` | |
| `TRIALSYNC_TERMINOLOGY_TIMEOUT_SECONDS` | Float | `5.0` | 1.0-10.0 |
| `TRIALSYNC_TERMINOLOGY_MAX_RESULTS` | Int | `5` | 1-10 |
| `TRIALSYNC_LOINC_USERNAME` | String | `""` | LOINC Basic Auth username |
| `TRIALSYNC_LOINC_PASSWORD` | String | `""` | LOINC Basic Auth password |

### Startup validation
- `DATABASE_URL` **must** begin with `postgresql+psycopg://` or `postgresql://`
- `TRIALSYNC_AUTH_SECRET` **must** be at least 32 characters, or the application must refuse to start

### CORS
Configured from `TRIALSYNC_CORS_ORIGINS`. Allowed methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`. Allowed headers: `Authorization, Content-Type, X-Trace-ID`. Credentials: **not allowed** (`allow_credentials=false`).

---

## 4. Database Schema

### 4.1 Enums

All enums are PostgreSQL native enum types.

```sql
CREATE TYPE fact_type AS ENUM ('condition', 'medication', 'observation', 'demographic');
CREATE TYPE fact_assertion AS ENUM ('present', 'absent', 'unknown');
CREATE TYPE version_status AS ENUM ('draft', 'approved');
CREATE TYPE criterion_kind AS ENUM ('inclusion', 'exclusion');
CREATE TYPE overall_state AS ENUM ('potentially_eligible', 'likely_ineligible', 'needs_review');
CREATE TYPE evaluation_result AS ENUM ('pass', 'fail', 'unknown');
CREATE TYPE document_kind AS ENUM ('patient', 'trial');
CREATE TYPE document_source_type AS ENUM ('text', 'pdf');
CREATE TYPE document_status AS ENUM ('needs_review', 'approved', 'rejected');
```

> **Note:** The Python SQLAlchemy model maps `EvaluationResult.pass_` to the DB value `"pass"` (Python identifier cannot be `pass`). In the database, the stored value is literally `"pass"`.

### 4.2 Tables

#### `users`
```sql
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(320) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_catalog_admin BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_users_email ON users(email);
```

#### `patients`
```sql
CREATE TABLE patients (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    external_id  VARCHAR(64) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    date_of_birth DATE,
    sex          VARCHAR(32) CHECK (sex IS NULL OR sex IN ('male', 'female')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(owner_id, external_id)
);
CREATE INDEX ON patients(owner_id);
```

#### `patient_facts`
```sql
CREATE TABLE patient_facts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id    UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    fact_type     fact_type NOT NULL,
    concept       VARCHAR(160) NOT NULL,
    value_numeric NUMERIC(18,6),
    value_text    VARCHAR(500),
    unit          VARCHAR(40),
    assertion     fact_assertion NOT NULL DEFAULT 'present',
    effective_date DATE,
    source_label  VARCHAR(120) NOT NULL DEFAULT 'Manual entry',
    voided_at     TIMESTAMPTZ,
    void_reason   VARCHAR(500),
    voided_by_id  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON patient_facts(patient_id);
CREATE INDEX ix_patient_facts_patient_type ON patient_facts(patient_id, fact_type);
```
> **Active facts:** Facts are "active" when `voided_at IS NULL`. The Patient-to-facts relationship in Python only loads active facts. Implement this as a filtered association or always filter by `voided_at IS NULL`.

#### `patient_unsupported_details`
```sql
CREATE TABLE patient_unsupported_details (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id  UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    category    VARCHAR(24) NOT NULL CHECK (category IN ('condition', 'medication', 'observation', 'other')),
    label       VARCHAR(160) NOT NULL,
    context     VARCHAR(500),
    source_label VARCHAR(120) NOT NULL DEFAULT 'Manual review item',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON patient_unsupported_details(patient_id);
CREATE INDEX ix_patient_unsupported_details_patient_category ON patient_unsupported_details(patient_id, category);
```

#### `patient_change_events`
```sql
CREATE TABLE patient_change_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id  UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    actor_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type  VARCHAR(32) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id   UUID,
    reason      VARCHAR(500),
    before_json JSONB,
    after_json  JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON patient_change_events(patient_id);
CREATE INDEX ON patient_change_events(actor_id);
CREATE INDEX ix_patient_change_events_patient_created ON patient_change_events(patient_id, created_at);
```
> No `updated_at` on this table. It is immutable after insert.

#### `clinical_concepts`
```sql
CREATE TABLE clinical_concepts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key                   VARCHAR(80) NOT NULL,
    fact_type             fact_type NOT NULL,
    concept               VARCHAR(160) NOT NULL,
    display_label         VARCHAR(120) NOT NULL,
    concept_group         VARCHAR(24) NOT NULL CHECK (concept_group IN ('conditions', 'medications', 'observations')),
    input_kind            VARCHAR(24) NOT NULL CHECK (input_kind IN ('status', 'pregnancy_status', 'numeric')),
    allowed_assertions_json JSONB NOT NULL,
    fixed_unit            VARCHAR(40),
    effective_date_required BOOLEAN NOT NULL DEFAULT false,
    screening_supported   BOOLEAN NOT NULL DEFAULT true,
    help_text             VARCHAR(300) NOT NULL,
    terminology_system    VARCHAR(32),
    terminology_code      VARCHAR(80),
    display_order         INTEGER NOT NULL,
    active                BOOLEAN NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(key),
    UNIQUE(fact_type, concept)
);
CREATE INDEX ON clinical_concepts(active);
CREATE INDEX ON clinical_concepts(key);
```

#### `trials`
```sql
CREATE TABLE trials (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    registry_id  VARCHAR(64) NOT NULL,
    title        VARCHAR(240) NOT NULL,
    condition    VARCHAR(160) NOT NULL,
    phase        VARCHAR(40),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(owner_id, registry_id)
);
CREATE INDEX ON trials(owner_id);
```

#### `trial_versions`
```sql
CREATE TABLE trial_versions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trial_id    UUID NOT NULL REFERENCES trials(id) ON DELETE CASCADE,
    version     INTEGER NOT NULL,
    status      version_status NOT NULL DEFAULT 'draft',
    source_text TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(trial_id, version)
);
CREATE INDEX ON trial_versions(trial_id);
```

#### `criteria`
```sql
CREATE TABLE criteria (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trial_version_id UUID NOT NULL REFERENCES trial_versions(id) ON DELETE CASCADE,
    kind             criterion_kind NOT NULL,
    "order"          INTEGER NOT NULL,
    source_text      TEXT NOT NULL,
    normalized_rule  JSONB,
    required         BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(trial_version_id, "order")
);
CREATE INDEX ON criteria(trial_version_id);
```

#### `documents`
```sql
CREATE TABLE documents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind                document_kind NOT NULL,
    source_type         document_source_type NOT NULL,
    status              document_status NOT NULL DEFAULT 'needs_review',
    filename            VARCHAR(255),
    mime_type           VARCHAR(100) NOT NULL,
    size_bytes          INTEGER NOT NULL,
    checksum            VARCHAR(64) NOT NULL,
    original_content    BYTEA,
    source_text         TEXT NOT NULL,
    pages_json          JSONB NOT NULL,
    candidates_json     JSONB NOT NULL,
    warnings_json       JSONB NOT NULL,
    quality_json        JSONB NOT NULL,
    approved_resource_id UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON documents(owner_id);
CREATE INDEX ix_documents_owner_status ON documents(owner_id, status);
CREATE INDEX ON documents(checksum);
```

#### `document_spans`
```sql
CREATE TABLE document_spans (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    page         INTEGER NOT NULL,
    start_offset INTEGER NOT NULL,
    end_offset   INTEGER NOT NULL,
    exact_text   TEXT NOT NULL
);
CREATE INDEX ON document_spans(document_id);
```
> No `created_at`/`updated_at` on this table.

#### `patient_snapshots`
```sql
CREATE TABLE patient_snapshots (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    patient_id       UUID REFERENCES patients(id) ON DELETE SET NULL,
    content_hash     VARCHAR(64) NOT NULL,
    snapshot_version VARCHAR(64) NOT NULL,
    date_of_birth    DATE,
    facts_json       JSONB NOT NULL,
    source_summary   JSONB NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(patient_id, content_hash)
);
CREATE INDEX ON patient_snapshots(owner_id);
CREATE INDEX ON patient_snapshots(patient_id);
```

#### `screening_batches`
```sql
CREATE TABLE screening_batches (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label      VARCHAR(120),
    pair_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON screening_batches(owner_id);
```

#### `screenings`
```sql
CREATE TABLE screenings (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    batch_id              UUID REFERENCES screening_batches(id) ON DELETE CASCADE,
    patient_snapshot_id   UUID NOT NULL REFERENCES patient_snapshots(id) ON DELETE CASCADE,
    trial_version_id      UUID NOT NULL REFERENCES trial_versions(id) ON DELETE RESTRICT,
    trial_registry_id     VARCHAR(64) NOT NULL,
    trial_title           VARCHAR(240) NOT NULL,
    trial_version_number  INTEGER NOT NULL,
    overall_state         overall_state NOT NULL,
    screening_date        DATE NOT NULL,
    engine_version        VARCHAR(40) NOT NULL,
    dsl_version           VARCHAR(20) NOT NULL,
    terminology_version   VARCHAR(40) NOT NULL,
    unit_version          VARCHAR(40) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON screenings(owner_id);
CREATE INDEX ON screenings(batch_id);
CREATE INDEX ON screenings(patient_snapshot_id);
CREATE INDEX ON screenings(trial_version_id);
```
> `trial_version_id` uses `ON DELETE RESTRICT` – cannot delete a trial version that has screenings.

#### `criterion_evaluations`
```sql
CREATE TABLE criterion_evaluations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    screening_id            UUID NOT NULL REFERENCES screenings(id) ON DELETE CASCADE,
    criterion_id            UUID NOT NULL REFERENCES criteria(id) ON DELETE RESTRICT,
    criterion_order         INTEGER NOT NULL,
    criterion_kind          criterion_kind NOT NULL,
    criterion_source_text   TEXT NOT NULL,
    result                  evaluation_result NOT NULL,
    truth                   VARCHAR(16) NOT NULL,
    reason_code             VARCHAR(64) NOT NULL,
    canonical_explanation   TEXT NOT NULL,
    evidence_json           JSONB NOT NULL,
    rejected_evidence_json  JSONB NOT NULL,
    missing_information_json JSONB NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(screening_id, criterion_id)
);
CREATE INDEX ON criterion_evaluations(screening_id);
CREATE INDEX ON criterion_evaluations(criterion_id);
```

#### `screening_chat_messages`
```sql
CREATE TABLE screening_chat_messages (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    screening_id UUID NOT NULL REFERENCES screenings(id) ON DELETE CASCADE,
    role         VARCHAR(16) NOT NULL CHECK (role IN ('user', 'assistant')),
    content      TEXT NOT NULL,
    answer_state VARCHAR(32) CHECK (answer_state IS NULL OR answer_state IN ('supported', 'insufficient_evidence', 'refused')),
    citations_json JSONB NOT NULL DEFAULT '[]',
    provider     VARCHAR(40),
    model_id     VARCHAR(120),
    prompt_version VARCHAR(40),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chat_message_role_state CHECK (
        (role = 'user' AND answer_state IS NULL) OR
        (role = 'assistant' AND answer_state IS NOT NULL)
    )
);
CREATE INDEX ON screening_chat_messages(screening_id);
CREATE INDEX ix_screening_chat_messages_screening_created ON screening_chat_messages(screening_id, created_at);
```
> No `updated_at` on chat messages. Messages are immutable after insert.

### 4.3 Alembic / Flyway Revision Tracking

The Python app checks `alembic_version.version_num` at startup.  
Expected value: `20260802_0012`.  

In Spring Boot with Flyway/Liquibase, replicate this by maintaining a migration history. The health `/ready` endpoint must verify the schema is at the expected revision.

---

## 5. Security: Password Hashing & JWT

### 5.1 Password Hashing

The Python implementation does **not** use bcrypt. It uses a **custom PBKDF2-SHA256** scheme stored as a delimited string:

```
pbkdf2_sha256$600000$<base64url-salt>$<base64url-digest>
```

- Algorithm: `PBKDF2-HMAC-SHA256`
- Iterations: **600,000**
- Salt: 16 random bytes, encoded as **URL-safe base64 without padding** (`=` stripped)
- Digest: the derived key (32 bytes), encoded as **URL-safe base64 without padding**
- Delimiter: `$`

**Hash a password (pseudocode):**
```
salt = random_bytes(16)
derived = PBKDF2_HMAC_SHA256(password.encode("utf-8"), salt, iterations=600_000, key_len=32)
salt_text = base64url_nopadding(salt)
digest_text = base64url_nopadding(derived)
encoded = "pbkdf2_sha256$600000$" + salt_text + "$" + digest_text
```

**Verify a password:**
```
[algorithm, rounds, salt_text, digest_text] = encoded.split("$", limit=4)
# algorithm must equal "pbkdf2_sha256"
salt = base64url_decode_padded(salt_text)
expected = base64url_decode_padded(digest_text)
actual = PBKDF2_HMAC_SHA256(password.encode("utf-8"), salt, int(rounds), 32)
return constant_time_equal(actual, expected)
```

> In Java, use `SecretKeyFactory` with `PBKDF2WithHmacSHA256`, store 32 bytes. Use `Base64.getUrlEncoder().withoutPadding()` for encoding. Use `MessageDigest.isEqual()` for constant-time compare.

### 5.2 JWT Tokens (Custom – not using a JWT library)

The Python implementation generates **hand-crafted JWTs** using HMAC-SHA256. Structure:

```
base64url(header).base64url(payload).base64url(signature)
```

**Header** (static):
```json
{"alg":"HS256","typ":"JWT"}
```

**Payload**:
```json
{"sub":"<user-uuid-string>","exp":<unix-timestamp-seconds>}
```

**Signature**: `HMAC-SHA256(secret, "<header_part>.<payload_part>".encode("utf-8"))`

All base64 encoding uses **URL-safe base64 without padding** (`=` stripped; add `=` padding for decode).

**Token creation:**
```
header_part = base64url_nopadding(compact_json({"alg":"HS256","typ":"JWT"}))
payload_part = base64url_nopadding(compact_json({"sub": str(user_id), "exp": now + lifetime_minutes*60}))
sig_input = (header_part + "." + payload_part).encode("utf-8")
signature_part = base64url_nopadding(HMAC_SHA256(secret.encode("utf-8"), sig_input))
token = header_part + "." + payload_part + "." + signature_part
```

**Token validation:**
1. Split on `.` – must have exactly 3 parts
2. Recompute `expected_sig = base64url_nopadding(HMAC_SHA256(secret, (parts[0]+"."+parts[1]).encode("utf-8")))`
3. Compare `parts[2] == expected_sig` using **constant-time comparison**
4. Decode payload, check `exp > current_epoch_seconds`
5. Return `UUID.fromString(payload["sub"])`
6. Any exception → return null/empty (invalid token)

**Token transport:** Bearer token in `Authorization: Bearer <token>` header.

**Token lifetime:** `TRIALSYNC_ACCESS_TOKEN_MINUTES` (default 480 = 8 hours).

---

## 6. API Endpoints – Complete Reference

All endpoints are under `/api/v1/` except health endpoints (`/health/`).

### 6.1 Auth Endpoints (`/api/v1/auth`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/register` | Public | Create user, return token |
| POST | `/api/v1/auth/login` | Public | Login, return token |
| GET | `/api/v1/auth/me` | Bearer | Return current user |

#### POST /api/v1/auth/register -> 201
**Request body:**
```json
{
  "email": "user@example.com",
  "display_name": "Jane Doe",
  "password": "at-least-10-chars"
}
```
**Response:** `TokenResponse`  
**Errors:**
- `409 EMAIL_ALREADY_REGISTERED` if email in use

**Logic:**
1. Lowercase the email
2. Strip whitespace from display_name  
3. Hash password with PBKDF2 scheme
4. Insert user, commit
5. Create JWT token
6. Return `TokenResponse`

#### POST /api/v1/auth/login -> 200
**Request body:** `{ "email": "...", "password": "..." }`  
**Response:** `TokenResponse`  
**Errors:** `401 INVALID_CREDENTIALS` if email not found or password wrong (same message for both)

#### GET /api/v1/auth/me -> 200
**Response:** `UserRead`. Requires valid Bearer token.

---

### 6.2 Patient Endpoints (`/api/v1/patients`)

All patient endpoints require Bearer auth. All operations are **owner-scoped** (patients are private to the user who created them).

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/patients` | List patients (limit 100, order by updated_at desc) |
| POST | `/api/v1/patients` | Create patient |
| GET | `/api/v1/patients/{patient_id}` | Get patient by ID |
| PATCH | `/api/v1/patients/{patient_id}` | Update patient (optimistic lock) |
| DELETE | `/api/v1/patients/{patient_id}` | Delete patient |
| GET | `/api/v1/patients/{patient_id}/activity` | Get change history (limit 100) |
| POST | `/api/v1/patients/{patient_id}/facts` | Add fact |
| PATCH | `/api/v1/patients/{patient_id}/facts/{fact_id}` | Update fact |
| DELETE | `/api/v1/patients/{patient_id}/facts/{fact_id}` | Void (soft-delete) fact |
| POST | `/api/v1/patients/{patient_id}/facts/{fact_id}/restore` | Restore voided fact |
| POST | `/api/v1/patients/{patient_id}/unsupported-details` | Add unsupported detail |
| PATCH | `/api/v1/patients/{patient_id}/unsupported-details/{detail_id}` | Update unsupported detail |
| DELETE | `/api/v1/patients/{patient_id}/unsupported-details/{detail_id}` | Delete unsupported detail |

#### GET /api/v1/patients -> 200 list[PatientRead]
- Filter by `owner_id = current_user.id`
- Eager-load facts (active only, `voided_at IS NULL`) and unsupported_details
- Order by `updated_at DESC`, limit 100
- Each `PatientRead` computes `consistency_issues` (pregnancy/sex conflicts)

#### POST /api/v1/patients -> 201 PatientRead
**Request:** `PatientCreate`
**Logic:**
1. Check for duplicate display name (case-insensitive). If found and `confirm_duplicate_name=false`, return `409 PATIENT_NAME_REVIEW_REQUIRED` with `details=[{patient_id, display_name}]`
2. If `external_id` is null, generate `SYN-{10 random hex uppercase chars}`
3. Insert patient, flush
4. Insert `PatientChangeEvent` (event_type=`patient_created`, entity_type=`patient`)
5. Commit

#### PATCH /api/v1/patients/{patient_id} -> 200 PatientRead
**Request:** `PatientUpdate` (includes `expected_updated_at`)
**Logic:**
1. Load patient with facts
2. Compare `patient.updated_at == expected_updated_at`. If mismatch -> `409 PATIENT_RECORD_STALE`
3. If changing sex to male: check if any active fact is `condition.pregnancy` with `assertion=present` -> `409 PATIENT_PREGNANCY_SEX_CONFLICT` with `details=[{fact_id}]`
4. Execute `UPDATE patients SET ... WHERE id=? AND owner_id=? AND updated_at=?` (optimistic lock)
5. If 0 rows updated -> `409 PATIENT_RECORD_STALE`
6. Insert `PatientChangeEvent` (event_type=`profile_updated`)
7. Commit

#### DELETE /api/v1/patients/{patient_id} -> 204
Hard delete. Cascades to facts, unsupported_details, snapshots (SET NULL), change_events.

#### GET /api/v1/patients/{patient_id}/activity -> 200 list[PatientChangeEventRead]
- Verify ownership
- Return `patient_change_events` ordered by `created_at DESC`, limit 100

#### POST /api/v1/patients/{patient_id}/facts -> 201 FactRead
**Request:** `PatientFactCreateRequest`
**Logic:**
1. Load patient with facts, verify ownership
2. Check `patient.updated_at == expected_patient_updated_at` -> `409 PATIENT_RECORD_STALE`
3. Look up catalog entry by `catalog_key` (active only) -> `422 PATIENT_FACT_UNSUPPORTED` if not found
4. Validate value against catalog entry (type check, allowed assertions) -> `422 PATIENT_FACT_VALUE_INVALID`
5. Pregnancy/sex check -> `409 PATIENT_PREGNANCY_SEX_CONFLICT`
6. Check for active duplicate (same patient, fact_type, concept, `voided_at IS NULL`; for numeric: also same `effective_date`) -> `409 PATIENT_FACT_DUPLICATE` with `details=[{fact_id, catalog_key, display_label}]`
7. Insert fact, flush
8. Insert change event (event_type=`fact_created`)
9. Commit

**Fact values set from catalog:**
- `fact_type`, `concept` from catalog entry
- For numeric: `value_numeric` from request, `unit` from `entry.fixed_unit`
- For status/pregnancy: `value_numeric=null`, `unit=null`
- `value_text` is always `null` (catalog facts don't use free-text)

#### PATCH /api/v1/patients/{patient_id}/facts/{fact_id} -> 200 FactRead
**Request:** `PatientFactUpdateRequest`  
Same pattern: verify ownership, load fact (active only), check optimistic lock, validate, save, log event.

#### DELETE /api/v1/patients/{patient_id}/facts/{fact_id} -> 204
**Request body:** `PatientFactVoidRequest { reason, expected_fact_updated_at }`  
**Soft delete only** – sets `voided_at=now()`, `void_reason`, `voided_by_id`. Does NOT hard-delete.

> Note: Uses request body on DELETE. Spring MVC supports this via `@RequestBody` on `@DeleteMapping`.

#### POST /api/v1/patients/{patient_id}/facts/{fact_id}/restore -> 200 FactRead
Clears `voided_at`, `void_reason`, `voided_by_id`. Validates no active duplicate exists first.

---

### 6.3 Trial Endpoints (`/api/v1/trials`)

All trial endpoints require Bearer auth. Owner-scoped.

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/trials` | List trials (limit 100, updated_at desc) |
| POST | `/api/v1/trials` | Create trial |
| GET | `/api/v1/trials/{trial_id}` | Get trial |
| PATCH | `/api/v1/trials/{trial_id}` | Update trial |
| DELETE | `/api/v1/trials/{trial_id}` | Delete trial |
| POST | `/api/v1/trials/{trial_id}/versions` | Create version (raw) |
| POST | `/api/v1/trials/{trial_id}/versions/draft` | Create guided draft version |
| PUT | `/api/v1/trials/{trial_id}/versions/{version_id}` | Update version |
| DELETE | `/api/v1/trials/{trial_id}/versions/{version_id}` | Delete draft version |
| POST | `/api/v1/trials/{trial_id}/versions/{version_id}/criteria` | Add criterion (raw) |
| POST | `/api/v1/trials/{trial_id}/versions/{version_id}/guided-criteria` | Add criterion (guided) |
| POST | `/api/v1/trials/{trial_id}/versions/{version_id}/unsupported-criteria` | Add unsupported criterion |
| PUT | `/api/v1/trials/{trial_id}/versions/{version_id}/criteria/{criterion_id}` | Update criterion |
| PUT | `/api/v1/trials/{trial_id}/versions/{version_id}/guided-criteria/{criterion_id}` | Update guided criterion |
| DELETE | `/api/v1/trials/{trial_id}/versions/{version_id}/criteria/{criterion_id}` | Delete criterion |

**Key business rules for trials:**
- Only **draft** versions can be modified or deleted (`409 APPROVED_VERSION_IMMUTABLE`)
- A version needs at least one criterion AND every criterion has a non-empty `normalized_rule` dict to be approved (`422 TRIAL_VERSION_REVIEW_INCOMPLETE`)
- Deleting a trial fails if it has screenings (`409 TRIAL_HAS_SCREENING_HISTORY`)
- If `registry_id` not provided, generate `SYN-TRIAL-{10 hex uppercase chars}`

**POST .../versions/draft:**
- If a draft already exists -> `409 TRIAL_DRAFT_EXISTS` with `details=[{version_id}]`
- New version number = `last_version.version + 1` (or 1 if no versions)
- Copy `source_text` and all criteria from latest version

**Guided criterion rule construction:**

| subject_key | operator | normalized_rule |
|---|---|---|
| `age` | `gte`/`lte` | `{"op":"gte","fact":"demographic.age","value":N,"unit":"year"}` |
| `age` | `between` | `{"op":"between","fact":"demographic.age","min":N,"max":N,"unit":"year"}` |
| `biological_sex` | `is` | `{"op":"concept_is","fact_type":"demographic","concept":"male"/"female"}` |
| catalog key (condition/med) | `present` | `{"op":"present","fact":"<fact_type>.<concept>"}` |
| catalog key (condition/med) | `absent` | `{"op":"absent","fact":"<fact_type>.<concept>"}` |
| catalog key (numeric obs) | `gte`/`lte` | `{"op":"gte","fact":"...","value":N,"unit":"<fixed_unit>","selection":"latest"}` |
| catalog key (numeric obs) | `between` | `{"op":"between","fact":"...","min":N,"max":N,"unit":"...","selection":"latest"}` |

---

### 6.4 Screening Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/screenings` | Run single screening -> 201 |
| GET | `/api/v1/screenings` | List screenings (limit 100, created_at desc) |
| GET | `/api/v1/screenings/{screening_id}` | Get screening |
| GET | `/api/v1/screenings/{screening_id}/report.pdf` | Download PDF report |
| GET | `/api/v1/screenings/{screening_id}/conversation` | Get chat conversation |
| POST | `/api/v1/screenings/{screening_id}/conversation/messages` | Send chat message -> 201 |
| DELETE | `/api/v1/screenings/{screening_id}/conversation` | Clear conversation -> 204 |
| POST | `/api/v1/screening-batches` | Run batch screening -> 201 |
| GET | `/api/v1/screening-batches` | List batches (limit 100) |
| GET | `/api/v1/screening-batches/{batch_id}` | Get batch |

#### POST /api/v1/screenings -> 201 ScreeningRead
1. Verify patient owned by user
2. Verify `trial_version_id` is an approved version whose trial is owned by user
3. Create/find patient snapshot (content-addressable, see §8.1)
4. Run deterministic screening engine (see §9)
5. Persist screening + criterion evaluations atomically

#### POST /api/v1/screening-batches -> 201 ScreeningBatchRead
Either `patient_ids` XOR `patient_snapshot_ids` must be provided. Enforces size limits from config. All pairs run in one transaction.

#### GET /api/v1/screenings/{id}/report.pdf
Returns binary PDF with `Content-Type: application/pdf` and `Content-Disposition: attachment; filename="trialsync-screening-{id}.pdf"`.

#### POST .../conversation/messages -> 201
1. Validate message length <= `screening_chat_message_max_chars`
2. Load last N messages as history context
3. Build `ScreeningChatContext` from screening + evaluations
4. Detect capability/criterion-state questions -> use `CanonicalExplainer` directly
5. Else call configured provider; on `ProviderCallError` fallback to `CanonicalExplainer` (Groq only)
6. Validate answer (strip invalid citations)
7. Persist user message + assistant message
8. Prune: keep only the latest `screening_chat_max_messages` rows (delete older ones)
9. Return persisted assistant message

---

### 6.5 Import Endpoints (`/api/v1/imports`)

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/imports` | Analyze/upload document -> 201 ImportRead |
| GET | `/api/v1/imports/{import_id}` | Get import |
| PUT | `/api/v1/imports/{import_id}` | Update candidates |
| POST | `/api/v1/imports/{import_id}/approve` | Approve -> create patient or trial |
| DELETE | `/api/v1/imports/{import_id}` | Reject (soft – set status=rejected) |

See §11 for full pipeline details.

---

### 6.6 Clinical Concepts Endpoints (`/api/v1/clinical-concepts`)

**All require Bearer auth AND `is_catalog_admin=true`** (403 `CATALOG_ADMIN_REQUIRED` otherwise).

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/clinical-concepts` | List all concepts (active + inactive) |
| GET | `/api/v1/clinical-concepts/suggestions` | Terminology lookup (RxNorm/LOINC) |
| POST | `/api/v1/clinical-concepts` | Create concept |
| PATCH | `/api/v1/clinical-concepts/{concept_id}` | Update label/help/screening_supported |
| POST | `/api/v1/clinical-concepts/{concept_id}/retire` | Set active=false |
| POST | `/api/v1/clinical-concepts/{concept_id}/restore` | Set active=true |

**List order:** `active DESC, concept_group ASC, display_order ASC, display_label ASC`

**Key derivation from display_label:**
```python
key = re.sub(r"[^a-z0-9]+", "_", display_label.lower()).strip("_")[:80]
```

**Concept metadata by fact_type:**
- `observation` -> `concept_group=observations`, `input_kind=numeric`, `allowed_assertions=["present","unknown"]`, `effective_date_required=true`
- `condition` -> `concept_group=conditions`, `input_kind=status`, `allowed_assertions=["present","absent","unknown"]`, `effective_date_required=false`
- `medication` -> `concept_group=medications`, `input_kind=status`, same assertions as condition

---

### 6.7 Patient Fact Catalog Endpoint

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/patient-fact-catalog` | Get active catalog entries (requires Bearer auth) |

Returns: `PatientFactCatalogResponse { version: "pd0-contract-v1", entries: [...] }`

---

### 6.8 Health Endpoints

| Method | Path | Auth |
|---|---|---|
| GET | `/health/live` | Public |
| GET | `/health/ready` | Public |

`/health/live` -> always `200 {"status": "ok"}` if app is running.  
`/health/ready` -> checks DB and schema version (see §17).

---

## 7. Request & Response Schemas

### TokenResponse
```json
{
  "access_token": "header.payload.sig",
  "token_type": "bearer",
  "user": { "id": "uuid", "email": "...", "display_name": "...", "is_catalog_admin": false, "created_at": "..." }
}
```

### PatientRead
```json
{
  "id": "uuid", "external_id": "...", "display_name": "...",
  "date_of_birth": "YYYY-MM-DD", "sex": "male|female|null",
  "created_at": "...", "updated_at": "...",
  "facts": [ /* FactRead[] (active only) */ ],
  "unsupported_details": [ /* UnsupportedDetailRead[] */ ],
  "consistency_issues": [ /* PatientConsistencyIssue[] – computed on serialization */ ]
}
```

**Consistency issues computed in response:**
- If `pregnancy` (condition, assertion=present) AND `sex=male` -> `PATIENT_PREGNANCY_SEX_CONFLICT` (severity=conflict)
- If `pregnancy` present AND `sex=null` -> `PATIENT_SEX_NOT_RECORDED_FOR_PREGNANCY` (severity=warning)

### PatientFactCreateRequest (discriminated on `input_kind`)
```json
{
  "catalog_key": "hba1c",
  "value": {
    "input_kind": "numeric",
    "assertion": "present",
    "value_numeric": 7.2,
    "effective_date": "2025-01-01"
  },
  "source_label": "Manual entry",
  "expected_patient_updated_at": "ISO datetime"
}
```

Value union:
- `"status"` -> `{ input_kind, assertion, effective_date? }`
- `"pregnancy_status"` -> `{ input_kind, assertion, effective_date (required) }`
- `"numeric"` -> `{ input_kind, assertion (present|unknown), value_numeric?, effective_date (required) }`

### ScreeningRead
```json
{
  "id": "uuid", "batch_id": "uuid|null",
  "patient_snapshot_id": "uuid", "trial_version_id": "uuid",
  "patient_snapshot": { "id", "external_id", "display_name", "date_of_birth", "sex", "facts": [...] },
  "trial_version": { "registry_id", "title", "version" },
  "overall_state": "potentially_eligible|likely_ineligible|needs_review",
  "screening_date": "YYYY-MM-DD",
  "engine_version": "0.1.0", "dsl_version": "1.0",
  "terminology_version": "local-1", "unit_version": "units-1",
  "created_at": "...",
  "counts": { "pass_count": 0, "fail_count": 0, "unknown_count": 0 },
  "evaluations": [
    {
      "id": "uuid", "criterion_id": "uuid",
      "criterion_order": 1, "criterion_kind": "inclusion",
      "result": "pass|fail|unknown", "truth": "true|false|unknown",
      "reason_code": "EVALUATED_TRUE",
      "criterion_source_text": "...", "canonical_explanation": "...",
      "evidence": [ { "fact_id", "source_label", "value", "unit", "effective_date" } ],
      "rejected_evidence": [...], "missing_information": [...]
    }
  ]
}
```

### ScreeningChatMessageRead
```json
{
  "id": "uuid", "role": "user|assistant",
  "content": "...", "answer_state": "supported|insufficient_evidence|refused|null",
  "citations": [ { "criterion_id", "evaluation_id", "evidence_ids": [], "label": "..." } ],
  "provider": { "enabled": true, "provider": "groq", "model": "...", "prompt_version": "..." },
  "created_at": "...",
  "suggested_questions": ["...", "..."]
}
```

### ImportRead
```json
{
  "id": "uuid", "kind": "patient|trial",
  "source_type": "text|pdf", "status": "needs_review|approved|rejected",
  "filename": "...|null", "mime_type": "...", "size_bytes": 0,
  "checksum": "sha256hex", "source_text": "...",
  "pages": [ { "page": 1, "text": "..." } ],
  "candidates": { /* PatientImportCandidates or TrialImportCandidates JSON */ },
  "warnings": ["..."],
  "quality": { /* extraction metadata */ },
  "approved_resource_id": "uuid|null",
  "created_at": "..."
}
```

---

## 8. Business Rules & Domain Invariants

### 8.1 Patient Snapshot (Content-Addressable Immutability)

Before running a screening, a snapshot of the patient is created using a content-addressable hash:

**Canonical JSON structure for hashing:**
```json
{
  "date_of_birth": "YYYY-MM-DD or null",
  "sex": "male|female|null",
  "facts": [
    { "id": "uuid-str", "fact_type": "...", "concept": "...", "value_numeric": "str|null",
      "value_text": "...|null", "unit": "...|null", "assertion": "...",
      "effective_date": "YYYY-MM-DD|null", "source_label": "..." }
  ]
}
```

**Rules:**
- Facts sorted ascending by `str(fact.id)` (UUID as string, not binary)
- `value_numeric` serialized as its string representation (not number)
- JSON serialized with `sort_keys=True`, no extra spaces, `(",",":")` separators
- `content_hash = sha256(canonical_json.encode("utf-8")).hexdigest()`

**Upsert:** If `(patient_id, content_hash)` already exists -> return existing snapshot. Else create new.

**`snapshot_version`** = `content_hash`

**`source_summary` JSON:**
```json
{ "patient_id": "uuid", "external_id": "...", "display_name": "...", "sex": "male|null" }
```

**In the domain snapshot (screening engine), sex is also added as a synthetic fact:**
```json
{ "id": "demographic.sex", "fact_type": "demographic", "concept": "male", "value": "male",
  "assertion": "present", "source_label": "Patient profile", "temporality": "current" }
```

### 8.2 Fact Validation Rules

1. **Catalog lookup:** All user-created facts must reference an active catalog entry by `key`
2. **Duplicate prevention:** Only one active (non-voided) fact per `(patient_id, fact_type, concept)`. For **numeric** entries: also per `effective_date`.
3. **Pregnancy/sex conflict:** Cannot record pregnancy as `present` if `patient.sex = 'male'`
4. **DOB validation:** Cannot be in the future

### 8.3 Trial Version Rules

1. Only `draft` versions can be modified
2. A version needs at least one criterion AND every criterion must have a non-null, non-empty `normalized_rule` to be approved
3. Approved versions are immutable (status is one-way: draft -> approved)
4. Trial cannot be deleted if any screening references it (`ON DELETE RESTRICT`)

### 8.4 Overall State Logic

Given criterion evaluations:
- Any required criterion has `result=fail` -> `likely_ineligible`
- All required criteria have `result=pass` -> `potentially_eligible`
- Otherwise (any required with `result=unknown`) -> `needs_review`

Non-required criteria are counted but don't affect overall state.

### 8.5 Optimistic Locking Pattern

1. Client sends `expected_updated_at`
2. Server includes it in `WHERE updated_at = ?` on UPDATE
3. If 0 rows updated -> `409 PATIENT_RECORD_STALE` (or `TRIAL_...`)

---

## 9. Screening Engine DSL

The screening engine is a **pure function** with no I/O, no randomness, no clock access:

```
screen(PatientSnapshot, ApprovedTrialVersion, ScreeningContext) -> ScreeningResult
```

**Version constants:**
- `ENGINE_VERSION = "0.1.0"`
- `SUPPORTED_DSL_VERSION = "1.0"`
- `terminology_version = "local-1"`
- `unit_version = "units-1"`

### 9.1 Supported Operators

| Op | Args | Description |
|---|---|---|
| `and` | `args: [rule, ...]` | All must be true |
| `or` | `args: [rule, ...]` | At least one true |
| `not` | `arg: rule` | Negates one rule |
| `present` | `fact: "type.concept"` | Concept is present |
| `absent` | `fact: "type.concept"` | Concept is absent |
| `eq` | `fact`, `value`, `unit` | Equals |
| `lt` | `fact`, `value`, `unit` | Less than |
| `lte` | `fact`, `value`, `unit` | Less than or equal |
| `gt` | `fact`, `value`, `unit` | Greater than |
| `gte` | `fact`, `value`, `unit` | Greater than or equal |
| `between` | `fact`, `min`, `max`, `unit` | Inclusive range |
| `concept_is` | `fact_type`, `concept` | Specific concept present |
| `concept_in` | `fact_type`, `concepts: [...]` | Any of concepts present |
| `current` | `arg: rule` | Only use current-temporality facts |
| `within_before` | `days: N`, `arg: rule` | Facts must be within N days before screening_date |

### 9.2 Fact Path Format

`"<fact_type>.<concept>"` e.g. `"condition.type2_diabetes"`, `"observation.hba1c"`, `"demographic.age"`

### 9.3 Matching Logic

1. Match by `fact_type` and `concept` (case-insensitive, trimmed)
2. Only facts where `experiencer == "patient"` (all facts default to "patient")
3. Facts with `effective_date > screening_date` -> rejected (cannot use future evidence)
4. `current` constraint: only facts with `temporality == "current"`
5. `within_before` constraint: only facts where `0 <= (screening_date - effective_date).days <= N`

### 9.4 Presence Evaluation

1. No matching facts -> `unknown (MISSING_FACT)`
2. Both present AND absent facts exist -> `unknown (CONFLICTING_EVIDENCE)`
3. Present facts only -> `truth=true`
4. Absent facts only -> `truth=false`
5. Only unknown-assertion facts -> `unknown (MISSING_FACT)`

For `absent` op: truth = NOT(presence_truth) (with unknown propagating).

### 9.5 Age Calculation

Special fact path `demographic.age`:
- `age = screening_date.year - dob.year - (1 if (screening_month, screening_day) < (dob_month, dob_day))`
- No dob -> `unknown (MISSING_FACT)`
- Unit must be compatible with "year" (alias: "years")

### 9.6 Numeric Selection Strategy

- `"selection": "latest"` (default): Pick facts with most recent effective_date. If multiple facts on same latest date have different values -> `unknown (CONFLICTING_EVIDENCE)`.
- `"selection": "any"`: Use any compatible fact.
- Other selection -> `unknown (UNSUPPORTED_RULE)`

### 9.7 Unit Aliases

```
"%"         -> "%"
"percent"   -> "%"
"year"      -> "year"
"years"     -> "year"
"ml/min/1.73m2"  -> "ml/min/1.73m2"
"ml/min/1.73m2"  -> "ml/min/1.73m2"  (with superscript 2)
```
Normalize: lowercase, strip spaces before alias lookup. If no alias, use normalized string directly.

### 9.8 Criterion Result from Truth

| Kind | Truth | Result |
|---|---|---|
| inclusion | true | pass |
| inclusion | false | fail |
| inclusion | unknown | unknown |
| exclusion | true | fail |
| exclusion | false | pass |
| exclusion | unknown | unknown |

### 9.9 Canonical Explanation Template

```
# For unknown result:
'"{source_text}" is unknown. {missing_details joined by "; " or "Manual review is required."}'

# For pass/fail:
'"{source_text}" {result}ed{" using the recorded evidence" if evidence else ""}.'
```

(`result` value is the string `"pass"` or `"fail"`)

### 9.10 Reason Codes

`EVALUATED_TRUE`, `EVALUATED_FALSE`, `MISSING_FACT`, `STALE_EVIDENCE`, `CONFLICTING_EVIDENCE`, `INCOMPATIBLE_UNIT`, `UNSUPPORTED_RULE`, `INVALID_RULE`

### 9.11 Criteria Ordering

Sort by `(order, str(id))` before evaluation.

### 9.12 DSL Version Check

If `trial.dsl_version != "1.0"` -> every criterion evaluates to `unknown (UNSUPPORTED_RULE)`.

---

## 10. NLP Integration (Groq)

### 10.1 API Endpoint and Parameters

```
POST https://api.groq.com/openai/v1/chat/completions
Authorization: Bearer <GROQ_API_KEY>
Content-Type: application/json

{
  "model": "<TRIALSYNC_GROQ_MODEL>",
  "temperature": 0,
  "max_completion_tokens": <2500 for extraction, 1000 for chat>,
  "response_format": {
    "type": "json_schema",
    "json_schema": { "name": "<name>", "strict": true, "schema": {...} }
  },
  "messages": [...]
}
```

### 10.2 Retry Logic

- Max retries: `TRIALSYNC_PROVIDER_MAX_RETRIES`
- On `429`: read `Retry-After` header (clamp to 0.0-1.0s), wait, retry
- On HTTP 5xx: wait `Retry-After` (or 0.1s), retry
- On timeout: retry
- After all retries exhausted: throw `ProviderCallError`

### 10.3 Extraction Provider Selection

| Config | Groq key | Provider |
|---|---|---|
| `disabled` | any | DisabledExtractor (RuleBased + warning) |
| `rule_based` | any | RuleBasedExtractor |
| `groq` | present | GroqExtractor |
| `groq` | absent | RuleBasedExtractor |
| `auto` | absent | RuleBasedExtractor |
| `auto` | present | GroqExtractor |

**Input size limit:** If Groq extractor selected and `len(text) > provider_max_input_chars` -> raise `PROVIDER_INPUT_TOO_LARGE` -> fall back to RuleBased with warning.

### 10.4 Chat Provider Selection

| Config | Groq key | Provider |
|---|---|---|
| `disabled` | any | DisabledProvider |
| `canonical` | any | CanonicalExplainer |
| `groq` | present | GroqScreeningChatProvider |
| `groq` | absent | DisabledProvider |
| `auto` | absent | CanonicalExplainer |
| `auto` | present | GroqScreeningChatProvider |

**On ProviderCallError (Groq chat):** Fall back to CanonicalExplainer.

### 10.5 Chat Capability Detection (bypass Groq)

Capability question (regex): `\b(?:what|how)\b.{0,60}\b(?:can|does)\b.{0,60}\b(?:assistant|chat|you)\b.{0,60}\b(?:do|help)\b`

Criterion state question (regex): `\b(?:what|which|list|show)\b.{0,80}\b(?:criterion|criteria|criterias)\b.{0,80}\b(?:pass(?:ed|ing)?|fail(?:ed|ing)?|eligible|ineligible|unknown|missing)\b`

If either pattern matches -> use `CanonicalExplainer` directly (even if Groq is configured).

### 10.6 Chat Refusal Patterns

```regex
\b(should|recommend)\b.*\b(enroll|treatment|medication|dose|take)\b
\b(diagnos(e|is)|clinically valid|medical advice|safe to)\b
\b(other|different|another)\b.*\b(patient|trial|screening|record)\b
\b(ignore|override|change|approve|rewrite)\b.*\b(instruction|result|evidence|outcome)\b
\b(system prompt|hidden prompt|weather|sports|stock price)\b
```

If a refusal pattern matches -> return `refused` answer with fixed message.

### 10.7 Chat Answer Validation (Post-processing)

For every citation in the response:
1. Look up `evaluation_id` in the context evaluations
2. Verify `criterion_id` matches
3. Verify all `evidence_ids` are a subset of the evaluation's evidence fact IDs
4. If `answer_state=supported` and any citation is invalid -> downgrade to `insufficient_evidence` with safe fallback message

### 10.8 Prompt Versions

- Extraction: `EXTRACTION_PROMPT_VERSION = "reviewed-extraction-v1"`
- Chat: `CHAT_PROMPT_VERSION = "screening-chat-v1"`

### 10.9 Suggested Questions Generation

After each response, compute up to 3 deduplicated suggested questions:
1. `"Why does this result have its current state?"`
2. If `unknown_count > 0`: `"What information is missing?"` else if `fail_count > 0`: `"Which criteria failed and why?"` else: `"Which criteria passed?"`
3. From provider's suggestions (Groq may return suggestions)
4. Then fill from: `"Which criteria passed?"`, `"What recorded evidence supports this result?"`, `"Which criteria failed and why?"`

Valid suggestion: 8-120 chars, ends with `?`, contains screening-related keyword, not a refusal pattern.

---

## 11. Document Import Pipeline

### 11.1 Analyze (POST /api/v1/imports)

**Request:** `ImportAnalyzeRequest { kind, source_type, text?, content_base64?, filename?, mime_type? }`

**For PDF:**
1. Base64-decode `content_base64` (URL-safe, validated)
2. Parse PDF with PyPDF (Java: Apache PDFBox) -> extract text per page
3. `checksum = sha256(raw_pdf_bytes).hexdigest()`
4. `mime_type = "application/pdf"`, `size_bytes = len(raw_pdf)`

**For text:**
1. Parse text -> extract pages (text splitting logic)
2. `checksum = sha256(text.encode("utf-8")).hexdigest()`
3. `mime_type = "text/plain"`, `size_bytes = len(text.encode("utf-8"))`

**Extraction:**
1. If Groq extractor and `len(text) > provider_max_input_chars` -> raise `PROVIDER_INPUT_TOO_LARGE` -> fallback with warning
2. On any `ProviderCallError` -> fallback to RuleBased with warning message `"External extraction was unavailable; deterministic candidates are shown."`

**Patient document post-processing:**
- Map each extracted concept to catalog entry (including aliases)
- Attach `warnings[]` to each fact candidate with catalog issues

**Span attachment:**
- For each candidate's `source { page, start, end, text }` -> create `DocumentSpan`
- Attach `span_id` to the candidate source JSON

### 11.2 Concept Aliases (import only)

Compact function: `"".join(c for c in value.casefold() if c.isalnum())`

| Input (compacted) | Canonical catalog key |
|---|---|
| type1diabetesmellitus | type1_diabetes |
| type1diabetes | type1_diabetes |
| typeidiabetes | type1_diabetes |
| type2diabetesmellitus | type2_diabetes |
| type2diabetes | type2_diabetes |
| typeiidiabetes | type2_diabetes |
| typeiidiabetesmellitus | type2_diabetes |
| highbloodpressure | hypertension |
| highbp | hypertension |
| reactiveairwaydisease | asthma |
| gestation | pregnancy |
| metforminhydrochloride | metformin |
| atorvastatincalcium | atorvastatin |
| insulintherapy | insulin |
| semaglutideinjection | semaglutide |

### 11.3 Approve (POST /api/v1/imports/{id}/approve)

**For patient documents:**
1. Validate candidates structure (PatientImportCandidates)
2. Re-annotate catalog
3. Duplicate name check -> `409 PATIENT_NAME_REVIEW_REQUIRED` (unless `confirm_duplicate_name=true`)
4. Create `Patient` (external_id = `SYN-{10 hex uppercase}`)
5. Insert patient_created change event
6. For each selected fact: catalog check -> if match and no issues: create `PatientFact` + fact_created event. Else: create `PatientUnsupportedDetail`
7. Set `document.status = approved`, `document.approved_resource_id = patient.id`

**For trial documents:**
1. All selected criteria must have `parse_state="parsed"` and non-null `normalized_rule`
2. Create `Trial` (registry_id = `SYN-TRIAL-{10 hex uppercase}`)
3. Create `TrialVersion` (version=1, status=draft)
4. Create `Criterion` for each selected criterion
5. Set `document.status = approved`, `approved_resource_id = trial.id`

**Reject (DELETE):** Sets `status=rejected`. The document record is NOT deleted.

**Update (PUT):**
- Only allowed when `status=needs_review`
- Validates structure and replaces `candidates_json`
- Verifies span provenance: every candidate's `span_id` must match an existing `DocumentSpan`

---

## 12. Terminology Suggestions Service

### RxNorm (medications)
- URL: `https://rxnav.nlm.nih.gov/REST/approximateTerm.json`
- Params: `{ term: query, maxEntries: N, option: 1 }`
- No authentication
- Response path: `response.approximateGroup.candidate[]` -> `{ rxcui, name, source, score }`

### LOINC (observations)
- URL: `https://loinc.regenstrief.org/searchapi/loincs`
- Params: `{ query: query, rows: N, offset: 0 }`
- HTTP Basic Auth: `TRIALSYNC_LOINC_USERNAME` / `TRIALSYNC_LOINC_PASSWORD`
- If no credentials configured -> return `unavailable_sources` message (not an error)
- Response: look for `Results`, `results`, `items`, or `data` arrays
- Per row: `{ LOINC_NUM, LONG_COMMON_NAME, EXAMPLE_UCUM_UNITS, SHORTNAME }`

**Timeout:** `TRIALSYNC_TERMINOLOGY_TIMEOUT_SECONDS`  
On any HTTP error -> return empty suggestions + error in `unavailable_sources`.

---

## 13. Patient Fact Catalog

### Initial Seed Data

All initial catalog concepts must be seeded into `clinical_concepts` via Flyway migration or a Spring startup hook.

**Conditions (input_kind=status, effective_date_required=false):**

| concept | display_label |
|---|---|
| type1_diabetes | Type 1 Diabetes |
| type2_diabetes | Type 2 Diabetes |
| hypertension | Hypertension |
| asthma | Asthma |
| pregnancy | Pregnancy (input_kind=pregnancy_status, effective_date_required=true) |

**Medications (input_kind=status):** metformin, atorvastatin, insulin, semaglutide

**Observations (input_kind=numeric, effective_date_required=true):**

| concept | fixed_unit |
|---|---|
| hba1c | % |
| fasting_glucose | mg/dL |
| egfr | mL/min/1.73m2 |
| creatinine | mg/dL |
| alt | U/L |
| ast | U/L |
| hemoglobin | g/dL |
| wbc | 10^9/L |
| platelets | 10^9/L |
| ldl | mg/dL |
| triglycerides | mg/dL |
| bmi | kg/m2 |
| systolic_bp | mmHg |
| diastolic_bp | mmHg |
| potassium | mmol/L |
| albumin | g/dL |

**Special:** `pregnancy` uses `input_kind=pregnancy_status` (not `status`) and `effective_date_required=true`, `allowed_assertions=["present","absent","unknown"]`.

---

## 14. PDF Report Generation

`GET /api/v1/screenings/{screening_id}/report.pdf`

Generated at request time from the screening record. Response headers:
- `Content-Type: application/pdf`
- `Content-Disposition: attachment; filename="trialsync-screening-{screening_id}.pdf"`

**Report sections (in order):**
1. Title: `TrialSync Screening Report`
2. Metadata: trial name, patient name, screening date, overall state, generated_at timestamp, engine version, DSL version, terminology version, unit version
3. Summary counts: pass / fail / unknown
4. Criteria table: for each criterion evaluation in order: source_text, kind, result, reason_code, canonical_explanation, evidence list, missing_information list

---

## 15. Error Response Format

All errors return:
```json
{
  "error": {
    "code": "ERROR_CODE_STRING",
    "message": "Human-readable message.",
    "trace_id": "uuid-string",
    "field": "optional_field_name_or_null",
    "details": [ {} ]
  }
}
```

`trace_id` comes from the `X-Trace-ID` response header set by middleware.

### Complete Error Code Reference

| HTTP | Code | Trigger |
|---|---|---|
| 401 | `AUTHENTICATION_REQUIRED` | Missing/invalid token header |
| 401 | `INVALID_TOKEN` | Token valid format but user not found/expired |
| 401 | `INVALID_CREDENTIALS` | Login failure (email or password wrong) |
| 403 | `CATALOG_ADMIN_REQUIRED` | Non-admin on admin endpoints |
| 404 | `PATIENT_NOT_FOUND` | |
| 404 | `FACT_NOT_FOUND` | |
| 404 | `PATIENT_FACT_ALREADY_REMOVED` | Voiding already-voided fact |
| 404 | `TRIAL_NOT_FOUND` | |
| 404 | `TRIAL_VERSION_NOT_FOUND` | |
| 404 | `CRITERION_NOT_FOUND` | |
| 404 | `IMPORT_NOT_FOUND` | |
| 404 | `PATIENT_UNSUPPORTED_DETAIL_NOT_FOUND` | |
| 404 | `PATIENT_SNAPSHOT_NOT_FOUND` | |
| 404 | `SCREENING_NOT_FOUND` | |
| 404 | `SCREENING_BATCH_NOT_FOUND` | |
| 404 | `CATALOG_CONCEPT_NOT_FOUND` | |
| 404 | `APPROVED_TRIAL_VERSION_NOT_FOUND` | |
| 409 | `EMAIL_ALREADY_REGISTERED` | |
| 409 | `PATIENT_NAME_REVIEW_REQUIRED` | Soft duplicate name warning |
| 409 | `PATIENT_EXTERNAL_ID_EXISTS` | |
| 409 | `PATIENT_RECORD_STALE` | Optimistic lock on patient |
| 409 | `PATIENT_FACT_DUPLICATE` | Active duplicate fact |
| 409 | `PATIENT_FACT_RESTORE_CONFLICT` | Restore would create duplicate |
| 409 | `PATIENT_PREGNANCY_SEX_CONFLICT` | |
| 409 | `TRIAL_REGISTRY_ID_EXISTS` | |
| 409 | `TRIAL_HAS_SCREENING_HISTORY` | Cannot delete trial |
| 409 | `TRIAL_DRAFT_EXISTS` | |
| 409 | `TRIAL_VERSION_EXISTS` | |
| 409 | `APPROVED_VERSION_IMMUTABLE` | |
| 409 | `CRITERION_ORDER_EXISTS` | |
| 409 | `IMPORT_ALREADY_REVIEWED` | |
| 409 | `IMPORT_IMMUTABLE` | Edit non-needs_review import |
| 422 | `REQUEST_VALIDATION_ERROR` | Generic validation |
| 422 | `PATIENT_SEX_INVALID` | |
| 422 | `PATIENT_DOB_IN_FUTURE` | |
| 422 | `PATIENT_FACT_VALUE_INVALID` | |
| 422 | `PATIENT_FACT_UNSUPPORTED` | Catalog key not found |
| 422 | `PATIENT_FACT_REMOVAL_REASON_REQUIRED` | |
| 422 | `TRIAL_CRITERION_VALUE_INVALID` | |
| 422 | `TRIAL_VERSION_REVIEW_INCOMPLETE` | |
| 422 | `IMPORT_REVIEW_INVALID` | |
| 422 | `IMPORT_REVIEW_INCOMPLETE` | |
| 422 | `IMPORT_WRONG_TYPE` | |
| 422 | `PDF_MALFORMED` | |
| 422 | `IMPORT_PROVENANCE_INVALID` | |
| 422 | `BATCH_LIMIT_EXCEEDED` | |
| 422 | `ASSISTANT_MESSAGE_TOO_LONG` | |
| 422 | `CATALOG_KEY_INVALID` | |
| 422 | `CATALOG_CONCEPT_EXISTS` | |
| 429 | `ASSISTANT_RATE_LIMITED` | |
| 502 | `ASSISTANT_RESPONSE_INVALID` | |
| 502 | `ASSISTANT_PROVIDER_ERROR` | |
| 503 | `SERVICE_NOT_READY` | DB unreachable |
| 503 | `DATABASE_MIGRATION_REQUIRED` | Schema version mismatch |
| 503 | `ASSISTANT_DISABLED` | |
| 504 | `ASSISTANT_TIMEOUT` | |

---

## 16. Middleware

### TraceIdMiddleware
- Generates UUID v4 as `trace_id` for every request
- Stored on the request context/attributes
- Added as `X-Trace-ID: <uuid>` on every response header
- Available to error handlers for inclusion in error JSON

**Spring Boot equivalent:** `OncePerRequestFilter` that calls `response.setHeader("X-Trace-ID", uuid)` and stores in a `ThreadLocal` or Spring `RequestAttributes`.

### CORS Configuration
- Origins: `TRIALSYNC_CORS_ORIGINS` (JSON array)
- Methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`
- Headers: `Authorization, Content-Type, X-Trace-ID`
- `allow_credentials = false`

---

## 17. Health Endpoints

### GET /health/live -> 200
```json
{ "status": "ok" }
```
Always `200` if JVM/app is running.

### GET /health/ready -> 200 or 503
1. Execute `SELECT 1` against the database
   - On failure: `503` with `{ "error": { "code": "SERVICE_NOT_READY", ... } }`
2. Execute `SELECT version_num FROM alembic_version`
   - If `version_num != "20260802_0012"`: `503` with `{ "error": { "code": "DATABASE_MIGRATION_REQUIRED", ... } }`
3. Return `{ "status": "ready" }` on success

> With Flyway in Java: replace the `alembic_version` check with a Flyway schema history check. The expected revision identifier `20260802_0012` should be stored as a constant and compared to the latest applied migration's version.

---

## 18. Docker & Deployment

### Existing compose.yaml structure

```yaml
services:
  db:
    image: postgres:17
    environment:
      POSTGRES_DB: trialsync
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports: ["5432:5432"]

  backend:
    build: ./backend
    ports: ["8000:8000"]
    env_file: .env
    depends_on: [db]

  web:
    build: ./web
    ports: ["80:80"]
    depends_on: [backend]
```

### Spring Boot Backend Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/trialsync-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Port note

The frontend's `VITE_API_BASE_URL` defaults to `http://localhost:8000/api/v1`. Either configure Spring Boot to listen on port `8000` (via `server.port=8000`) or update the `VITE_API_BASE_URL` env var.

### nginx configuration (web container)

The web container proxies API calls:
```nginx
location /api/ {
    proxy_pass http://backend:8000;
}
location / {
    try_files $uri $uri/ /index.html;
}
```

---

## 19. Migration Notes & Gotchas

### JWT is hand-crafted – must match byte-exactly
Do NOT use a JWT library that adds extra fields or changes key ordering. The Java implementation must produce the same token structure. JSON keys in the header must be `alg` then `typ`; in the payload: `sub` then `exp`. Use `Base64.getUrlEncoder().withoutPadding()`.

### PBKDF2 password hashing – existing DB passwords must still work
Java must implement the same format: `pbkdf2_sha256$600000$<url-safe-nopad-salt>$<url-safe-nopad-digest>`. Do not switch to bcrypt unless you migrate all existing password hashes.

### Content-addressable snapshot hash must be identical
The SHA-256 hash depends on exact JSON serialization: compact separators, `sort_keys=True`, facts sorted by UUID string. Any whitespace or key order difference will create duplicate snapshots. Use a JSON library that supports compact output with sorted keys (e.g., `ObjectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)`).

### `evaluation_result` enum value is `"pass"` not `"pass_"`
PostgreSQL stores `"pass"`. The Python identifier `pass_` is only needed because `pass` is a keyword in Python. In Java the enum variant can be named `PASS` and mapped to the string `"pass"`.

### Active facts are `voided_at IS NULL`
The `Patient.facts` relationship in Python is defined with a lazy filter that excludes voided facts. In JPA, add `@Where(clause = "voided_at IS NULL")` on the `facts` collection in the `Patient` entity, or always explicitly join/filter.

### Request body on DELETE
`DELETE /api/v1/patients/{id}/facts/{fact_id}` sends a JSON body (`PatientFactVoidRequest`). Spring MVC supports `@RequestBody` on `@DeleteMapping`. Some HTTP clients and proxies may strip DELETE bodies – preserve this API contract as-is.

### Batch screening is fully synchronous
All pairs run in a single database transaction. Do not introduce async/parallel execution unless you also add rollback logic.

### Chat messages are pruned on each insert
After inserting a new user+assistant message pair, query the latest `N` message IDs and delete all others for that screening. This keeps the chat window bounded.

### `confirm_duplicate_name` is a soft guard only
Returns `409` on first call (without confirm=true) to surface the conflict in UI. A second call with `confirm_duplicate_name=true` proceeds. No hard enforcement.

### `alembic_version` table
The `/health/ready` endpoint checks this table. With Flyway in Java, the equivalent is `flyway_schema_history`. You must either keep the `alembic_version` table populated by a Flyway migration (insert a row with `version_num='20260802_0012'`) or change the health check to use `flyway_schema_history`.

### Terminology service errors are soft
LOINC/RxNorm failures return empty suggestions with an error string in `unavailable_sources`. They never throw HTTP errors.

### Extraction fallback warning messages
When Groq extraction fails, the warning message `"External extraction was unavailable; deterministic candidates are shown."` is prepended to `warnings_json`. This exact string is shown to users.

### Maximum list sizes
- `/api/v1/patients`, `/api/v1/trials`, `/api/v1/screenings`, `/api/v1/screening-batches` -> 100 items
- `/api/v1/patients/{id}/activity` -> 100 items
- Chat messages in DB per screening -> capped at `screening_chat_max_messages` (default 10)

### `unsupported-criteria` (trial import)
This endpoint stores criteria that cannot be expressed in the DSL. The `normalized_rule` is null; the criterion gets `required=false` implicitly (or they can be flagged but won't block approval). Check the actual API handler for exact behavior.

---

*Specification produced from full source analysis of `backend/src/trialsync/` at schema revision `20260802_0012`.*
*Files analysed: main.py, config.py, security.py, db/models.py, api/{auth,patients,trials,screenings,imports,clinical_concepts,health}.py, schemas.py, screening/service.py, domain/{types,engine,logic}.py, nlp/{groq,extraction,chat}.py, patient_data/{contracts,catalog}.py, imports/schemas.py, terminology/suggestions.py, pyproject.toml, .env.example*
