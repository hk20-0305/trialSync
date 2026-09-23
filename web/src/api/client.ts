import { getApiBaseUrl } from './config'

export type User = { id: string; email: string; display_name: string; is_catalog_admin: boolean }
export type AuthResponse = { access_token: string; token_type: string; user: User }
export type BiologicalSex = 'male' | 'female'
export type Fact = {
  id: string
  patient_id: string
  fact_type: 'condition' | 'medication' | 'observation' | 'demographic'
  concept: string
  value_numeric: string | null
  value_text: string | null
  unit: string | null
  assertion: 'present' | 'absent' | 'unknown'
  effective_date: string | null
  source_label: string
  created_at: string
  updated_at: string
  voided_at?: string | null
  void_reason?: string | null
}
export type PatientFactGroup = 'conditions' | 'medications' | 'observations'
export type PatientFactInputKind = 'status' | 'pregnancy_status' | 'numeric'
export type PatientFactCatalogEntry = {
  key: string
  fact_type: Fact['fact_type']
  concept: string
  display_label: string
  group: PatientFactGroup
  input_kind: PatientFactInputKind
  allowed_assertions: Fact['assertion'][]
  fixed_unit: string | null
  allowed_units: string[]
  effective_date_required: boolean
  screening_supported: boolean
  help_text: string
  terminology_system: string | null
  terminology_code: string | null
  display_order: number
}
export type TerminologySuggestion = {
  source: 'rxnorm' | 'loinc'
  code: string
  display_label: string
  detail: string | null
  fixed_unit: string | null
  score: number | null
}
export type TerminologySuggestionResponse = {
  query: string
  suggestions: TerminologySuggestion[]
  unavailable_sources: string[]
}
export type PatientFactCatalog = {
  version: string
  entries: PatientFactCatalogEntry[]
}
export type ClinicalConcept = {
  id: string
  key: string
  fact_type: 'condition' | 'medication' | 'observation'
  concept: string
  display_label: string
  concept_group: PatientFactGroup
  input_kind: PatientFactInputKind
  allowed_assertions_json: Fact['assertion'][]
  fixed_unit: string | null
  effective_date_required: boolean
  screening_supported: boolean
  help_text: string
  display_order: number
  active: boolean
  created_at: string
  updated_at: string
}
export type ClinicalDetailValue =
  | {
      input_kind: 'status'
      assertion: Fact['assertion']
      effective_date: string | null
    }
  | {
      input_kind: 'pregnancy_status'
      assertion: Fact['assertion']
      effective_date: string
    }
  | {
      input_kind: 'numeric'
      assertion: 'present' | 'unknown'
      value_numeric: number | null
      effective_date: string
    }
export type PatientUnsupportedDetailCategory =
  | 'condition'
  | 'medication'
  | 'observation'
  | 'other'
