import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches

# Ensure output directory exists
OUT_DIR = r"d:\PROJECT\TrialSync\paper_figures"
os.makedirs(OUT_DIR, exist_ok=True)

# Set high-DPI and publication styling
plt.rcParams['font.family'] = 'sans-serif'
plt.rcParams['font.sans-serif'] = ['DejaVu Sans', 'Arial', 'Helvetica']
plt.rcParams['font.size'] = 9

def save_fig(fig, name):
    path = os.path.join(OUT_DIR, name)
    fig.savefig(path, dpi=300, bbox_inches='tight', facecolor='white', edgecolor='none')
    plt.close(fig)
    print(f"Saved {path}")

# ==============================================================================
# Figure 1: TrialSync System Architecture
# ==============================================================================
def draw_figure_1():
    fig, ax = plt.subplots(figsize=(11, 7.5))
    ax.set_xlim(0, 11)
    ax.set_ylim(0, 7.5)
    ax.axis('off')

    # Color Palette - Professional Academic Slate & Teal
    c_pres = "#e0f2fe"      # Light Sky
    c_gw = "#f1f5f9"        # Light Slate
    c_core = "#ecfdf5"      # Light Emerald
    c_domain = "#fef3c7"    # Warm Amber
    c_db = "#f5f3ff"        # Light Indigo
    c_aux = "#fdf2f8"       # Light Rose
    border_col = "#334155"

    # Title
    ax.text(5.5, 7.2, "Figure 1: TrialSync Decoupled System Architecture", 
            ha='center', va='center', fontsize=12, fontweight='bold', color='#0f172a')

    # 1. Presentation Layer
    p_box = patches.FancyBboxPatch((0.5, 5.8), 10.0, 1.1, boxstyle="round,pad=0.08,rounding_size=0.15",
                                   facecolor=c_pres, edgecolor="#0284c7", linewidth=1.5)
    ax.add_patch(p_box)
    ax.text(0.7, 6.6, "Presentation Layer (React 19 + TypeScript + Vite)", fontsize=10, fontweight='bold', color="#0369a1")
    ax.text(0.7, 6.1, "• Clinical Patient Workspace (PatientDetailPage)\n• Trial Protocol Editor (TrialDetailPage)", fontsize=8, color="#1e293b")
    ax.text(4.2, 6.1, "• Screening Detail & Evidence View (ScreeningDetailPage)\n• Grounded Conversational Assistant (ScreeningChatPanel)", fontsize=8, color="#1e293b")
    ax.text(8.0, 6.1, "• Document Import Review Workspace\n• Accessible Design Tokens (CSS Variables)", fontsize=8, color="#1e293b")

    # Arrow Down
    ax.annotate('', xy=(5.5, 5.4), xytext=(5.5, 5.8),
                arrowprops=dict(facecolor='#475569', edgecolor='#475569', width=1.5, headwidth=6, shrink=0.05))
    ax.text(5.6, 5.55, "JSON / HTTP + Bearer JWT", fontsize=7.5, color='#475569', fontstyle='italic')

    # 2. Gateway Layer
    gw_box = patches.FancyBboxPatch((0.5, 4.4), 10.0, 0.95, boxstyle="round,pad=0.08,rounding_size=0.15",
                                    facecolor=c_gw, edgecolor="#64748b", linewidth=1.5)
    ax.add_patch(gw_box)
    ax.text(0.7, 5.1, "API & Application Gateway (FastAPI 0.139.0)", fontsize=10, fontweight='bold', color="#334155")
    ax.text(0.7, 4.65, "• TraceIdMiddleware (UUIDv4 X-Trace-ID)\n• Standardized Error Handlers (ApplicationError)", fontsize=8, color="#1e293b")
    ax.text(4.2, 4.65, "• Security & AuthGuard (PBKDF2, HS256 JWT)\n• Scoped Dependency Injection (SessionDep, CurrentUser)", fontsize=8, color="#1e293b")
    ax.text(8.0, 4.65, "• 29 REST Endpoints (10 Resource Groups)\n• Tenant Isolation Enforced (owner_id filter)", fontsize=8, color="#1e293b")

    # Arrow Down to Core Services
    ax.annotate('', xy=(5.5, 4.0), xytext=(5.5, 4.4),
                arrowprops=dict(facecolor='#475569', edgecolor='#475569', width=1.5, headwidth=6, shrink=0.05))

    # 3. Backend Service Layer
    core_box = patches.FancyBboxPatch((0.5, 2.5), 6.5, 1.45, boxstyle="round,pad=0.08,rounding_size=0.15",
                                      facecolor=c_core, edgecolor="#059669", linewidth=1.5)
    ax.add_patch(core_box)
    ax.text(0.7, 3.65, "Backend Service Layer (Python 3.12)", fontsize=10, fontweight='bold', color="#047857")
    ax.text(0.7, 3.1, "• Screening Service (Snapshot materialization & orchestration)\n• Import Service (PDF text parsing, OCR fallback, catalog annotator)\n• Screening Chat Coordinator (Citation validation & refusal gates)", fontsize=8, color="#1e293b")
    ax.text(0.7, 2.7, "• Report Service (ReportLab 5.0.0 vector PDF assembler - provider-free)\n• Clinical Catalog Service (Controlled vocabulary & lifecycle)", fontsize=8, color="#1e293b")

    # Arrow to Domain Engine (Right)
    ax.annotate('', xy=(7.3, 3.2), xytext=(7.0, 3.2),
                arrowprops=dict(facecolor='#b45309', edgecolor='#b45309', width=2, headwidth=7, shrink=0.05))
    ax.text(7.05, 3.35, "Invokes", fontsize=8, color='#b45309', fontweight='bold')

    # 4. Isolated Domain Layer
    dom_box = patches.FancyBboxPatch((7.3, 2.1), 3.2, 2.15, boxstyle="round,pad=0.08,rounding_size=0.15",
                                     facecolor=c_domain, edgecolor="#d97706", linewidth=2.0)
    ax.add_patch(dom_box)
    ax.text(7.45, 3.95, "Pure Domain Engine", fontsize=10, fontweight='bold', color="#b45309")
    ax.text(7.45, 3.7, "[STRICT ARCHITECTURAL ISOLATION]", fontsize=7.5, fontweight='bold', color="#9a3412")
    ax.text(7.45, 3.1, "• Pure Functional Evaluator (engine.py)\n• Kleene 3-Valued Logic (logic.py)\n• 15 DSL 1.0 AST Operators\n• Zero Framework/DB/Clock Imports\n• Evaluates Immutable Dataclasses\n• Output: Deterministic ScreeningResult", fontsize=7.5, color="#1e293b")

    # Arrow from Service to DB
    ax.annotate('', xy=(3.5, 1.8), xytext=(3.5, 2.5),
                arrowprops=dict(facecolor='#475569', edgecolor='#475569', width=1.5, headwidth=6, shrink=0.05))

    # 5. Database Layer (Bottom Left)
    db_box = patches.FancyBboxPatch((0.5, 0.3), 6.5, 1.45, boxstyle="round,pad=0.08,rounding_size=0.15",
                                    facecolor=c_db, edgecolor="#6366f1", linewidth=1.5)
    ax.add_patch(db_box)
    ax.text(0.7, 1.45, "Relational Store (PostgreSQL 17.5 + SQLAlchemy 2.0 AsyncIO)", fontsize=10, fontweight='bold', color="#4338ca")
    ax.text(0.7, 0.95, "• 16 Normalized Tables (12 Alembic Migrations)\n• Immutable Tables: patient_snapshots, criterion_evaluations, screenings\n• Mutable Working Tables: patients, patient_facts, trials, criteria", fontsize=8, color="#1e293b")
    ax.text(0.7, 0.5, "• Referential Invariants: snapshots preserve on patient delete (SET NULL);\n  screened trials strictly protected against deletion (RESTRICT)", fontsize=7.5, color="#1e293b")

    # 6. Auxiliary & External Services (Bottom Right)
    aux_box = patches.FancyBboxPatch((7.3, 0.3), 3.2, 1.45, boxstyle="round,pad=0.08,rounding_size=0.15",
                                     facecolor=c_aux, edgecolor="#db2777", linewidth=1.5)
    ax.add_patch(aux_box)
    ax.text(7.45, 1.45, "Auxiliary External Services", fontsize=9.5, fontweight='bold', color="#be185d")
    ax.text(7.45, 1.25, "[NO ELIGIBILITY AUTHORITY]", fontsize=7.5, fontweight='bold', color="#9d174d")
    ax.text(7.45, 0.85, "• Groq API (openai/gpt-oss-20b JSON schema)\n• Tesseract OCR + Poppler pdftoppm\n• Advisory NLM RxNav & LOINC APIs\n• Strict Citation & Source Span Verification", fontsize=7.5, color="#1e293b")

    # Connect Service to Aux
    ax.annotate('', xy=(7.3, 1.1), xytext=(7.0, 2.5),
                arrowprops=dict(facecolor='#db2777', edgecolor='#db2777', width=1.2, headwidth=5, shrink=0.05, linestyle='--'))
    ax.text(6.4, 1.8, "Advisory\nCalls", fontsize=7.5, color='#be185d', fontweight='bold', ha='center')

    save_fig(fig, "fig1_system_architecture.png")

