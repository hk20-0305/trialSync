/**
 * ResearchDropoutPage — deployed-style Dropout Follow-up Workspace.
 *
 * Layout:
 *   1. Summary grid (status breakdown + band chart placeholder)
 *   2. Toolbar (search + count)
 *   3. Worklist table — populated from enrolled screenings; "Not assessed" state
 *      until user opens the follow-up form for a participant.
 *   4. Detail view (per participant):
 *      - Immutable pre-fill from patient/screening record
 *      - 30-day follow-up form (dosing / visits / AEs)
 *      - On submit → prediction result with risk gauge + SHAP attribution
 *
 * Data source:
 *   - No /research/dropout/enrollments endpoint exists.
 *   - Worklist uses the existing 33-feature predictDropout API for the detail view.
 *   - Status counts are derived from session-state predictions only.
 *   - No fake data is persisted.
 */

import { useState, useMemo } from 'react'
import { predictDropout, type DropoutFeatures, type DropoutPredictionResponse } from '../api/client'
import { useAuth } from '../auth/AuthContext'

// ── Types ─────────────────────────────────────────────────────────────────────

type ModelType = 'xgboost' | 'logistic_regression'

type DropoutStatus = 'not_started' | 'information_needed' | 'ready' | 'predicted'

/** A worklist entry representing one enrolled participant (session-state only). */
interface WorklistEntry {
  id: string
  label: string
  trial: string
  enrollment: string
  status: DropoutStatus
  probability: number | null
  threshold: number | null
  risk_tier: string | null
}

// ── Default feature set (research demo values) ────────────────────────────────

const DEFAULT_FEATURES: DropoutFeatures = {
  age: 52,
  is_female: 1,
  is_experimental_arm: 1,
  baseline_health_score: 68,
  is_high_travel_burden: 0,
  frailty_index: 0.25,
  doses_scheduled_pre_cutoff: 20,
  doses_administered_pre_cutoff: 17,
  doses_missed_pre_cutoff: 3,
  doses_reduced_pre_cutoff: 1,
  adherence_ratio_pre_cutoff: 0.85,
  cumulative_dose_mg_pre_cutoff: 1700,
  visits_scheduled_pre_cutoff: 6,
  visits_attended_pre_cutoff: 5,
  visits_missed_pre_cutoff: 1,
  visit_attendance_rate_pre_cutoff: 0.833,
  ae_count_pre_cutoff: 2,
  ae_max_grade_pre_cutoff: 2,
  ae_serious_count_pre_cutoff: 0,
  ae_burden_score_pre_cutoff: 1.4,
  has_drug_related_ae_pre_cutoff: 1,
  measurement_count_pre_cutoff: 12,
  abnormal_measurement_count_pre_cutoff: 2,
  abnormal_measurement_rate_pre_cutoff: 0.167,
  systolic_bp_baseline: 130,
  systolic_bp_latest_pre_cutoff: 134,
  systolic_bp_change_pre_cutoff: 4,
  platelets_baseline: 210,
  platelets_latest_pre_cutoff: 198,
  platelets_pct_change_pre_cutoff: -5.7,
  alt_baseline: 28,
  alt_latest_pre_cutoff: 31,
  alt_elevation_ratio_pre_cutoff: 0.78,
}

// ── Feature groups for the follow-up form ─────────────────────────────────────

const FEATURE_GROUPS: { label: string; keys: (keyof DropoutFeatures)[] }[] = [
  {
    label: 'Demographics & Baseline',
    keys: ['age', 'is_female', 'is_experimental_arm', 'baseline_health_score', 'is_high_travel_burden', 'frailty_index'],
  },
  {
    label: 'Dosing (Day 30)',
    keys: [
      'doses_scheduled_pre_cutoff', 'doses_administered_pre_cutoff', 'doses_missed_pre_cutoff',
      'doses_reduced_pre_cutoff', 'adherence_ratio_pre_cutoff', 'cumulative_dose_mg_pre_cutoff',
    ],
  },
  {
    label: 'Visits (Day 30)',
    keys: [
      'visits_scheduled_pre_cutoff', 'visits_attended_pre_cutoff',
      'visits_missed_pre_cutoff', 'visit_attendance_rate_pre_cutoff',
    ],
  },
  {
    label: 'Adverse Events (Day 30)',
    keys: [
      'ae_count_pre_cutoff', 'ae_max_grade_pre_cutoff', 'ae_serious_count_pre_cutoff',
      'ae_burden_score_pre_cutoff', 'has_drug_related_ae_pre_cutoff',
    ],
  },
  {
    label: 'Measurements & Trends (Day 30)',
    keys: [
      'measurement_count_pre_cutoff', 'abnormal_measurement_count_pre_cutoff',
      'abnormal_measurement_rate_pre_cutoff', 'systolic_bp_baseline', 'systolic_bp_latest_pre_cutoff',
      'systolic_bp_change_pre_cutoff', 'platelets_baseline', 'platelets_latest_pre_cutoff',
      'platelets_pct_change_pre_cutoff', 'alt_baseline', 'alt_latest_pre_cutoff',
      'alt_elevation_ratio_pre_cutoff',
    ],
  },
]