export type PatientUnsupportedDetail = {
  id: string
  patient_id: string
  category: PatientUnsupportedDetailCategory
  label: string
  context: string | null
  source_label: string
  created_at: string
  updated_at: string
}
export type PatientConsistencyIssue = {
  code:
    | 'PATIENT_PREGNANCY_SEX_CONFLICT'
    | 'PATIENT_SEX_NOT_RECORDED_FOR_PREGNANCY'
  severity: 'conflict' | 'warning'
  message: string
  field: 'sex' | 'pregnancy'
  fact_id: string
}
export type PatientChangeEvent = {
  id: string
  patient_id: string
  actor_id: string
  event_type: string
  entity_type: string
  entity_id: string | null
  reason: string | null
  before_json: Record<string, unknown> | null
  after_json: Record<string, unknown> | null
  created_at: string
}
export type Patient = {
  id: string
  external_id: string
  display_name: string
  date_of_birth: string | null
  sex: BiologicalSex | null
  created_at: string
  updated_at: string
  facts: Fact[]
  unsupported_details: PatientUnsupportedDetail[]
  consistency_issues: PatientConsistencyIssue[]
  activity?: PatientChangeEvent[]
}
export type Criterion = {
  id: string
  kind: 'inclusion' | 'exclusion'
  order: number
  source_text: string
  normalized_rule: Record<string, unknown> | null
  required: boolean
}
export type TrialVersion = {
  id: string
  version: number
  status: 'draft' | 'approved'
  source_text: string | null
  criteria: Criterion[]
}
export type Trial = {
  id: string
  registry_id: string
  title: string
  condition: string
  phase: string | null
  versions: TrialVersion[]
}
export type ScreeningState = 'potentially_eligible' | 'likely_ineligible' | 'needs_review'
export type ScreeningCounts = { pass_count: number; fail_count: number; unknown_count: number }
export type Evidence = {
  fact_id: string
  source_label?: string
  value?: string | number | null
  unit?: string | null
  effective_date?: string | null
}
export type MissingInformation = { fact: string; reason: string; detail: string }
export type SnapshotSummary = {
  id: string
  external_id: string
  display_name: string
  date_of_birth: string | null
  sex: string | null
  facts: Fact[]
}
export type TrialSummary = { registry_id: string; title: string; version: number }
export type CriterionEvaluation = {
  id: string
  criterion_id: string
  criterion_order: number
  criterion_kind: 'inclusion' | 'exclusion'
  result: 'pass' | 'fail' | 'unknown'
  truth: string
  reason_code: string
  criterion_source_text: string
  canonical_explanation: string
  evidence: Evidence[]
  rejected_evidence: Evidence[]
  missing_information: MissingInformation[]
}
export type Screening = {
  id: string
  batch_id: string | null
  patient_snapshot_id: string
  patient_snapshot: SnapshotSummary
  trial_version_id: string
  trial_version: TrialSummary
  overall_state: ScreeningState
  screening_date: string
  engine_version: string
  dsl_version: string
  terminology_version: string
  unit_version: string
  created_at: string
  counts: ScreeningCounts
  evaluations: CriterionEvaluation[]
}
export type BatchPair = {
  patient_snapshot_id: string
  patient_snapshot: SnapshotSummary
  trial_version_id: string
  trial_version: TrialSummary
  screening_id: string
  overall_state: ScreeningState
  counts: ScreeningCounts
}
export type ScreeningBatch = {
  id: string
  label: string | null
  pair_count: number
  created_at: string
  state_counts: Record<ScreeningState, number>
  unknown_criterion_count: number
  screenings: BatchPair[]
}

export type ImportSource = {
  span_id: string | null
  page: number
  start: number
  end: number
  text: string
}
export type PatientImportFact = {
  candidate_id: string
  selected: boolean
  fact_type: Fact['fact_type']
  concept: string
  value_numeric: string | null
  value_text: string | null
  unit: string | null
  assertion: Fact['assertion']
  effective_date: string | null
  source: ImportSource
  warnings: string[]
}
export type TrialImportCriterion = {
  candidate_id: string
  selected: boolean
  kind: Criterion['kind']
  order: number
  source_text: string
  normalized_rule: Record<string, unknown> | null
  parse_state: 'parsed' | 'needs_manual_rule'
  source: ImportSource
  warnings: string[]
}
export type ImportDocument = {
  id: string
  kind: 'patient' | 'trial'
  source_type: 'text' | 'pdf'
  status: 'needs_review' | 'approved' | 'rejected'
  filename: string | null
  mime_type: string
  size_bytes: number
  checksum: string
  source_text: string
  pages: Array<{ page: number; start_offset: number; end_offset: number; text: string }>
  candidates: {
    profile: Record<string, string | null>
    facts?: PatientImportFact[]
    criteria?: TrialImportCriterion[]
  }
  warnings: string[]
  quality: { page_count: number; character_count: number; [key: string]: unknown }
  approved_resource_id: string | null
  created_at: string
}

export type ScreeningChatCitation = {
  criterion_id: string
  evaluation_id: string
  evidence_ids: string[]
  label: string
}
export type ScreeningChatProvider = {
  enabled: boolean
  provider: string
  model: string | null
  prompt_version: string
}
export type ScreeningChatMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
  answer_state: 'supported' | 'insufficient_evidence' | 'refused' | null
  citations: ScreeningChatCitation[]
  provider: ScreeningChatProvider | null
  created_at: string
  suggested_questions: string[]
}
export type ScreeningConversation = {
  screening_id: string
  messages: ScreeningChatMessage[]
  provider: ScreeningChatProvider
  suggested_questions: string[]
  max_messages: number
  max_message_chars: number
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string,
    readonly details?: Array<Record<string, unknown>>,
  ) {
    super(message)
  }
}