# ==============================================================================
# Figure 2: Patient-to-Trial Screening Workflow
# ==============================================================================
def draw_figure_2():
    fig, ax = plt.subplots(figsize=(10.5, 6.5))
    ax.set_xlim(0, 10.5)
    ax.set_ylim(0, 6.5)
    ax.axis('off')

    ax.text(5.25, 6.2, "Figure 2: Deterministic Patient-to-Trial Screening Workflow", 
            ha='center', va='center', fontsize=12, fontweight='bold', color='#0f172a')

    # Step 1: Inputs
    b1 = patches.FancyBboxPatch((0.5, 4.3), 2.8, 1.5, boxstyle="round,pad=0.08,rounding_size=0.12",
                                facecolor="#f1f5f9", edgecolor="#475569", linewidth=1.5)
    ax.add_patch(b1)
    ax.text(1.9, 5.5, "1. Screening Input Context", ha='center', fontsize=9.5, fontweight='bold', color="#1e293b")
    ax.text(0.7, 4.9, "• Immutable Patient Snapshot P\n  (SHA-256 content_hash)\n• Approved Trial Version T\n  (Criteria set C, sorted by order)\n• Context (Screening Date t)", fontsize=8, color="#334155")

    # Arrow to Step 2
    ax.annotate('', xy=(3.8, 5.05), xytext=(3.3, 5.05),
                arrowprops=dict(facecolor='#475569', edgecolor='#475569', width=1.5, headwidth=6))

    # Step 2: AST Rule Evaluation
    b2 = patches.FancyBboxPatch((3.8, 4.3), 3.0, 1.5, boxstyle="round,pad=0.08,rounding_size=0.12",
                                facecolor="#fef3c7", edgecolor="#d97706", linewidth=1.5)
    ax.add_patch(b2)
    ax.text(5.3, 5.5, "2. AST Rule Evaluation", ha='center', fontsize=9.5, fontweight='bold', color="#b45309")
    ax.text(4.0, 4.9, "• Evaluate criterion c ∈ C\n• Match clinical concept & unit\n• Apply temporal bounds (current, within)\n• Kleene Truth v ∈ {True, False, Unknown}", fontsize=8, color="#334155")

    # Arrow to Step 3
    ax.annotate('', xy=(7.3, 5.05), xytext=(6.8, 5.05),
                arrowprops=dict(facecolor='#475569', edgecolor='#475569', width=1.5, headwidth=6))

    # Step 3: Kind Mapping
    b3 = patches.FancyBboxPatch((7.3, 4.3), 2.7, 1.5, boxstyle="round,pad=0.08,rounding_size=0.12",
                                facecolor="#e0f2fe", edgecolor="#0284c7", linewidth=1.5)
    ax.add_patch(b3)
    ax.text(8.65, 5.5, "3. Criterion Kind Mapping", ha='center', fontsize=9.5, fontweight='bold', color="#0369a1")
    ax.text(7.5, 4.9, "• Inclusion Criterion:\n  True → PASS, False → FAIL\n• Exclusion Criterion:\n  True → FAIL, False → PASS\n• Unknown → UNKNOWN", fontsize=8, color="#334155")

    # Arrow down from Step 3 to Step 4
    ax.annotate('', xy=(8.65, 3.4), xytext=(8.65, 4.3),
                arrowprops=dict(facecolor='#475569', edgecolor='#475569', width=1.5, headwidth=6))

    # Step 4: Evidence Synthesis (Bottom Right)
    b4 = patches.FancyBboxPatch((6.8, 1.8), 3.2, 1.5, boxstyle="round,pad=0.08,rounding_size=0.12",
                                facecolor="#ecfdf5", edgecolor="#059669", linewidth=1.5)
    ax.add_patch(b4)
    ax.text(8.4, 3.0, "4. Evidence Synthesis", ha='center', fontsize=9.5, fontweight='bold', color="#047857")
    ax.text(7.0, 2.3, "• reason_code & canonical_explanation\n• evidence_json (evaluating facts)\n• rejected_evidence_json (stale/future)\n• missing_information_json (unmet specs)", fontsize=8, color="#334155")

    # Arrow left to Step 5
    ax.annotate('', xy=(5.8, 2.55), xytext=(6.8, 2.55),
                arrowprops=dict(facecolor='#475569', edgecolor='#475569', width=1.5, headwidth=6))

    # Step 5: Overall State Aggregation (Bottom Left / Center)
    b5 = patches.FancyBboxPatch((1.5, 1.4), 4.3, 2.3, boxstyle="round,pad=0.08,rounding_size=0.15",
                                facecolor="#ffffff", edgecolor="#0f172a", linewidth=2.0)
    ax.add_patch(b5)
    ax.text(3.65, 3.4, "5. Overall Screening State Resolution", ha='center', fontsize=10, fontweight='bold', color="#0f172a")

    # Three outcome branches inside box 5
    p_fail = patches.Rectangle((1.7, 2.7), 3.9, 0.45, facecolor="#fee2e2", edgecolor="#ef4444", linewidth=1)
    ax.add_patch(p_fail)
    ax.text(1.8, 2.85, "FAIL: ∃ c ∈ C_req [result(c) = FAIL] ⇒ likely_ineligible", fontsize=7.5, fontweight='bold', color="#b91c1c")

    p_pass = patches.Rectangle((1.7, 2.15), 3.9, 0.45, facecolor="#dcfce7", edgecolor="#22c55e", linewidth=1)
    ax.add_patch(p_pass)
    ax.text(1.8, 2.3, "PASS: ∀ c ∈ C_req [result(c) = PASS] ⇒ potentially_eligible", fontsize=7.5, fontweight='bold', color="#15803d")

    p_unk = patches.Rectangle((1.7, 1.6), 3.9, 0.45, facecolor="#fef3c7", edgecolor="#f59e0b", linewidth=1)
    ax.add_patch(p_unk)
    ax.text(1.8, 1.75, "UNK: Otherwise (any required UNKNOWN) ⇒ needs_review", fontsize=7.5, fontweight='bold', color="#b45309")

    # Arrow down from box 5 to Output
    ax.annotate('', xy=(3.65, 0.8), xytext=(3.65, 1.4),
                arrowprops=dict(facecolor='#475569', edgecolor='#475569', width=1.5, headwidth=6))
    ax.text(3.65, 0.5, "Transactional Persistence: Screening + CriterionEvaluation Records", 
            ha='center', va='center', fontsize=8.5, fontweight='bold', color="#1e293b",
            bbox=dict(boxstyle='round,pad=0.3', facecolor='#f8fafc', edgecolor='#94a3b8'))

    save_fig(fig, "fig2_screening_workflow.png")

