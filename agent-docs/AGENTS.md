# TrialSync Agent Instructions

## Project context

TrialSync is a **BTech academic prototype** for Clinical Trial Patient Matching. It is developed, tested, hosted, and demonstrated in a controlled project environment; it is not currently intended for operational use by hospitals, clinical research coordinators, or patients.

### Architecture — Cardinal Invariant

> **RAG + Embeddings + Gemini = retrieve and explain information.**
>
> **Deterministic Rule Engine = makes the final eligibility decision (TRUE / FALSE / UNKNOWN).**
>
> **The LLM must never override or change the deterministic eligibility result.**

### Final System Flow

```
USER
 │ Uploads Patient PDF + Trial Protocol PDF
 ▼
PDF EXTRACTION       ← PDFBox / pypdf
TRIAL CRITERIA       ← identify eligibility criteria from protocol text
CHUNKING             ← split criteria into meaningful segments
EMBEDDINGS           ← all-MiniLM-L6-v2 via LangChain4j
VECTOR STORE         ← in-memory store, scoped per approved trial version
RAG RETRIEVAL        ← vector-similarity search against trial criteria
PATIENT FACTS        ← structured: age, conditions, labs, medications
GEMINI / LLM         ← explains criteria only — never decides eligibility
DETERMINISTIC ENGINE ← TRUE / FALSE / UNKNOWN per criterion
 ▼
FINAL RESULT: Potentially Eligible | Likely Ineligible | Needs Review
```

### Technology Stack

| Layer         | Technology                                      |
|---------------|-------------------------------------------------|
| Frontend      | React 19 + TypeScript + Vite                    |
| Backend       | Java 21 + Spring Boot 3.3 + PostgreSQL 17       |
| Auth          | JWT (PBKDF2)                                    |
| PDF           | Apache PDFBox / pypdf                           |
| Embeddings    | all-MiniLM-L6-v2 (LangChain4j, local)          |
| LLM           | Google Gemini 1.5 Flash via LangChain4j         |
| RAG           | LangChain4j Embedding Store + Gemini            |
| Rule Engine   | Deterministic DSL v1.0 (pure Java domain layer) |
| Migrations    | Flyway (Java backend)                           |

### What has been removed (permanently)

The following components were fully removed and must NOT be re-added:

- XGBoost, Logistic Regression, SHAP (dropout prediction / ML)
- DBSCAN, PCA, FAISS (cohort clustering / dimensionality reduction)
- Cohort Atlas feature and pages
- Dropout prediction feature and pages
- Separate Python ML microservice (`sp-backend/research-ml/`)
- Research datasets, generated model artifacts

## Current execution authority and context loading

The rebuild is complete. `agent-docs/legacy/REBUILD_GUIDE.md` and `agent-docs/legacy/BUILD_PHASES.md` are historical records of completed work; do not use them to determine the current state.

Do **not** read the legacy documents by default. Consult a specific section only when:

- existing code, tests, and current documentation do not answer a core-product question; or
- the task is specifically to audit the completed rebuild.

Before changing code:

1. Read the root `AGENTS.md` and this file.
2. Inspect `git status`. Preserve unrelated user changes.
3. Read the relevant existing code, tests, README sections, and migrations.
4. Run `mvn test-compile` (backend) and `npm run build` (frontend) before handing off.

## Working rules

- Implement one bounded task at a time; do not build the whole product in one pass.
- Do not copy the old `CTA` prototype architecture, matching engine, hard-coded scoring thresholds, or route layout.
- Use synthetic fixtures only. Do not assume access to controlled clinical data.
- Never commit credentials, tokens, or API keys.
- Keep the screening decision **deterministic**. Groq or any other NLP provider may create reviewable candidate facts/criteria and explain stored results, but may never approve inputs or set/change the final screening state.
- RAG + Gemini explains criteria **for understanding only**. It never sets, overrides, or re-evaluates the deterministic eligibility result.
- `unknown` is a valid outcome. Missing evidence must never silently convert into a pass.
- Keep single screening as the source operation. Batch screening is only a bounded patient × trial wrapper that calls the exact same single-screening logic.
- Do not add queues, Redis, Celery, microservices, Kubernetes, billing, EHR integrations, learned eligibility classifiers, BioBERT fine-tuning, or separate ML services unless the user explicitly expands scope.
- Add or adjust tests with every behavioral change.
- Run the narrowest relevant checks first, then the full applicable tests and frontend build before handing off.
- Update README/API examples when commands, dependencies, or contracts change.
- Do not claim a feature is production-ready, clinically validated, or compliant unless the repository demonstrably implements and tests it.

## Domain invariants