async function responseError(response: Response, fallback: string): Promise<ApiError> {
  const body = await response.json().catch(() => null)
  return new ApiError(
    body?.error?.message ?? fallback,
    response.status,
    body?.error?.code ?? 'API_ERROR',
    body?.error?.details,
  )
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  token?: string | null,
): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${getApiBaseUrl()}${path}`, { ...options, headers })
  if (!response.ok) {
    throw await responseError(response, 'The API request failed.')
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export async function apiDownload(
  path: string,
  token?: string | null,
): Promise<Blob> {
  const headers = new Headers()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${getApiBaseUrl()}${path}`, { headers })
  if (!response.ok) {
    throw await responseError(response, 'The download could not be prepared.')
  }
  return response.blob()
}

// ── Research: Dropout Risk ────────────────────────────────────────────────────

/** The 33 pre-cutoff clinical features expected by the Python ML service via Spring Boot. */
export type DropoutFeatures = {
  age: number
  is_female: number
  is_experimental_arm: number
  baseline_health_score: number
  is_high_travel_burden: number
  frailty_index: number
  doses_scheduled_pre_cutoff: number
  doses_administered_pre_cutoff: number
  doses_missed_pre_cutoff: number
  doses_reduced_pre_cutoff: number
  adherence_ratio_pre_cutoff: number
  cumulative_dose_mg_pre_cutoff: number
  visits_scheduled_pre_cutoff: number
  visits_attended_pre_cutoff: number
  visits_missed_pre_cutoff: number
  visit_attendance_rate_pre_cutoff: number
  ae_count_pre_cutoff: number
  ae_max_grade_pre_cutoff: number
  ae_serious_count_pre_cutoff: number
  ae_burden_score_pre_cutoff: number
  has_drug_related_ae_pre_cutoff: number
  measurement_count_pre_cutoff: number
  abnormal_measurement_count_pre_cutoff: number
  abnormal_measurement_rate_pre_cutoff: number
  systolic_bp_baseline: number
  systolic_bp_latest_pre_cutoff: number
  systolic_bp_change_pre_cutoff: number
  platelets_baseline: number
  platelets_latest_pre_cutoff: number
  platelets_pct_change_pre_cutoff: number
  alt_baseline: number
  alt_latest_pre_cutoff: number
  alt_elevation_ratio_pre_cutoff: number
}

export type ShapContribution = {
  feature: string
  value: number
  shap_value: number
  abs_magnitude: number
  direction: string
}

export type ShapExplanation = {
  base_value: number
  predicted_probability: number
  top_contributions: ShapContribution[]
}

export type DropoutPredictionRequest = {
  model_type: 'xgboost' | 'logistic_regression'
  features: DropoutFeatures
}

export type DropoutPredictionResponse = {
  dropout_probability: number
  predicted_dropout: boolean
  risk_tier: string
  model_type: string
  model_version: string
  predicted_at: string
  threshold: number
  shap_explanation: ShapExplanation | null
  note: string | null
}

export async function predictDropout(
  request: DropoutPredictionRequest,
  token?: string | null,
): Promise<DropoutPredictionResponse> {
  return apiRequest<DropoutPredictionResponse>(
    '/research/dropout/predict',
    { method: 'POST', body: JSON.stringify(request) },
    token,
  )
}

// ── Research: Trial Criteria RAG ──────────────────────────────────────────────

export type RagIngestResponse = {
  trial_version_id: string
  status: string
  chunk_count: number
  corpus_checksum: string
  indexed_at: string
  message: string
}

export type RagRetrieveRequest = {
  query: string
  top_k?: number
}

export type RetrievedCriterion = {
  criterion_id: string
  criterion_type: string
  source_text: string
  relevance_score: number
  trial_version_id: string
}

export type RagRetrieveResponse = {
  trial_version_id: string
  query: string
  results: RetrievedCriterion[]
  result_count: number
}

export type CriterionExplanation = {
  criterion_id: string
  criterion_type: string
  source_text: string
  explanation: string
}