# ==============================================================================
# Figure 3: Evidence Traceability Flow
# ==============================================================================
def draw_figure_3():
    fig, ax = plt.subplots(figsize=(11, 6.5))
    ax.set_xlim(0, 11)
    ax.set_ylim(0, 6.5)
    ax.axis('off')

    ax.text(5.5, 6.2, "Figure 3: End-to-End Clinical Evidence Traceability & Provenance", 
            ha='center', va='center', fontsize=12, fontweight='bold', color='#0f172a')

    # Step Boxes across top
    steps = [
        ("Raw Document", "PDF / Plain Text\nSHA-256 Checksum", 0.5, 4.5, 1.8, "#f1f5f9", "#475569"),
        ("Character Spans", "DocumentSpan\n(page, start, end)\nExact raw quote", 2.6, 4.5, 1.8, "#e0f2fe", "#0284c7"),
        ("Candidate Facts", "LLM / Regex Extracted\nSource span verified\nCatalog matched", 4.7, 4.5, 1.8, "#fef3c7", "#d97706"),
        ("Human Approval", "Clinical Coordinator\nVerifies / edits facts\nRejects hallucination", 6.8, 4.5, 1.8, "#ecfdf5", "#059669"),
        ("Approved Fact", "PatientFact row\nsource_label =\n'Imported doc p.X'", 8.9, 4.5, 1.6, "#f5f3ff", "#7c3aed"),
    ]

    for title, desc, x, y, w, bg, border in steps:
        box = patches.FancyBboxPatch((x, y), w, 1.3, boxstyle="round,pad=0.06,rounding_size=0.1",
                                     facecolor=bg, edgecolor=border, linewidth=1.5)
        ax.add_patch(box)
        ax.text(x + w/2, y + 1.05, title, ha='center', fontsize=9, fontweight='bold', color="#0f172a")
        ax.text(x + w/2, y + 0.5, desc, ha='center', fontsize=7.5, color="#334155")

    # Arrows between top steps
    for i in range(len(steps)-1):
        x_start = steps[i][2] + steps[i][4]
        x_end = steps[i+1][2]
        ax.annotate('', xy=(x_end, 5.15), xytext=(x_start, 5.15),
                    arrowprops=dict(facecolor='#64748b', edgecolor='#64748b', width=1.2, headwidth=5))

    # Arrow Down from Approved Fact to Snapshot
    ax.annotate('', xy=(9.7, 3.6), xytext=(9.7, 4.5),
                arrowprops=dict(facecolor='#7c3aed', edgecolor='#7c3aed', width=1.5, headwidth=6))

    # Central Anchor: Immutable Patient Snapshot
    snap_box = patches.FancyBboxPatch((3.0, 2.5), 7.5, 1.1, boxstyle="round,pad=0.08,rounding_size=0.12",
                                      facecolor="#f5f3ff", edgecolor="#6366f1", linewidth=2.0)
    ax.add_patch(snap_box)
    ax.text(6.75, 3.3, "Immutable Patient Snapshot (patient_snapshots)", ha='center', fontsize=10, fontweight='bold', color="#4338ca")
    ax.text(6.75, 2.8, "Canonical Sorted JSON Serialization • SHA-256 Fingerprint (content_hash) • Frozen Clinical State", 
            ha='center', fontsize=8, color="#312e81")

    # Arrow down from Snapshot to Criterion Evaluation
    ax.annotate('', xy=(6.75, 1.9), xytext=(6.75, 2.5),
                arrowprops=dict(facecolor='#475569', edgecolor='#475569', width=1.5, headwidth=6))

    # Criterion Evaluation Box
    eval_box = patches.FancyBboxPatch((0.5, 0.4), 6.0, 1.5, boxstyle="round,pad=0.08,rounding_size=0.12",
                                      facecolor="#fefce8", edgecolor="#ca8a04", linewidth=1.5)
    ax.add_patch(eval_box)
    ax.text(3.5, 1.6, "CriterionEvaluation Record (criterion_evaluations)", ha='center', fontsize=9.5, fontweight='bold', color="#854d0e")
    ax.text(0.7, 1.1, "• evidence_json: Direct supporting fact UUIDs, values, units, dates\n• rejected_evidence_json: Stale facts, post-dated labs, assertion mismatches\n• missing_information_json: Explicit missing clinical requirements", fontsize=7.5, color="#422006")

    # Downstream Artifacts Box (Bottom Right)
    art_box = patches.FancyBboxPatch((7.0, 0.4), 3.5, 1.5, boxstyle="round,pad=0.08,rounding_size=0.12",
                                     facecolor="#ecfdf5", edgecolor="#10b981", linewidth=1.5)
    ax.add_patch(art_box)
    ax.text(8.75, 1.6, "Verifiable Downstream Artifacts", ha='center', fontsize=9.5, fontweight='bold', color="#065f46")
    ax.text(7.2, 1.1, "1. Canonical PDF Report: ReportLab vector\n   document citing evidence IDs & page provenance\n2. Screening Chat Assistant: Server-side citation\n   validation; downgrades ungrounded claims", fontsize=7.5, color="#064e3b")

    # Connect Eval to Artifacts
    ax.annotate('', xy=(7.0, 1.15), xytext=(6.5, 1.15),
                arrowprops=dict(facecolor='#059669', edgecolor='#059669', width=1.5, headwidth=6))

    save_fig(fig, "fig3_evidence_traceability.png")