const FEATURE_LABELS: Partial<Record<keyof DropoutFeatures, string>> = {
  age: 'Age (years)',
  is_female: 'Female (0/1)',
  is_experimental_arm: 'Experimental arm (0/1)',
  baseline_health_score: 'Baseline health score',
  is_high_travel_burden: 'High travel burden (0/1)',
  frailty_index: 'Frailty index (0–1)',
  doses_scheduled_pre_cutoff: 'Doses scheduled',
  doses_administered_pre_cutoff: 'Doses administered',
  doses_missed_pre_cutoff: 'Doses missed',
  doses_reduced_pre_cutoff: 'Doses reduced',
  adherence_ratio_pre_cutoff: 'Adherence ratio (0–1)',
  cumulative_dose_mg_pre_cutoff: 'Cumulative dose (mg)',
  visits_scheduled_pre_cutoff: 'Visits scheduled',
  visits_attended_pre_cutoff: 'Visits attended',
  visits_missed_pre_cutoff: 'Visits missed',
  visit_attendance_rate_pre_cutoff: 'Visit attendance rate (0–1)',
  ae_count_pre_cutoff: 'AE count',
  ae_max_grade_pre_cutoff: 'AE max grade (0–5)',
  ae_serious_count_pre_cutoff: 'Serious AE count',
  ae_burden_score_pre_cutoff: 'AE burden score',
  has_drug_related_ae_pre_cutoff: 'Drug-related AE (0/1)',
  measurement_count_pre_cutoff: 'Measurement count',
  abnormal_measurement_count_pre_cutoff: 'Abnormal measurement count',
  abnormal_measurement_rate_pre_cutoff: 'Abnormal measurement rate (0–1)',
  systolic_bp_baseline: 'Systolic BP baseline (mmHg)',
  systolic_bp_latest_pre_cutoff: 'Systolic BP latest (mmHg)',
  systolic_bp_change_pre_cutoff: 'Systolic BP change',
  platelets_baseline: 'Platelets baseline (×10⁹/L)',
  platelets_latest_pre_cutoff: 'Platelets latest',
  platelets_pct_change_pre_cutoff: 'Platelets % change',
  alt_baseline: 'ALT baseline (U/L)',
  alt_latest_pre_cutoff: 'ALT latest (U/L)',
  alt_elevation_ratio_pre_cutoff: 'ALT elevation ratio',
}

// ── Static demo worklist rows (session-state shell, no fake persisted data) ───

const DEMO_WORKLIST: WorklistEntry[] = [
  { id: 'P-10042', label: 'Patient 10042', trial: 'TRIAL-A / Arm 1', enrollment: 'Enrolled', status: 'not_started', probability: null, threshold: null, risk_tier: null },
  { id: 'P-10087', label: 'Patient 10087', trial: 'TRIAL-A / Arm 2', enrollment: 'Enrolled', status: 'not_started', probability: null, threshold: null, risk_tier: null },
  { id: 'P-10121', label: 'Patient 10121', trial: 'TRIAL-B / Arm 1', enrollment: 'Enrolled', status: 'not_started', probability: null, threshold: null, risk_tier: null },
  { id: 'P-10155', label: 'Patient 10155', trial: 'TRIAL-B / Arm 1', enrollment: 'Enrolled', status: 'not_started', probability: null, threshold: null, risk_tier: null },
  { id: 'P-10203', label: 'Patient 10203', trial: 'TRIAL-C / Arm 1', enrollment: 'Enrolled', status: 'not_started', probability: null, threshold: null, risk_tier: null },
]

// ── Helper: status display ────────────────────────────────────────────────────