export type RagExplainRequest = {
  query: string
  top_k?: number
}

export type RagExplainResponse = {
  run_id: string
  trial_version_id: string
  query: string
  model: string
  status: string
  insufficient_evidence: boolean
  summary: string
  explanations: CriterionExplanation[]
  provenance_valid: boolean
  disclaimer: string
}

export async function ragIndexTrial(
  versionId: string,
  token?: string | null,
): Promise<RagIngestResponse> {
  return apiRequest<RagIngestResponse>(
    `/research/rag/trials/${versionId}/index`,
    { method: 'POST' },
    token,
  )
}

export async function ragRetrieve(
  versionId: string,
  request: RagRetrieveRequest,
  token?: string | null,
): Promise<RagRetrieveResponse> {
  return apiRequest<RagRetrieveResponse>(
    `/research/rag/trials/${versionId}/retrieve`,
    { method: 'POST', body: JSON.stringify(request) },
    token,
  )
}

export async function ragExplain(
  versionId: string,
  request: RagExplainRequest,
  token?: string | null,
): Promise<RagExplainResponse> {
  return apiRequest<RagExplainResponse>(
    `/research/rag/trials/${versionId}/explain`,
    { method: 'POST', body: JSON.stringify(request) },
    token,
  )
}

// ── Research: Cohort Atlas (Phase R10) ────────────────────────────────────────

export type CohortHealthResponse = {
  status: string
  participant_count: number
  faiss_total_indexed: number
  cluster_count: number
}

export type CohortSummaryResponse = {
  participant_count: number
  feature_dimension: number
  cluster_count: number
  noise_count: number
  noise_percentage: number
  dbscan_parameters: {
    eps: number
    min_samples: number
    metric: string
    [key: string]: unknown
  }
  pca_explained_variance: {
    explained_variance_ratio: Record<string, number>
    cumulative_explained_variance: number
  }
  artifact_metadata: Record<string, unknown>
}

export type CohortProjectionItem = {
  participant_id: string
  pc1: number
  pc2: number
  cluster_label: number
  is_noise: boolean
}

export type CohortProjectionPageResponse = {
  total: number
  page: number
  page_size: number
  total_pages: number
  cluster_filter: number | null
  items: CohortProjectionItem[]
}

export type CohortClusterItem = {
  cluster_label: number
  size: number
  size_pct: number
  is_noise: boolean
  means?: Record<string, number>
}

export type CohortClustersResponse = CohortClusterItem[]

export type CohortNearestRequest = {
  participant_id: string
  k?: number
}

export type CohortNeighborItem = {
  rank: number
  participant_id: string
  faiss_index: number
  l2_distance: number
  similarity_score: number
}

export type CohortNearestResponse = {
  participant_id: string
  k: number
  neighbors: CohortNeighborItem[]
}

export async function getCohortHealth(
  token?: string | null,
): Promise<CohortHealthResponse> {
  return apiRequest<CohortHealthResponse>('/research/cohort/health', {}, token)
}

export async function getCohortSummary(
  token?: string | null,
): Promise<CohortSummaryResponse> {
  return apiRequest<CohortSummaryResponse>('/research/cohort/summary', {}, token)
}

export async function getCohortProjection(
  page = 1,
  pageSize = 50,
  clusterLabel?: number | null,
  token?: string | null,
): Promise<CohortProjectionPageResponse> {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize),
  })
  if (clusterLabel !== undefined && clusterLabel !== null) {
    params.set('clusterLabel', String(clusterLabel))
  }
  return apiRequest<CohortProjectionPageResponse>(
    `/research/cohort/projection?${params.toString()}`,
    {},
    token,
  )
}

export async function getCohortClusters(
  token?: string | null,
): Promise<CohortClustersResponse> {
  return apiRequest<CohortClustersResponse>('/research/cohort/clusters', {}, token)
}

export async function getCohortNearest(
  request: CohortNearestRequest,
  token?: string | null,
): Promise<CohortNearestResponse> {
  return apiRequest<CohortNearestResponse>(
    '/research/cohort/nearest',
    { method: 'POST', body: JSON.stringify(request) },
    token,
  )
}