# ==============================================================================
# Figure 4: Document Import and Extraction Pipeline
# ==============================================================================
def draw_figure_4():
    fig, ax = plt.subplots(figsize=(10.5, 7.0))
    ax.set_xlim(0, 10.5)
    ax.set_ylim(0, 7.0)
    ax.axis('off')

    ax.text(5.25, 6.7, "Figure 4: Document Ingestion, OCR Fallback, and Verification Pipeline", 
            ha='center', va='center', fontsize=12, fontweight='bold', color='#0f172a')

    # Upload
    b_up = patches.FancyBboxPatch((0.5, 5.3), 2.2, 1.0, boxstyle="round,pad=0.08,rounding_size=0.1",
                                  facecolor="#f1f5f9", edgecolor="#64748b", linewidth=1.5)
    ax.add_patch(b_up)
    ax.text(1.6, 5.95, "Document Upload", ha='center', fontsize=9, fontweight='bold')
    ax.text(1.6, 5.55, "Text (<1MB) or\nPDF (<5MB, ≤10 pp)", ha='center', fontsize=7.5)

    # Arrow to pypdf
    ax.annotate('', xy=(3.2, 5.8), xytext=(2.7, 5.8),
                arrowprops=dict(facecolor='#64748b', edgecolor='#64748b', width=1.2, headwidth=5))

    # Native extraction
    b_pdf = patches.FancyBboxPatch((3.2, 5.3), 2.3, 1.0, boxstyle="round,pad=0.08,rounding_size=0.1",
                                   facecolor="#e0f2fe", edgecolor="#0284c7", linewidth=1.5)
    ax.add_patch(b_pdf)
    ax.text(4.35, 5.95, "Native pypdf Extraction", ha='center', fontsize=9, fontweight='bold', color="#0369a1")
    ax.text(4.35, 5.55, "Extract embedded text\nper page", ha='center', fontsize=7.5)

    # Decision: usable text?
    ax.annotate('', xy=(6.0, 5.8), xytext=(5.5, 5.8),
                arrowprops=dict(facecolor='#64748b', edgecolor='#64748b', width=1.2, headwidth=5))

    b_chk = patches.FancyBboxPatch((6.0, 5.2), 2.2, 1.2, boxstyle="round,pad=0.08,rounding_size=0.1",
                                   facecolor="#fffbeb", edgecolor="#f59e0b", linewidth=1.5)
    ax.add_patch(b_chk)
    ax.text(7.1, 6.05, "Text Usability Check", ha='center', fontsize=8.5, fontweight='bold', color="#b45309")
    ax.text(7.1, 5.5, "≥40 non-ws chars &\n≥20 chars / page?", ha='center', fontsize=7.5)

    # Path A: Usable text -> Normalized Pages
    ax.annotate('', xy=(8.7, 5.8), xytext=(8.2, 5.8),
                arrowprops=dict(facecolor='#059669', edgecolor='#059669', width=1.5, headwidth=5))
    ax.text(8.45, 6.0, "Yes", fontsize=7.5, color="#059669", fontweight='bold')

    # Path B: Scanned / insufficient -> OCR fallback
    ax.annotate('', xy=(7.1, 4.3), xytext=(7.1, 5.2),
                arrowprops=dict(facecolor='#dc2626', edgecolor='#dc2626', width=1.5, headwidth=5))
    ax.text(7.2, 4.75, "No (Scanned)", fontsize=7.5, color="#dc2626", fontweight='bold')

    b_ocr = patches.FancyBboxPatch((5.8, 3.2), 2.6, 1.1, boxstyle="round,pad=0.08,rounding_size=0.1",
                                   facecolor="#fee2e2", edgecolor="#ef4444", linewidth=1.5)
    ax.add_patch(b_ocr)
    ax.text(7.1, 3.95, "Local OCR Fallback", ha='center', fontsize=8.5, fontweight='bold', color="#b91c1c")
    ax.text(7.1, 3.5, "Poppler pdftoppm (200 DPI)\n+ Tesseract OCR (PSM 6)", ha='center', fontsize=7.5)

    # Both join to Normalized Document Pages
    b_norm = patches.FancyBboxPatch((8.7, 4.5), 1.6, 1.8, boxstyle="round,pad=0.08,rounding_size=0.1",
                                    facecolor="#f1f5f9", edgecolor="#334155", linewidth=1.5)
    ax.add_patch(b_norm)
    ax.text(9.5, 5.85, "Normalized\nDocument\nPages", ha='center', fontsize=9, fontweight='bold')
    ax.text(9.5, 4.85, "Persisted in\npages_json", ha='center', fontsize=7.5)

    ax.annotate('', xy=(8.7, 3.8), xytext=(8.4, 3.8),
                arrowprops=dict(facecolor='#475569', edgecolor='#475569', width=1.2, headwidth=5))

    # Arrow down to Candidate Extraction
    ax.annotate('', xy=(5.25, 2.6), xytext=(9.5, 4.5),
                arrowprops=dict(facecolor='#475569', edgecolor='#475569', width=1.2, headwidth=5, connectionstyle="arc3,rad=0.2"))

    # Extraction & Span Verification (Middle Row)
    b_ext = patches.FancyBboxPatch((0.5, 1.6), 4.5, 1.2, boxstyle="round,pad=0.08,rounding_size=0.12",
                                   facecolor="#fdf2f8", edgecolor="#db2777", linewidth=1.5)
    ax.add_patch(b_ext)
    ax.text(2.75, 2.45, "AI-Assisted Candidate Extraction (Groq)", ha='center', fontsize=9, fontweight='bold', color="#be185d")
    ax.text(2.75, 1.9, "• openai/gpt-oss-20b with strict JSON schema\n• Proposes candidate clinical facts & source offsets", fontsize=7.5, color="#1e293b")

    b_ver = patches.FancyBboxPatch((5.5, 1.6), 4.5, 1.2, boxstyle="round,pad=0.08,rounding_size=0.12",
                                   facecolor="#fef3c7", edgecolor="#d97706", linewidth=1.5)
    ax.add_patch(b_ver)
    ax.text(7.75, 2.45, "Exact Source Span Verification", ha='center', fontsize=9, fontweight='bold', color="#b45309")
    ax.text(7.75, 1.9, "• Check: source_text[start:end] == quotation\n• Mismatch/failure defaults to RuleBasedExtractor", fontsize=7.5, color="#1e293b")

    ax.annotate('', xy=(5.5, 2.2), xytext=(5.0, 2.2),
                arrowprops=dict(facecolor='#d97706', edgecolor='#d97706', width=1.5, headwidth=5))

    # Bottom Row: Human Review and Approval
    b_rev = patches.FancyBboxPatch((0.5, 0.2), 9.5, 1.0, boxstyle="round,pad=0.08,rounding_size=0.12",
                                   facecolor="#ecfdf5", edgecolor="#059669", linewidth=2.0)
    ax.add_patch(b_rev)
    ax.text(5.25, 0.85, "Human-in-the-Loop Clinical Review & Materialization", ha='center', fontsize=9.5, fontweight='bold', color="#047857")
    ax.text(5.25, 0.45, "Coordinator inspects side-by-side text/candidates • Corrects errors • Rejects ungrounded facts • Materializes PatientFacts", 
            ha='center', fontsize=8, color="#064e3b")

    ax.annotate('', xy=(5.25, 1.2), xytext=(5.25, 1.6),
                arrowprops=dict(facecolor='#059669', edgecolor='#059669', width=1.5, headwidth=5))

    save_fig(fig, "fig4_document_pipeline.png")