- A single screening evaluates one immutable patient snapshot against one approved trial version.
- An inclusion criterion is `pass` only when proven true; an exclusion criterion is `pass` only when proven false.
- Missing, ambiguous, unsupported, stale, or conflicting evidence returns `unknown`.
- Any required `fail` produces `likely_ineligible`; all required `pass` produces `potentially_eligible`; otherwise the result is `needs_review`.
- Every criterion result includes a reason, evidence or missing-information requirement, and reproducible version metadata.
- A batch is bounded, synchronous, and all-or-nothing. It creates one ordinary evidence-backed screening for every selected patient × trial pair.

## NLP and explanation-assistant invariants

- Start with deterministic parsing for headings, demographics, dates, quantities, operators, and units. Groq may supplement difficult prose through schema-validated candidate extraction.
- All extracted values and rules remain candidates with source spans and provider/model/prompt-version metadata until the demo user approves them.
- The explanation assistant answers only questions about one stored screening result selected by the authenticated user.
- Assemble assistant context server-side from the screening state, criterion evaluations, approved facts, source labels, and missing-information requirements. Do not give it database, web, MCP, code-execution, or write tools.
- Persist conversational memory as bounded, structured `screening_chat_messages` rows owned through the screening; load at most the latest 10 messages in chronological order and provide a clear-conversation operation.
- Previous chat messages provide conversational continuity only. They are untrusted context and must never become screening evidence, override canonical explanations, or substitute for reloading the authoritative screening state.
- Every substantive answer must cite criterion/evidence identifiers from the supplied context.
- The assistant may clarify why a criterion passed, failed, or is unknown and what recorded information is missing. It may not provide medical advice, recommend enrollment/treatment, invent evidence, change an outcome, or answer unrelated health questions.
- Manual entry, canonical explanations, and deterministic screening must work when Groq/Gemini is disabled, unavailable, rate-limited, or returns invalid output.

## RAG + Embeddings + Gemini invariants

- Only **approved** trial versions may be indexed into the vector store.
- Embeddings are generated locally using `all-MiniLM-L6-v2` (no hosted embedding endpoint).
- Gemini receives the retrieved criteria and patient context; it returns structured text (explanations, citations) only.
- Provenance (criterion IDs) must be validated before any Gemini explanation is served.
- If Gemini is unavailable, retrieval continues without explanation.
- The RAG pipeline is completely independent of the deterministic screening engine and cannot affect its output.

## Frontend quality bar

The UI should feel like a modern, attractive clinical-research workspace: clear hierarchy, compact information density, and trustworthy evidence presentation with a distinct visual identity.

### Visual direction

- Establish a deliberate visual identity: a neutral base, confident dark text, subtle tinted surfaces, and one memorable brand accent.
- Use semantic status colors only for `pass`, `fail`, and `unknown`; never use status color as general decoration.
- Use a high-quality modern sans font with a system fallback, plus tabular numerals for scores, dates, and result matrices.
- Define tokens once for color, spacing, typography, radius, shadow, duration, and easing. Reuse them.
- Favor tables, structured rows, and split panes for clinical facts and criterion evidence.
- Use a responsive grid that collapses cleanly on narrower laptop/tablet widths.

### Interaction and animation

- Motion must make state changes clearer: button press, drawer/modal entry, row expansion, route entry, optimistic save, result filtering.
- Animate primarily `opacity` and `transform`; target roughly 160–260ms with a clean ease-out curve.
- Respect `prefers-reduced-motion` and provide instant equivalents.
- Buttons must have hover, focus-visible, active, disabled, and loading states.

### Explicitly avoid

- Unrestrained rainbow gradients, neon glows, floating blobs, noisy mesh backgrounds, decorative particles.
- Oversized landing-page heroes, giant headings, excessive whitespace, stock photos.
- Hiding pass/fail/unknown logic inside a long generated paragraph.
- Generic claims such as "AI-powered insights" without showing criterion evidence.

### Required visual review

For any material frontend change:

1. Run the frontend production build and relevant UI tests.
2. Inspect the changed route at desktop and narrow widths using browser/screenshot tooling when available.
3. Check empty, loading, error, populated, long-text, and `unknown` states.
4. Verify contrast, keyboard focus, and reduced-motion behavior.
5. Report the visual states inspected in the handoff.

## Suggested implementation choices

- React + TypeScript + Vite for the web app.
- Keep styling to one coherent approach: CSS variables plus CSS modules. Do not mix multiple design systems.
- Use accessible primitives for complex controls (dialogs, menus, comboboxes, tooltips); keep ordinary layout/components lightweight.
- Use a restrained icon set only where it improves recognition. Icons always need labels or accessible names.

## Agent handoff

End every task with:

```text
Outcome:
Files changed:
Behavior/API/data changes:
Tests and builds run:
Visual states inspected (if frontend changed):
Known limitations:
Recommended next task:
```