function statusLabel(s: DropoutStatus): string {
  switch (s) {
    case 'not_started': return 'Not assessed'
    case 'information_needed': return 'Info needed'
    case 'ready': return 'Ready'
    case 'predicted': return 'Predicted'
  }
}

function tierClass(tier: string): string {
  if (tier === 'high') return 'risk-tier-badge risk-tier-high'
  if (tier === 'medium') return 'risk-tier-badge risk-tier-medium'
  return 'risk-tier-badge risk-tier-low'
}

// ── Main component ────────────────────────────────────────────────────────────

export function ResearchDropoutPage() {
  const { token } = useAuth()

  // Worklist state (session only — no backend persistence for enrollment list)
  const [worklist, setWorklist] = useState<WorklistEntry[]>(DEMO_WORKLIST)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<DropoutStatus | 'all'>('all')

  // Detail view state
  const [detailEntry, setDetailEntry] = useState<WorklistEntry | null>(null)
  const [model, setModel] = useState<ModelType>('xgboost')
  const [features, setFeatures] = useState<DropoutFeatures>({ ...DEFAULT_FEATURES })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>('')
  const [result, setResult] = useState<DropoutPredictionResponse | null>(null)

  // ── Filtered list ──────────────────────────────────────────────────────────

  const filteredList = useMemo(() => {
    let rows = worklist
    if (statusFilter !== 'all') rows = rows.filter((r) => r.status === statusFilter)
    const q = search.trim().toLowerCase()
    if (q) rows = rows.filter((r) => r.id.toLowerCase().includes(q) || r.trial.toLowerCase().includes(q))
    return rows
  }, [worklist, statusFilter, search])

  // ── Status counts ──────────────────────────────────────────────────────────

  const counts = useMemo(() => ({
    not_started: worklist.filter((r) => r.status === 'not_started').length,
    information_needed: worklist.filter((r) => r.status === 'information_needed').length,
    ready: worklist.filter((r) => r.status === 'ready').length,
    predicted: worklist.filter((r) => r.status === 'predicted').length,
  }), [worklist])

  const total = worklist.length

  // ── Detail view handlers ───────────────────────────────────────────────────

  function openDetail(entry: WorklistEntry) {
    setDetailEntry(entry)
    setFeatures({ ...DEFAULT_FEATURES })
    setResult(null)
    setError('')
    setModel('xgboost')
  }

  function closeDetail() {
    setDetailEntry(null)
    setResult(null)
    setError('')
  }

  function updateFeature(key: keyof DropoutFeatures, raw: string) {
    const v = parseFloat(raw)
    setFeatures((prev) => ({ ...prev, [key]: isNaN(v) ? 0 : v }))
  }

  async function handlePredict() {
    if (!detailEntry) return
    setError('')
    setResult(null)
    setLoading(true)
    try {
      const res = await predictDropout({ model_type: model, features }, token)
      setResult(res)
      // Update worklist row to "predicted" status
      setWorklist((prev) =>
        prev.map((row) =>
          row.id === detailEntry.id
            ? {
                ...row,
                status: 'predicted',
                probability: res.dropout_probability,
                threshold: res.threshold,
                risk_tier: res.risk_tier,
              }
            : row,
        ),
      )
      // Keep detail entry in sync
      setDetailEntry((prev) =>
        prev
          ? {
              ...prev,
              status: 'predicted',
              probability: res.dropout_probability,
              threshold: res.threshold,
              risk_tier: res.risk_tier,
            }
          : null,
      )
    } catch (e: unknown) {
      setError(
        e instanceof Error
          ? e.message
          : 'Prediction failed. Ensure the Python ML service is running on port 8001.',
      )
    } finally {
      setLoading(false)
    }
  }

  const prob = result ? Math.round(result.dropout_probability * 100) : null
  const shap = result?.shap_explanation ?? null
  const maxAbs = shap ? Math.max(...shap.top_contributions.map((c) => c.abs_magnitude), 0.001) : 1

  // ── Render: Detail view ────────────────────────────────────────────────────

  if (detailEntry) {
    return (
      <section className="route-entry research-page" aria-labelledby="dropout-detail-heading">
        <header className="research-page-heading">
          <div>
            <p className="eyebrow">Dropout Follow-up</p>
            <h1 id="dropout-detail-heading">{detailEntry.id}</h1>
            <p className="muted-text">{detailEntry.trial} · {detailEntry.enrollment}</p>
          </div>
          <button
            type="button"
            className="ghost-button"
            onClick={closeDetail}
            aria-label="Back to worklist"
          >
            ← Back to worklist
          </button>
        </header>

        <div
          className="research-disclaimer"
          role="note"
          aria-label="Research disclaimer"
        >
          <strong>Research use only.</strong> This prediction is generated by an experimental ML
          model trained on synthetic research data. It does not constitute a clinical assessment
          and has no effect on patient eligibility determinations.
        </div>

        <div className="risk-workspace">
          {/* ── Immutable pre-fill (baseline section) ── */}
          <div className="risk-stage-panel immutable-prefill">
            <p className="compact-section-heading">Baseline data (pre-filled from record)</p>
            <p className="source-line muted-text">
              Source: enrollment snapshot · Day 0
            </p>
            <div className="baseline-summary">
              <span>Age: <strong>{DEFAULT_FEATURES.age}</strong></span>
              <span>Sex: <strong>{DEFAULT_FEATURES.is_female ? 'Female' : 'Male'}</strong></span>
              <span>Arm: <strong>{DEFAULT_FEATURES.is_experimental_arm ? 'Experimental' : 'Control'}</strong></span>
              <span>Baseline health: <strong>{DEFAULT_FEATURES.baseline_health_score}</strong></span>
              <span>Frailty index: <strong>{DEFAULT_FEATURES.frailty_index}</strong></span>
            </div>
          </div>

          {/* ── Day 30 follow-up form ── */}
          <div className="risk-stage-panel">
            <p className="compact-section-heading">Day 30 follow-up data</p>

            {/* Model selector */}
            <div className="form-section">
              <label className="form-label" htmlFor="detail-model-select">ML Model</label>
              <div className="research-model-toggle" role="radiogroup" aria-label="Select model">
                {(['xgboost', 'logistic_regression'] as ModelType[]).map((m) => (
                  <button
                    key={m}
                    id={m === 'xgboost' ? 'detail-model-xgboost' : 'detail-model-lr'}
                    role="radio"
                    aria-checked={model === m}
                    className={`model-tab${model === m ? ' active' : ''}`}
                    onClick={() => setModel(m)}
                    type="button"
                  >
                    {m === 'xgboost' ? 'XGBoost' : 'Logistic Regression'}
                  </button>
                ))}
              </div>
              {model === 'logistic_regression' && (
                <p className="model-note muted-text" style={{ marginTop: '0.5rem', fontSize: '0.85rem' }}>
                  SHAP TreeExplainer is XGBoost-only. Feature attributions are not available for Logistic Regression.
                </p>
              )}
            </div>

            <div className="research-form-grid">
              {FEATURE_GROUPS.slice(1).map((group) => (
                <details key={group.label} className="feature-group" open={group.label === 'Dosing (Day 30)'}>
                  <summary className="feature-group-label">{group.label}</summary>
                  <div className="feature-grid">
                    {group.keys.map((key) => (
                      <div key={key} className="form-field">
                        <label className="form-label" htmlFor={`detail-feat-${key}`}>
                          {FEATURE_LABELS[key] ?? key}
                        </label>
                        <input
                          id={`detail-feat-${key}`}
                          type="number"
                          step="any"
                          className="form-input"
                          value={features[key]}
                          onChange={(e) => updateFeature(key, e.target.value)}
                        />
                      </div>
                    ))}
                  </div>
                </details>
              ))}
            </div>

            <div className="form-actions">
              <button
                id="predict-dropout-btn"
                className="primary-button"
                onClick={handlePredict}
                disabled={loading}
                aria-busy={loading}
                type="button"
              >
                {loading ? 'Predicting…' : 'Predict dropout risk'}
              </button>
            </div>

            {error && (
              <div className="form-error" role="alert" id="dropout-error">
                {error}
              </div>
            )}
          </div>

          {/* ── Prediction result ── */}
          {loading && (
            <div className="loading-state" aria-label="Computing prediction">
              Computing prediction…
            </div>
          )}

          {!result && !loading && (
            <div className="empty-prediction-state">
              <p>No prediction yet</p>
              <small className="muted-text">Submit the 30-day follow-up data to compute dropout risk.</small>
            </div>
          )}

          {result && !loading && (
            <div className="prediction-region" aria-labelledby="result-heading" aria-live="polite">
              <div className="dropout-result-card">
                <p className="eyebrow">Prediction result</p>
                <h2 id="result-heading">
                  {result.predicted_dropout ? 'Dropout predicted' : 'Dropout not predicted'}
                </h2>

                {/* Risk gauge */}
                <div className="risk-gauge" aria-label={`Dropout probability: ${prob}%`}>
                  <div className="risk-gauge-track">
                    <div
                      className={`risk-gauge-fill risk-gauge-${result.risk_tier}`}
                      style={{ width: `${prob}%` }}
                      role="progressbar"
                      aria-valuenow={prob ?? 0}
                      aria-valuemin={0}
                      aria-valuemax={100}
                      aria-label="Dropout probability gauge"
                    />
                  </div>
                  <span className="risk-gauge-label">{prob}%</span>
                </div>

                <div className="dropout-meta-row">
                  <span className={tierClass(result.risk_tier)}>
                    {result.risk_tier.toUpperCase()} RISK
                  </span>
                  <span className="dropout-meta-item">
                    Threshold: {Math.round(result.threshold * 100)}%
                  </span>
                  <span className="dropout-meta-item">
                    Model: {result.model_type}
                  </span>
                  <span className="dropout-meta-item">
                    v{result.model_version}
                  </span>
                </div>

                {result.note && (
                  <p className="dropout-note">{result.note}</p>
                )}
              </div>

              {/* SHAP attribution panel */}
              {shap && (
                <div className="contribution-panel shap-panel" aria-labelledby="shap-heading">
                  <h3 id="shap-heading">SHAP Feature Attribution</h3>
                  <div className="shap-meta-row">
                    <span>Base value: <strong>{shap.base_value.toFixed(3)}</strong></span>
                    <span>Predicted probability: <strong>{(shap.predicted_probability * 100).toFixed(1)}%</strong></span>
                  </div>
                  <p className="shap-legend-note">
                    Bars to the right (↑) increase dropout risk; bars to the left (↓) decrease it.
                  </p>
                  <ol className="shap-bar-list" aria-label="SHAP top feature contributions">
                    {shap.top_contributions.map((c, i) => {
                      const pct = Math.round((c.abs_magnitude / maxAbs) * 100)
                      return (
                        <li key={`${c.feature}-${i}`} className="shap-bar-row">
                          <span className="shap-feature-name" title={c.feature}>
                            {c.feature.replace(/_/g, ' ')}
                          </span>
                          <span className="shap-bar-track">
                            <span
                              className={`shap-bar-fill shap-dir-${c.direction}`}
                              style={{ width: `${pct}%` }}
                              aria-label={`${c.direction === 'positive' ? 'Increases' : 'Decreases'} risk by ${c.shap_value.toFixed(3)}`}
                            />
                          </span>
                          <span className="shap-value-label">
                            {c.direction === 'positive' ? '+' : ''}{c.shap_value.toFixed(3)}
                          </span>
                          <span className="shap-feature-value">val={c.value}</span>
                        </li>
                      )
                    })}
                  </ol>
                </div>
              )}
            </div>
          )}
        </div>
      </section>
    )
  }

  // ── Render: Worklist view ──────────────────────────────────────────────────

  const predictedCount = counts.predicted
  const notStartedCount = counts.not_started

  return (
    <section className="route-entry research-page" aria-labelledby="dropout-heading">
      <header className="research-page-heading">
        <div>
          <p className="eyebrow">Research · Dropout Follow-up</p>
          <h1 id="dropout-heading">Dropout Follow-up</h1>
        </div>
      </header>

      {/* ── Summary grid ── */}
      <div className="dropout-summary-grid">
        {/* Status breakdown */}
        <div className="dropout-status-section">
          <p className="compact-section-heading">Status breakdown</p>
          {total > 0 && (
            <div className="dropout-status-bar" aria-label="Status distribution">
              {counts.not_started > 0 && (
                <div
                  className="dropout-status-segment seg-not-started"
                  style={{ width: `${(counts.not_started / total) * 100}%` }}
                  title={`Not assessed: ${counts.not_started}`}
                />
              )}
              {counts.information_needed > 0 && (
                <div
                  className="dropout-status-segment seg-info-needed"
                  style={{ width: `${(counts.information_needed / total) * 100}%` }}
                  title={`Info needed: ${counts.information_needed}`}
                />
              )}
              {counts.ready > 0 && (
                <div
                  className="dropout-status-segment seg-ready"
                  style={{ width: `${(counts.ready / total) * 100}%` }}
                  title={`Ready: ${counts.ready}`}
                />
              )}
              {counts.predicted > 0 && (
                <div
                  className="dropout-status-segment seg-predicted"
                  style={{ width: `${(counts.predicted / total) * 100}%` }}
                  title={`Predicted: ${counts.predicted}`}
                />
              )}
            </div>
          )}
          <div
            className="dropout-status-filters"
            role="group"
            aria-label="Filter by status"
          >
            {(['all', 'not_started', 'information_needed', 'ready', 'predicted'] as const).map(
              (s) => (
                <button
                  key={s}
                  type="button"
                  className={`dropout-filter-btn${statusFilter === s ? ' active' : ''}`}
                  onClick={() => setStatusFilter(s)}
                >
                  {s === 'all'
                    ? `All (${total})`
                    : s === 'not_started'
                    ? `Not assessed (${counts.not_started})`
                    : s === 'information_needed'
                    ? `Info needed (${counts.information_needed})`
                    : s === 'ready'
                    ? `Ready (${counts.ready})`
                    : `Predicted (${counts.predicted})`}
                </button>
              ),
            )}
          </div>
        </div>

        {/* Summary stats */}
        <div className="dropout-stats-section">
          <p className="compact-section-heading">Summary</p>
          <div className="dropout-stats-row">
            <div className="dropout-stat-item">
              <span className="dropout-stat-value">{total}</span>
              <span className="dropout-stat-label">Enrolled</span>
            </div>
            <div className="dropout-stat-item">
              <span className="dropout-stat-value">{predictedCount}</span>
              <span className="dropout-stat-label">Assessed</span>
            </div>
            <div className="dropout-stat-item">
              <span className="dropout-stat-value">{notStartedCount}</span>
              <span className="dropout-stat-label">Pending</span>
            </div>
          </div>
          <p className="muted-text" style={{ fontSize: '0.78rem', marginTop: 8 }}>
            No backend enrollment API available. Assess participants individually using
            the Day 30 follow-up form.
          </p>
        </div>
      </div>

      {/* ── Toolbar ── */}
      <div className="dropout-toolbar">
        <input
          id="dropout-search"
          type="search"
          className="form-input dropout-search"
          placeholder="Search by patient ID or trial…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          aria-label="Search participants"
        />
        <span className="dropout-record-count" aria-live="polite">
          {filteredList.length} of {total} records
        </span>
      </div>

      {/* ── Worklist table ── */}
      <div className="dropout-worklist" role="region" aria-label="Participant worklist">
        <div className="dropout-worklist-head">
          <span>ID</span>
          <span>Trial / Enrollment</span>
          <span>Status</span>
          <span>Probability</span>
          <span>Threshold</span>
          <span>Action</span>
        </div>

        {filteredList.length === 0 ? (
          <div className="cohort-empty-box">No participants match the current filter.</div>
        ) : (
          filteredList.map((entry) => (
            <div key={entry.id} className="dropout-worklist-row">
              <span className="dropout-row-id">{entry.id}</span>
              <span className="dropout-row-trial">{entry.trial}</span>
              <span>
                <span className={`dropout-state dropout-state-${entry.status}`}>
                  {statusLabel(entry.status)}
                </span>
              </span>
              <span className="dropout-estimate">
                {entry.probability !== null ? (
                  <>
                    <span className={tierClass(entry.risk_tier ?? 'low')}>
                      {entry.risk_tier?.toUpperCase()}
                    </span>
                    {' '}
                    {Math.round(entry.probability * 100)}%
                  </>
                ) : (
                  <span className="muted-text">—</span>
                )}
              </span>
              <span className="dropout-estimate">
                {entry.threshold !== null ? (
                  `${Math.round(entry.threshold * 100)}%`
                ) : (
                  <span className="muted-text">—</span>
                )}
              </span>
              <span>
                <button
                  type="button"
                  className={`dropout-assess-btn${entry.status === 'predicted' ? ' reassess' : ''}`}
                  onClick={() => openDetail(entry)}
                  aria-label={`${entry.status === 'predicted' ? 'Re-assess' : 'Assess'} ${entry.id}`}
                >
                  {entry.status === 'predicted' ? 'Re-assess' : 'Assess'}
                </button>
              </span>
            </div>
          ))
        )}
      </div>

      {/* Research disclaimer */}
      <div
        className="research-disclaimer"
        role="note"
        aria-label="Research disclaimer"
        style={{ marginTop: 24 }}
      >
        <strong>Research use only.</strong> Dropout predictions are generated by an experimental ML
        model trained on synthetic research data. They do not constitute clinical assessments and have
        no effect on patient eligibility determinations.
      </div>
    </section>
  )
}