# ==============================================================================
# Figure 5: Historical Screening and Auditability
# ==============================================================================
def draw_figure_5():
    fig, ax = plt.subplots(figsize=(10.5, 6.5))
    ax.set_xlim(0, 10.5)
    ax.set_ylim(0, 6.5)
    ax.axis('off')

    ax.text(5.25, 6.2, "Figure 5: Historical Screening Immutability & Auditability Architecture", 
            ha='center', va='center', fontsize=12, fontweight='bold', color='#0f172a')

    # Left: Mutable Operational Realm
    b_mut = patches.FancyBboxPatch((0.5, 1.8), 4.4, 4.0, boxstyle="round,pad=0.1,rounding_size=0.15",
                                   facecolor="#f8fafc", edgecolor="#94a3b8", linewidth=1.5, linestyle='--')
    ax.add_patch(b_mut)
    ax.text(2.7, 5.5, "Mutable Clinical Workspace", ha='center', fontsize=10, fontweight='bold', color="#334155")

    b_pat = patches.FancyBboxPatch((0.8, 4.3), 3.8, 0.9, boxstyle="round,pad=0.06,rounding_size=0.08",
                                   facecolor="#ffffff", edgecolor="#cbd5e1", linewidth=1.2)
    ax.add_patch(b_pat)
    ax.text(2.7, 4.85, "Patient Profile (patients)", ha='center', fontsize=8.5, fontweight='bold')
    ax.text(2.7, 4.5, "display_name, sex, date_of_birth (editable)", ha='center', fontsize=7.5)

    b_fact = patches.FancyBboxPatch((0.8, 3.1), 3.8, 0.95, boxstyle="round,pad=0.06,rounding_size=0.08",
                                    facecolor="#ffffff", edgecolor="#cbd5e1", linewidth=1.2)
    ax.add_patch(b_fact)
    ax.text(2.7, 3.75, "Clinical Facts (patient_facts)", ha='center', fontsize=8.5, fontweight='bold')
    ax.text(2.7, 3.3, "Active facts • Soft-voiding via voided_at\nMandatory clinical void_reason", ha='center', fontsize=7.5)

    b_audit = patches.FancyBboxPatch((0.8, 2.0), 3.8, 0.9, boxstyle="round,pad=0.06,rounding_size=0.08",
                                     facecolor="#ffffff", edgecolor="#cbd5e1", linewidth=1.2)
    ax.add_patch(b_audit)
    ax.text(2.7, 2.55, "Audit Trail (patient_change_events)", ha='center', fontsize=8.5, fontweight='bold')
    ax.text(2.7, 2.2, "Append-only before_json / after_json diffs", ha='center', fontsize=7.5)

    # Transition Arrow
    ax.annotate('', xy=(5.5, 3.8), xytext=(4.9, 3.8),
                arrowprops=dict(facecolor='#0284c7', edgecolor='#0284c7', width=2, headwidth=7))
    ax.text(5.2, 4.1, "Screening\nTriggered", ha='center', fontsize=8, color="#0284c7", fontweight='bold')

    # Right: Immutable Evaluation Realm
    b_imm = patches.FancyBboxPatch((5.5, 1.8), 4.5, 4.0, boxstyle="round,pad=0.1,rounding_size=0.15",
                                   facecolor="#f0fdf4", edgecolor="#16a34a", linewidth=1.8)
    ax.add_patch(b_imm)
    ax.text(7.75, 5.5, "Immutable Screening Records", ha='center', fontsize=10, fontweight='bold', color="#15803d")

    b_snap = patches.FancyBboxPatch((5.8, 4.3), 3.9, 0.9, boxstyle="round,pad=0.06,rounding_size=0.08",
                                    facecolor="#ffffff", edgecolor="#86efac", linewidth=1.2)
    ax.add_patch(b_snap)
    ax.text(7.75, 4.85, "PatientSnapshot (SHA-256)", ha='center', fontsize=8.5, fontweight='bold', color="#166534")
    ax.text(7.75, 4.5, "Canonical JSON • content_hash fingerprint\nSurvives patient deletion (ON DELETE SET NULL)", ha='center', fontsize=7.2)

    b_trial = patches.FancyBboxPatch((5.8, 3.1), 3.9, 0.95, boxstyle="round,pad=0.06,rounding_size=0.08",
                                     facecolor="#ffffff", edgecolor="#86efac", linewidth=1.2)
    ax.add_patch(b_trial)
    ax.text(7.75, 3.75, "Approved TrialVersion", ha='center', fontsize=8.5, fontweight='bold', color="#166534")
    ax.text(7.75, 3.3, "status='approved' is immutable (409 on edit)\nProtected from deletion (ON DELETE RESTRICT)", ha='center', fontsize=7.2)

    b_scr = patches.FancyBboxPatch((5.8, 2.0), 3.9, 0.9, boxstyle="round,pad=0.06,rounding_size=0.08",
                                   facecolor="#ffffff", edgecolor="#86efac", linewidth=1.2)
    ax.add_patch(b_scr)
    ax.text(7.75, 2.55, "Screening & CriterionEvaluations", ha='center', fontsize=8.5, fontweight='bold', color="#166534")
    ax.text(7.75, 2.2, "Permanently frozen evidence & reason codes", ha='center', fontsize=7.2)

    # Bottom Banner: Reproducibility Theorem
    b_bot = patches.FancyBboxPatch((0.5, 0.4), 9.5, 1.0, boxstyle="round,pad=0.08,rounding_size=0.12",
                                   facecolor="#eff6ff", edgecolor="#3b82f6", linewidth=1.5)
    ax.add_patch(b_bot)
    ax.text(5.25, 1.05, "Mathematical Reproducibility Guarantee", ha='center', fontsize=9.5, fontweight='bold', color="#1d4ed8")
    ax.text(5.25, 0.65, "screen(PatientSnapshot, ApprovedTrialVersion, context) produces 100% bit-for-bit identical results\neven if mutable clinical facts are subsequently updated, back-dated, or voided.", 
            ha='center', fontsize=8, color="#1e40af")

    save_fig(fig, "fig5_historical_auditability.png")

