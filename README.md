# TrialSync

TrialSync is an academic full-stack project for **Clinical Trial Patient Matching**. It connects a deterministic `pass`, `fail`, and `unknown` eligibility engine to immutable patient snapshots, approved trial versions, transactional single/batch screening history, and an evidence-first screening workspace. A RAG + Embeddings + Gemini pipeline retrieves and explains eligibility criteria; the Deterministic Rule Engine makes all final eligibility decisions.

## Architecture Overview

```
USER
 │
 ├─ Upload Patient PDF + Trial Protocol PDF
 │
 ▼
PDF EXTRACTION  ─────────────────────────────────────────────────────────────
 PDFBox / pypdf — extract text, page map, quality report
 │
 ├─► TRIAL CRITERIA EXTRACTION
 │     Identify and review eligibility criteria from protocol text
 │
 ├─► CHUNKING → EMBEDDINGS → VECTOR STORE
 │     all-MiniLM-L6-v2 embeddings via LangChain4j, stored in-memory
 │
 ├─► RAG RETRIEVAL
 │     Vector-similarity search scoped to the approved trial version
 │
 ├─► GEMINI / LLM
 │     Explains criteria and assists structured fact extraction
 │     ⚠ LLM never makes the final eligibility decision
 │
 ├─► PATIENT FACTS
 │     Structured: age, condition, lab values, medications, etc.
 │
 └─► DETERMINISTIC RULE ENGINE
       TRUE / FALSE / UNKNOWN — per criterion
       ▼
 FINAL RESULT
       Potentially Eligible | Likely Ineligible | Needs Review
```

### Cardinal Invariant

> **RAG + Embeddings + Gemini = retrieve and explain information.**
>
> **Deterministic Rule Engine = makes the final eligibility decision.**
>
> **The LLM must never override or change the deterministic eligibility result.**

### Technology Stack

| Layer         | Technology                                      |
|---------------|-------------------------------------------------|
| Frontend      | React 19 + TypeScript + Vite                    |
| Backend       | Java 21 + Spring Boot 3.3                       |
| Database      | PostgreSQL 17                                   |
| Auth          | JWT (PBKDF2 password hashing)                   |
| PDF           | Apache PDFBox (Java) / pypdf (Python)           |
| Embeddings    | all-MiniLM-L6-v2 (LangChain4j, local)          |
| LLM           | Google Gemini 1.5 Flash via LangChain4j         |
| RAG           | LangChain4j Embedding Store + Gemini            |
| Rule Engine   | Deterministic DSL v1.0 (pure Java)              |
| Migrations    | Flyway (Java) / Alembic (Python)                |

## Prerequisites

- Python 3.12 or newer (for the Python backend)
- Java 21 or newer (for the Spring Boot backend)
- Node.js 20.19 or newer and npm
- Docker Engine with Docker Compose
- Tesseract OCR and Poppler (`tesseract-ocr` and `poppler-utils` on Debian/Ubuntu)

## First-time setup

Run these commands from the repository root:

```bash
cp .env.example .env
```

The copied values are local placeholders. Change `POSTGRES_PASSWORD`, the matching password inside `DATABASE_URL`, and `TRIALSYNC_AUTH_SECRET` if this database is accessible beyond your machine. The auth secret must contain at least 32 characters.

Start PostgreSQL and wait for its health check:

```bash
docker compose config --quiet
docker compose up -d --wait db
```

Create the Python backend environment, install the pinned project dependencies, and apply migrations:

```bash
python3 -m venv backend/.venv
backend/.venv/bin/python -m pip install --upgrade pip
backend/.venv/bin/python -m pip install -e './backend[dev]'
backend/.venv/bin/alembic -c backend/alembic.ini upgrade head
```

Install the frontend dependencies from the checked-in lockfile:

```bash
npm --prefix web ci
```

## Run locally

Use two terminals from the repository root.

Backend (Python/FastAPI):

```bash
backend/.venv/bin/uvicorn trialsync.main:create_app --factory --app-dir backend/src --reload
```

Backend (Java/Spring Boot — alternative):

```bash
cd sp-backend && mvn spring-boot:run
```

Frontend:

```bash
npm --prefix web run dev
```

Open `http://localhost:5173` (or `http://127.0.0.1:5173`). The API documentation is at `http://localhost:8000/docs`. Health endpoints are:

- `GET http://localhost:8000/health/live` — process liveness only.
- `GET http://localhost:8000/health/ready` — database connectivity and migration status.

## Product tour

The current workspace is organized around an evidence-first dashboard, saved
screening details, synchronous batch matrices, and review-first imports. These
screenshots use only the seeded synthetic workspace:

![TrialSync dashboard](docs/assets/screenshots/dashboard-desktop.png)

![Screening evidence and grounded assistant](docs/assets/screenshots/screening-detail-chat-desktop.png)

![Grounded assistant at a narrow width](docs/assets/screenshots/screening-detail-chat-narrow.png)

![Batch screening matrix](docs/assets/screenshots/batch-matrix-desktop.png)

![Reviewed import](docs/assets/screenshots/import-review-desktop.png)

