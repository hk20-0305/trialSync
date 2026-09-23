import { useState } from 'react'
import {
  ragIndexTrial,
  ragRetrieve,
  ragExplain,
  type RagIngestResponse,
  type RagRetrieveResponse,
  type RagExplainResponse,
} from '../api/client'
import { useAuth } from '../auth/AuthContext'

type TabId = 'index' | 'retrieve' | 'explain'

export function ResearchRagPage() {
  const { token } = useAuth()
  const [tab, setTab] = useState<TabId>('index')

  // Shared state
  const [versionId, setVersionId] = useState('')

  // Index
  const [indexLoading, setIndexLoading] = useState(false)
  const [indexError, setIndexError] = useState('')
  const [indexResult, setIndexResult] = useState<RagIngestResponse | null>(null)

  // Retrieve
  const [retrieveQuery, setRetrieveQuery] = useState('')
  const [retrieveTopK, setRetrieveTopK] = useState(5)
  const [retrieveLoading, setRetrieveLoading] = useState(false)
  const [retrieveError, setRetrieveError] = useState('')
  const [retrieveResult, setRetrieveResult] = useState<RagRetrieveResponse | null>(null)

  // Explain
  const [explainQuery, setExplainQuery] = useState('')
  const [explainTopK, setExplainTopK] = useState(5)
  const [explainLoading, setExplainLoading] = useState(false)
  const [explainError, setExplainError] = useState('')
  const [explainResult, setExplainResult] = useState<RagExplainResponse | null>(null)

  async function handleIndex() {
    if (!versionId.trim()) { setIndexError('Trial version ID is required.'); return }
    setIndexError(''); setIndexResult(null); setIndexLoading(true)
    try {
      setIndexResult(await ragIndexTrial(versionId.trim(), token))
    } catch (e: unknown) {
      setIndexError(e instanceof Error ? e.message : 'Indexing failed.')
    } finally {
      setIndexLoading(false)
    }
  }

  async function handleRetrieve() {
    if (!versionId.trim()) { setRetrieveError('Trial version ID is required.'); return }
    if (!retrieveQuery.trim()) { setRetrieveError('Query is required.'); return }
    setRetrieveError(''); setRetrieveResult(null); setRetrieveLoading(true)
    try {
      setRetrieveResult(await ragRetrieve(versionId.trim(), { query: retrieveQuery.trim(), top_k: retrieveTopK }, token))
    } catch (e: unknown) {
      setRetrieveError(e instanceof Error ? e.message : 'Retrieval failed.')
    } finally {
      setRetrieveLoading(false)
    }
  }

  async function handleExplain() {
    if (!versionId.trim()) { setExplainError('Trial version ID is required.'); return }
    if (!explainQuery.trim()) { setExplainError('Query is required.'); return }
    setExplainError(''); setExplainResult(null); setExplainLoading(true)
    try {
      setExplainResult(await ragExplain(versionId.trim(), { query: explainQuery.trim(), top_k: explainTopK }, token))
    } catch (e: unknown) {
      setExplainError(e instanceof Error ? e.message : 'Explanation generation failed.')
    } finally {
      setExplainLoading(false)
    }
  }

  return (
    <section className="route-entry research-page" aria-labelledby="rag-heading">
      <header className="page-heading">
        <div>
          <p className="eyebrow">Research · Trial Criteria Knowledge Base</p>
          <h1 id="rag-heading">Criteria Knowledge Base</h1>
          <p>
            Index approved trial versions, retrieve criteria by similarity, and generate structured
            explanations using LangChain4j + Gemini. Only approved trial versions may be indexed.
          </p>
        </div>
      </header>

      <div
        className="research-disclaimer"
        role="note"
        aria-label="RAG research disclaimer"
        id="rag-disclaimer"
      >
        <strong>Research use only.</strong> RAG explanations are generated for criteria
        comprehension and research purposes only. The deterministic screening engine remains the
        sole authority for actual eligibility decisions. Gemini output cannot alter patient
        eligibility.
      </div>

      {/* Shared trial version ID input */}
      <div className="rag-version-row">
        <div className="form-field rag-version-field">
          <label className="form-label" htmlFor="rag-version-id">
            Approved trial version ID (UUID)
          </label>
          <input
            id="rag-version-id"
            className="form-input"
            type="text"
            placeholder="e.g. b85faff4-a3a3-44ce-bd57-9b8b826d9b0e"
            value={versionId}
            onChange={(e) => setVersionId(e.target.value)}
            aria-describedby="rag-version-hint"
          />
          <small id="rag-version-hint" className="form-hint">
            Find approved version IDs on the Trials page. Only approved versions can be indexed.
          </small>
        </div>
      </div>

      {/* Tab navigation */}
      <div className="rag-tabs" role="tablist" aria-label="RAG operations">
        {(['index', 'retrieve', 'explain'] as TabId[]).map((t) => (
          <button
            key={t}
            id={`rag-tab-${t}`}
            role="tab"
            aria-selected={tab === t}
            aria-controls={`rag-panel-${t}`}
            className={`rag-tab${tab === t ? ' active' : ''}`}
            onClick={() => setTab(t)}
            type="button"
          >
            {t === 'index' ? 'Index' : t === 'retrieve' ? 'Retrieve' : 'Explain'}
          </button>
        ))}
      </div>

      {/* ── INDEX PANEL ─────────────────────────────────────────── */}
      {tab === 'index' && (
        <div id="rag-panel-index" role="tabpanel" aria-labelledby="rag-tab-index" className="rag-panel">
          <p className="rag-panel-desc">
            Embed and store the eligibility criteria for the specified approved trial version
            into the vector store using LangChain4j + all-MiniLM-L6-v2.
          </p>
          <div className="form-actions">
            <button
              id="rag-index-btn"
              className="primary-button"
              onClick={handleIndex}
              disabled={indexLoading}
              aria-busy={indexLoading}
              type="button"
            >
              {indexLoading ? 'Indexing…' : 'Index trial version'}
            </button>
          </div>

          {indexError && (
            <div className="form-error" role="alert" id="rag-index-error">{indexError}</div>
          )}

          {indexResult && (
            <div className="rag-result-card" aria-label="Indexing result" id="rag-index-result">
              <div className="rag-result-header">
                <span className={`rag-status-badge rag-status-${indexResult.status}`}>
                  {indexResult.status.toUpperCase()}
                </span>
                <span className="rag-result-meta">{indexResult.chunk_count} criteria chunks indexed</span>
              </div>
              <p className="rag-result-message">{indexResult.message}</p>
              <dl className="rag-result-dl">
                <dt>Trial version</dt>
                <dd className="provenance-tag">{indexResult.trial_version_id}</dd>
                <dt>Corpus checksum</dt>
                <dd><code>{indexResult.corpus_checksum}</code></dd>
                <dt>Indexed at</dt>
                <dd>{new Date(indexResult.indexed_at).toLocaleString()}</dd>
              </dl>
            </div>
          )}

          {!indexResult && !indexLoading && (
            <div className="empty-state" id="rag-index-empty">
              <h2>Not indexed yet</h2>
              <p>Enter an approved trial version UUID above and click <em>Index trial version</em>.</p>
            </div>
          )}
        </div>
      )}

      {/* ── RETRIEVE PANEL ─────────────────────────────────────── */}
      {tab === 'retrieve' && (
        <div id="rag-panel-retrieve" role="tabpanel" aria-labelledby="rag-tab-retrieve" className="rag-panel">
          <p className="rag-panel-desc">
            Search for eligibility criteria semantically similar to your query. Results are
            strictly scoped to the specified approved trial version.
          </p>
          <div className="rag-query-row">
            <div className="form-field rag-query-field">
              <label className="form-label" htmlFor="retrieve-query">Query</label>
              <input
                id="retrieve-query"
                className="form-input"
                type="text"
                placeholder="e.g. HbA1c ≥ 8% inclusion criteria"
                value={retrieveQuery}
                onChange={(e) => setRetrieveQuery(e.target.value)}
              />
            </div>
            <div className="form-field rag-topk-field">
              <label className="form-label" htmlFor="retrieve-topk">Top K</label>
              <input
                id="retrieve-topk"
                className="form-input"
                type="number"
                min={1}
                max={20}
                value={retrieveTopK}
                onChange={(e) => setRetrieveTopK(parseInt(e.target.value) || 5)}
              />
            </div>
          </div>
          <div className="form-actions">
            <button
              id="rag-retrieve-btn"
              className="primary-button"
              onClick={handleRetrieve}
              disabled={retrieveLoading}
              aria-busy={retrieveLoading}
              type="button"
            >
              {retrieveLoading ? 'Retrieving…' : 'Retrieve criteria'}
            </button>
          </div>

          {retrieveError && (
            <div className="form-error" role="alert" id="rag-retrieve-error">{retrieveError}</div>
          )}

          {retrieveLoading && <div className="loading-state">Retrieving criteria…</div>}

          {retrieveResult && !retrieveLoading && (
            <div aria-label="Retrieval results" id="rag-retrieve-results">
              <p className="rag-result-count">
                {retrieveResult.result_count} criteria returned for query:{' '}
                <em>"{retrieveResult.query}"</em>
              </p>
              {retrieveResult.results.length === 0 ? (
                <div className="empty-state">
                  <h2>No matching criteria</h2>
                  <p>Try a different query or ensure the trial version has been indexed first.</p>
                </div>
              ) : (
                <ol className="rag-criterion-list">
                  {retrieveResult.results.map((r, i) => (
                    <li key={`${r.criterion_id}-${i}`} className="rag-criterion-item">
                      <div className="rag-criterion-header">
                        <span className={`criterion-kind-badge ck-${r.criterion_type}`}>
                          {r.criterion_type}
                        </span>
                        <span className="rag-score">
                          Score: {r.relevance_score.toFixed(3)}
                        </span>
                        <span className="provenance-tag" title="Criterion ID">{r.criterion_id}</span>
                      </div>
                      <p className="rag-criterion-text">{r.source_text}</p>
                    </li>
                  ))}
                </ol>
              )}
            </div>
          )}

          {!retrieveResult && !retrieveLoading && (
            <div className="empty-state" id="rag-retrieve-empty">
              <h2>No results yet</h2>
              <p>Enter a query and click <em>Retrieve criteria</em>.</p>
            </div>
          )}
        </div>
      )}

      {/* ── EXPLAIN PANEL ─────────────────────────────────────── */}
      {tab === 'explain' && (
        <div id="rag-panel-explain" role="tabpanel" aria-labelledby="rag-tab-explain" className="rag-panel">
          <p className="rag-panel-desc">
            Generate a Gemini-powered structured explanation of eligibility criteria relevant
            to your query. Retrieved criteria are used as grounding context.
          </p>
          <div className="rag-query-row">
            <div className="form-field rag-query-field">
              <label className="form-label" htmlFor="explain-query">Query</label>
              <input
                id="explain-query"
                className="form-input"
                type="text"
                placeholder="e.g. What are the age and HbA1c requirements?"
                value={explainQuery}
                onChange={(e) => setExplainQuery(e.target.value)}
              />
            </div>
            <div className="form-field rag-topk-field">
              <label className="form-label" htmlFor="explain-topk">Top K</label>
              <input
                id="explain-topk"
                className="form-input"
                type="number"
                min={1}
                max={20}
                value={explainTopK}
                onChange={(e) => setExplainTopK(parseInt(e.target.value) || 5)}
              />
            </div>
          </div>
          <div className="form-actions">
            <button
              id="rag-explain-btn"
              className="primary-button"
              onClick={handleExplain}
              disabled={explainLoading}
              aria-busy={explainLoading}
              type="button"
            >
              {explainLoading ? 'Generating explanation…' : 'Generate explanation'}
            </button>
          </div>

          {explainError && (
            <div className="form-error" role="alert" id="rag-explain-error">{explainError}</div>
          )}

          {explainLoading && <div className="loading-state">Generating Gemini explanation…</div>}

          {explainResult && !explainLoading && (
            <div className="rag-explain-result" aria-labelledby="rag-explain-heading" id="rag-explain-result">
              <div className="rag-result-header">
                <h2 id="rag-explain-heading">Explanation</h2>
                <span className={`rag-status-badge rag-status-${explainResult.status}`}>
                  {explainResult.status.toUpperCase()}
                </span>
                <span
                  className={`provenance-badge ${explainResult.provenance_valid ? 'prov-valid' : 'prov-invalid'}`}
                  title={explainResult.provenance_valid ? 'All citations grounded in retrieved criteria' : 'Citation provenance could not be fully validated'}
                >
                  {explainResult.provenance_valid ? 'Provenance ✓' : 'Provenance ✗'}
                </span>
                <span className="rag-result-meta">Model: {explainResult.model}</span>
              </div>

              {explainResult.insufficient_evidence && (
                <div className="rag-insufficient" role="alert" id="rag-insufficient">
                  <strong>Insufficient evidence.</strong> The indexed criteria do not contain
                  enough grounding to answer this query reliably. Index the trial version and
                  try a more specific query.
                </div>
              )}

              {!explainResult.insufficient_evidence && (
                <>
                  <div className="rag-summary-block">
                    <h3>Summary</h3>
                    <p>{explainResult.summary}</p>
                  </div>

                  {explainResult.explanations.length > 0 && (
                    <div className="rag-explanations-block">
                      <h3>Criterion Explanations ({explainResult.explanations.length})</h3>
                      <ol className="rag-criterion-list">
                        {explainResult.explanations.map((ex, i) => (
                          <li key={`${ex.criterion_id}-${i}`} className="rag-criterion-item">
                            <div className="rag-criterion-header">
                              <span className={`criterion-kind-badge ck-${ex.criterion_type}`}>
                                {ex.criterion_type}
                              </span>
                              <span className="provenance-tag" title="Criterion ID">
                                {ex.criterion_id}
                              </span>
                            </div>
                            <p className="rag-criterion-text"><em>{ex.source_text}</em></p>
                            <p className="rag-criterion-explanation">{ex.explanation}</p>
                          </li>
                        ))}
                      </ol>
                    </div>
                  )}
                </>
              )}

              <div className="rag-disclaimer-footer" role="note">
                {explainResult.disclaimer}
              </div>
            </div>
          )}

          {!explainResult && !explainLoading && (
            <div className="empty-state" id="rag-explain-empty">
              <h2>No explanation yet</h2>
              <p>
                Index a trial version first, then enter a query and click{' '}
                <em>Generate explanation</em>.
              </p>
            </div>
          )}
        </div>
      )}
    </section>
  )
}