# ==============================================================================
# Figure 6: Implemented Features vs Roadmap Features
# ==============================================================================
def draw_figure_6():
    fig, ax = plt.subplots(figsize=(11, 7.2))
    ax.set_xlim(0, 11)
    ax.set_ylim(0, 7.2)
    ax.axis('off')

    ax.text(5.5, 6.8, "Figure 6: Verified Operational System vs. Unimplemented Roadmap Extensions", 
            ha='center', va='center', fontsize=12, fontweight='bold', color='#0f172a')

    # Left Column: VERIFIED IMPLEMENTED SYSTEM (Green)
    b_imp = patches.FancyBboxPatch((0.5, 0.4), 4.8, 6.0, boxstyle="round,pad=0.1,rounding_size=0.15",
                                   facecolor="#f0fdf4", edgecolor="#16a34a", linewidth=2.0)
    ax.add_patch(b_imp)
    ax.text(2.9, 6.1, "Verified Implemented Core (TrialSync)", ha='center', fontsize=11, fontweight='bold', color="#15803d")
    ax.text(2.9, 5.8, "[Audited in Codebase & Evaluated in Test Suites]", ha='center', fontsize=8, color="#166534")

    imp_items = [
        ("Deterministic Screening Engine", "100% rule-based pure Python domain evaluator"),
        ("Three-Valued Kleene Logic", "Strict propagation of {True, False, Unknown}"),
        ("15 DSL 1.0 AST Operators", "Logic, numeric, concept, and temporal bounds"),
        ("Cryptographic Snapshots", "SHA-256 canonical patient snapshot fingerprinting"),
        ("Evidence & Provenance Link", "evidence_json, rejected_evidence, missing_info"),
        ("Source Span Verification", "Exact character quotation verification against text"),
        ("Human-in-the-Loop Review", "Candidate review & manual approval workspace"),
        ("Server-Side Citation Grounding", "Assistant citations validated against DB keys"),
        ("Deterministic PDF Reports", "Provider-free ReportLab vector document generator"),
        ("Controlled Concept Catalog", "25 seeded clinical concepts with fixed units"),
        ("Patient Audit Trail", "Non-destructive soft-voiding & change event logging"),
        ("Optimistic Concurrency Control", "Conflict recovery via expected_updated_at"),
    ]

    y_pos = 5.3
    for title, desc in imp_items:
        ax.text(0.8, y_pos, f"✓  {title}:", fontsize=8, fontweight='bold', color="#166534")
        ax.text(1.1, y_pos - 0.2, desc, fontsize=7.2, color="#334155")
        y_pos -= 0.43

    # Right Column: UNIMPLEMENTED ROADMAP FEATURES (Red / Gray)
    b_road = patches.FancyBboxPatch((5.7, 0.4), 4.8, 6.0, boxstyle="round,pad=0.1,rounding_size=0.15",
                                    facecolor="#fef2f2", edgecolor="#ef4444", linewidth=2.0)
    ax.add_patch(b_road)
    ax.text(8.1, 6.1, "Unimplemented Roadmap Features", ha='center', fontsize=11, fontweight='bold', color="#b91c1c")
    ax.text(8.1, 5.8, "[Documented Only in Project Board / Plans]", ha='center', fontsize=8, color="#991b1b")

    road_items = [
        ("Dropout-Risk Prediction", "Survival analysis / machine learning models"),
        ("XGBoost / LightGBM Models", "Gradient boosting for 30/90-day dropout risk"),
        ("Scenario Lab Simulation", "Missed-dose adherence counterfactual simulation"),
        ("FAISS Cohort Similarity", "Vector embeddings & k-NN patient indexing"),
        ("DBSCAN Phenotyping Clustering", "Unsupervised patient cohort discovery atlas"),
        ("LangChain + Gemini Criteria RAG", "Retrieval-augmented trial search & ranking"),
        ("Reverse Cohort Querying", "API query: 'Which patients qualify for Trial X?'"),
        ("Asynchronous Task Queue", "Celery / Redis workers for Cartesian batch jobs"),
        ("External Blob Storage (S3/MinIO)", "Offloading raw PDF bytes from Postgres TOAST"),
        ("Cursor / Offset Pagination", "List endpoints currently hardcode .limit(100)"),
        ("API Rate Limiting Middleware", "Missing brute-force & AI quota throttling"),
        ("JWT Blacklist / Revocation", "Stateless 8-hour tokens cannot be revoked"),
    ]

    y_pos = 5.3
    for title, desc in road_items:
        ax.text(6.0, y_pos, f"✗  {title}:", fontsize=8, fontweight='bold', color="#991b1b")
        ax.text(6.3, y_pos - 0.2, desc, fontsize=7.2, color="#475569")
        y_pos -= 0.43

    save_fig(fig, "fig6_implemented_vs_roadmap.png")

if __name__ == "__main__":
    print("Generating Figure 1...")
    draw_figure_1()
    print("Generating Figure 2...")
    draw_figure_2()
    print("Generating Figure 3...")
    draw_figure_3()
    print("Generating Figure 4...")
    draw_figure_4()
    print("Generating Figure 5...")
    draw_figure_5()
    print("Generating Figure 6...")
    draw_figure_6()
    print("All 6 figures generated successfully in paper_figures/")