The browser API base URL comes from `VITE_API_BASE_URL` in the root `.env`; backend settings come from `DATABASE_URL` and `TRIALSYNC_*` variables. No credentials belong in Git.

## Current capabilities

The current workspace supports the evidence-backed matching workflow:

- Upload or import a Patient PDF and Trial Protocol PDF; text is extracted and reviewed before approval.
- Match a patient against trial criteria and inspect the evidence behind every result.
- Identify missing facts that block a confident match and preserve immutable screening evidence.
- Review imported synthetic text or PDFs before approving structured facts and criteria.
- Internal RAG + Embeddings pipeline retrieves relevant trial criteria via vector similarity to assist eligibility understanding (eligibility decisions remain strictly deterministic).
- Ask evidence-grounded questions about one stored screening without changing its outcome.
- Download a canonical, provider-free PDF report for any saved screening; it is assembled from the stored snapshot, approved trial version, and persisted criterion evaluations.

## Deterministic eligibility engine

The core screening engine evaluates immutable typed inputs without importing any hosted provider, ML package, or external model. Callers supply the screening date explicitly. The versioned `1.0` rule DSL supports:

- `and`, `or`, and `not` with three-valued logic.
- `present`, `absent`, `concept_is`, and `concept_in`.
- `eq`, `lt`, `lte`, `gt`, `gte`, and inclusive `between` comparisons.
- `current` and `within_before` temporal wrappers.
- `latest` and `any` numeric selection.

Missing, stale, conflicting, unsupported, or unit-incompatible evidence returns `unknown`; it never silently passes. Inclusion and exclusion criteria share the same raw truth evaluation but convert truth to results according to criterion kind:

- Any required **`FALSE`** → `likely_ineligible`
- All required **`TRUE`** → `potentially_eligible`
- Otherwise → `needs_review`

The API uses JSON bearer-token authentication:

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/auth/me

GET|POST           /api/v1/patients
GET|PATCH|DELETE   /api/v1/patients/{patient_id}
GET                /api/v1/patient-fact-catalog
GET                /api/v1/patients/{patient_id}/activity
GET|POST           /api/v1/clinical-concepts
GET                /api/v1/clinical-concepts/suggestions
PATCH              /api/v1/clinical-concepts/{concept_id}
POST               /api/v1/clinical-concepts/{concept_id}/retire
POST               /api/v1/clinical-concepts/{concept_id}/restore
POST               /api/v1/patients/{patient_id}/facts
PATCH|DELETE       /api/v1/patients/{patient_id}/facts/{fact_id}
POST               /api/v1/patients/{patient_id}/facts/{fact_id}/restore
POST               /api/v1/patients/{patient_id}/unsupported-details
PATCH|DELETE       /api/v1/patients/{patient_id}/unsupported-details/{detail_id}

GET|POST           /api/v1/trials
GET|PATCH|DELETE   /api/v1/trials/{trial_id}
POST               /api/v1/trials/{trial_id}/versions
POST               /api/v1/trials/{trial_id}/versions/draft
PUT|DELETE         /api/v1/trials/{trial_id}/versions/{version_id}
POST               /api/v1/trials/{trial_id}/versions/{version_id}/criteria
POST               /api/v1/trials/{trial_id}/versions/{version_id}/guided-criteria
PUT                /api/v1/trials/{trial_id}/versions/{version_id}/guided-criteria/{criterion_id}
POST               /api/v1/trials/{trial_id}/versions/{version_id}/unsupported-criteria
PUT|DELETE         /api/v1/trials/{trial_id}/versions/{version_id}/criteria/{criterion_id}

POST               /api/v1/screenings
GET                /api/v1/screenings
GET                /api/v1/screenings/{screening_id}
GET                /api/v1/screenings/{screening_id}/report.pdf
POST               /api/v1/screening-batches
GET                /api/v1/screening-batches
GET                /api/v1/screening-batches/{batch_id}

POST               /api/v1/imports
GET|PUT|DELETE     /api/v1/imports/{import_id}
POST               /api/v1/imports/{import_id}/approve

GET                /api/v1/screenings/{screening_id}/conversation
POST               /api/v1/screenings/{screening_id}/conversation/messages
DELETE             /api/v1/screenings/{screening_id}/conversation

POST               /api/v1/research/rag/trials/{version_id}/index
POST               /api/v1/research/rag/trials/{version_id}/retrieve
POST               /api/v1/research/rag/trials/{version_id}/explain
```

## RAG + Embeddings + Gemini (Criteria Knowledge Base)

The Criteria Knowledge Base pipeline:

1. **Ingest**: An approved trial version is chunked (per criterion) and embedded using `all-MiniLM-L6-v2` via LangChain4j. Embeddings are stored in an in-memory vector store scoped to the trial version.
2. **Retrieve**: A patient-context query is embedded and compared against stored criteria using vector similarity. The top-K most relevant criteria are returned with provenance metadata.
3. **Explain**: The retrieved criteria and patient context are sent to Gemini 1.5 Flash with strict constraints: it must explain what each criterion requires, it must cite criterion IDs, and it **must not** make an eligibility decision.

Only approved trial versions may be indexed. Provenance is validated on every explanation. If Gemini is unavailable, retrieval continues without explanation. The deterministic screening engine remains available independently of Gemini.

## Saved screening history

`POST /api/v1/screenings` accepts a user-owned `patient_id`, an approved
`trial_version_id`, and an optional ISO screening date. It creates or reuses an
immutable patient snapshot, runs the deterministic engine, and stores every
criterion result, evidence reference, rejected evidence item, missing-information
requirement, and version field in one transaction.

Deleting a patient after screening detaches the identity record but keeps its
immutable snapshot and screening history. A trial referenced by a saved screening
cannot be deleted. Editing current patient or trial labels never rewrites a stored
criterion outcome.

`POST /api/v1/screening-batches` accepts unique or repeated current `patient_ids`
or existing `patient_snapshot_ids`, plus approved `trial_version_ids`. Current
patients are snapshotted transactionally before screening. IDs are deduplicated before
the configured limits are checked (50 snapshots, 10 trial versions, and 500 pairs).
The bounded Cartesian product runs synchronously with one screening date and one
engine version, and the whole batch rolls back on unexpected persistence failure.
The response includes state totals, the total unknown-criterion count, and a normal
evidence-backed screening ID for every matrix cell.

## Reviewed imports

Patient and trial list pages link to a review-first import flow for pasted text and
PDFs. Pasted text is limited to 1 MB and PDFs to 5 MB/10 pages. Encrypted,
malformed, empty, and wrong-type PDFs are rejected with explicit error codes. When a
PDF has insufficient embedded text, TrialSync rasterizes it locally and uses
Tesseract OCR (`tesseract-ocr` plus Poppler's `pdftoppm`) with bounded per-page and
whole-document timeouts. OCR text is visibly labelled in the review UI, retains
page-local provenance, and remains unapproved candidate data; poor scans fail
explicitly and manual entry remains available.

Deterministic parsing proposes profile fields, patient facts, trial criteria, and a
small supported subset of rule structures. Every candidate remains editable and
unapproved, with page and character-span provenance, until the authenticated owner
explicitly approves the review.

## Bounded NLP and explanation conversation

Reviewed import uses Groq-assisted extraction by default (`TRIALSYNC_EXTRACTION_PROVIDER=groq`).
With a configured `GROQ_API_KEY`, Groq may propose schema-validated patient facts or
trial criteria from deterministic or local-OCR source text; every proposal must retain an
exact verified source quotation and remains unapproved until human review. Timeout,
rate-limit, invalid-schema, or provider failures fall back visibly to deterministic
candidates and record the provider transition in review metadata.

Saved screening details include a short explanation conversation scoped to that one
authenticated result. The server reloads authoritative evaluations every turn,
validates criterion/evaluation/evidence citations, persists at most the latest 10
messages, and supports chat-only clearing. Advice, diagnosis, enrollment guidance,
cross-record requests, unsupported questions, and prompt injection fail safely.
Canonical explanations and deterministic screening remain available during every
provider failure and cannot be modified through the assistant.

## Reproducible demo and evaluation

Create or restore the fixed synthetic development account and its deterministic
screening matrix:

```bash
make seed-demo
```

Sign in with `demo@trialsync.example` / `SyntheticDemo123!`. The login page can
fill these public synthetic credentials with **Use demo account**. The seed is
idempotent: it replaces only that demo account and creates six fictional patients,
two approved trials, 12 linked screenings with a balanced 4/4/4 state distribution,
and supported/refused/insufficient conversation history. It refuses to run in the
production environment.

The six seeded patients are Synthetic Ada Mercer, Synthetic Ben Carter, Synthetic
Cora Bennett, Synthetic Dev Malik, Synthetic Emi Tanaka, and Synthetic Finn Osei.

Reset only this fixed account with:

```bash
make reset-demo
```

## Production deployment

The development `compose.yaml` intentionally runs only PostgreSQL. The full
production stack is defined in `compose.prod.yaml`. See
[`agent-docs/DEPLOYMENT.md`](agent-docs/DEPLOYMENT.md) for first deployment, migrations, backup,
restore, upgrades, and the required tunnel configuration.

GitHub Actions CI is defined in `.github/workflows/ci.yml` and runs the same backend/frontend
verification gate plus credential-free container builds.

## Verification

With `.env` present and PostgreSQL running, run the complete backend/frontend gate
from the repository root:

```bash
docker compose up -d --wait db
make verify
```

`make verify-backend` and `make verify-frontend` provide narrower full-suite gates.

The backend import is intentionally side-effect free: it does not connect to PostgreSQL, create tables, or load models. Schema changes are made only through Flyway / Alembic.

## Current scope

The implemented application covers owner-scoped synthetic patient and trial records, deterministic
single and batch screening, reviewed text/PDF imports, bounded Groq-assisted candidate extraction,
evidence-grounded screening conversations, and internal criteria RAG retrieval with embeddings.

This is an educational prototype, not a medical device, clinical decision system, or production hospital service.

## License

TrialSync is available under the [MIT License](LICENSE).
