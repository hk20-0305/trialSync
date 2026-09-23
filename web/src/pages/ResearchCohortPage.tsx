/**
 * Cohort Atlas page — Phase R10.
 *
 * Connects to the R9 Cohort Atlas backend APIs:
 * - GET  /api/v1/research/cohort/health
 * - GET  /api/v1/research/cohort/summary
 * - GET  /api/v1/research/cohort/projection
 * - GET  /api/v1/research/cohort/clusters
 * - POST /api/v1/research/cohort/nearest
 *
 * Implements:
 * - Header with research-only context and refresh action
 * - High-level summary metrics (participants, dimensions, clusters, noise, DBSCAN params, PCA variance)
 * - Pure vanilla SVG 2D PCA scatter plot (axes, interactive points, noise differentiation, point selection)
 * - Clusters breakdown table with truthful reporting (0 clusters, 400 noise under eps=0.6)
 * - Cluster filtering and pagination for projection data
 * - Nearest-neighbor peer similarity lookup via exact CPU FAISS index
 * - Comprehensive state handling (loading, empty, backend unavailable, invalid participant, errors)
 */

import { useCallback, useEffect, useState } from 'react'
import {
  getCohortClusters,
  getCohortHealth,
  getCohortNearest,
  getCohortProjection,
  getCohortSummary,
  type CohortClusterItem,
  type CohortNearestResponse,
  type CohortProjectionItem,
  type CohortProjectionPageResponse,
  type CohortSummaryResponse,
} from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function ResearchCohortPage() {
  const { token } = useAuth()

  // High-level data
  const [summary, setSummary] = useState<CohortSummaryResponse | null>(null)
  const [clusters, setClusters] = useState<CohortClusterItem[]>([])
  const [projectionData, setProjectionData] = useState<CohortProjectionPageResponse | null>(null)

  // Controls & Pagination
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(50)
  const [clusterFilter, setClusterFilter] = useState<string>('all')

  // Selected participant & Nearest Neighbor state
  const [selectedParticipantId, setSelectedParticipantId] = useState<string>('')
  const [nearestTargetId, setNearestTargetId] = useState<string>('')
  const [nearestK, setNearestK] = useState<number>(5)
  const [nearestResults, setNearestResults] = useState<CohortNearestResponse | null>(null)
  const [nearestLoading, setNearestLoading] = useState(false)
  const [nearestError, setNearestError] = useState<string>('')

  // Global Page States
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>('')

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      // 1. Verify health first
      await getCohortHealth(token)

      // 2. Fetch summary, clusters, and projection in parallel
      const parsedCluster = clusterFilter === 'all' ? null : parseInt(clusterFilter, 10)
      const [sumRes, clustRes, projRes] = await Promise.all([
        getCohortSummary(token),
        getCohortClusters(token),
        getCohortProjection(page, pageSize, parsedCluster, token),
      ])

      setSummary(sumRes)
      setClusters(clustRes)
      setProjectionData(projRes)

      // Default select the first participant if none selected
      if (!selectedParticipantId && projRes.items.length > 0) {
        setSelectedParticipantId(projRes.items[0].participant_id)
        setNearestTargetId(projRes.items[0].participant_id)
      }
    } catch (err: unknown) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Cohort Atlas backend service is currently unavailable. Ensure the Python ML service and Spring Boot backend are running.'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }, [token, page, pageSize, clusterFilter, selectedParticipantId])

  useEffect(() => {
    void loadData()
  }, [loadData])

  async function handleFindNearest(targetId?: string) {
    const idToQuery = (targetId || nearestTargetId || selectedParticipantId).trim()
    if (!idToQuery) {
      setNearestError('Please enter or select a valid participant ID.')
      return
    }

    setNearestLoading(true)
    setNearestError('')
    try {
      const res = await getCohortNearest({ participant_id: idToQuery, k: nearestK }, token)
      setNearestResults(res)
      setSelectedParticipantId(idToQuery)
      setNearestTargetId(idToQuery)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Nearest-neighbor search failed.'
      setNearestError(msg)
      setNearestResults(null)
    } finally {
      setNearestLoading(false)
    }
  }

  function handlePointSelect(item: CohortProjectionItem) {
    setSelectedParticipantId(item.participant_id)
    setNearestTargetId(item.participant_id)
  }

  // --- Vanilla SVG Coordinate Calculations ---
  const points = projectionData?.items ?? []
  const svgWidth = 600
  const svgHeight = 350
  const pad = { top: 25, right: 30, bottom: 45, left: 55 }
  const plotWidth = svgWidth - pad.left - pad.right
  const plotHeight = svgHeight - pad.top - pad.bottom

  let minX = -4
  let maxX = 4
  let minY = -4
  let maxY = 4

  if (points.length > 0) {
    const pc1Vals = points.map((p) => p.pc1)
    const pc2Vals = points.map((p) => p.pc2)
    const rawMinX = Math.min(...pc1Vals)
    const rawMaxX = Math.max(...pc1Vals)
    const rawMinY = Math.min(...pc2Vals)
    const rawMaxY = Math.max(...pc2Vals)
    const spanX = Math.max(rawMaxX - rawMinX, 1)
    const spanY = Math.max(rawMaxY - rawMinY, 1)
    minX = rawMinX - spanX * 0.1
    maxX = rawMaxX + spanX * 0.1
    minY = rawMinY - spanY * 0.1
    maxY = rawMaxY + spanY * 0.1
  }

  const scaleX = (x: number) => pad.left + ((x - minX) / (maxX - minX)) * plotWidth
  const scaleY = (y: number) => pad.top + plotHeight - ((y - minY) / (maxY - minY)) * plotHeight

  // Origin axis positions
  const zeroX = scaleX(0)
  const zeroY = scaleY(0)

  const selectedPoint = points.find((p) => p.participant_id === selectedParticipantId)

  return (
    <section className="route-entry research-page" aria-labelledby="cohort-heading">
      <header className="page-heading">
        <div>
          <p className="eyebrow">Research · Cohort Atlas</p>
          <h1 id="cohort-heading">Cohort Atlas</h1>
          <p>
            Explore unsupervised sub-cohort phenotypes, 2D PCA latent visualization, and FAISS exact
            CPU peer similarity for the synthetic clinical trial cohort.
          </p>
        </div>
        <div className="cohort-header-actions">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => void loadData()}
            disabled={loading}
            aria-label="Refresh cohort data"
          >
            {loading ? 'Refreshing…' : '↻ Refresh'}
          </button>
        </div>
      </header>

      <div
        className="research-disclaimer"
        role="note"
        aria-label="Research disclaimer"
        id="cohort-disclaimer"
      >
        <strong>Research use only.</strong> Unsupervised DBSCAN phenotypes, PCA projections, and
        FAISS nearest-neighbor peer rankings are derived from offline synthetic research data. These
        metrics do not alter patient eligibility determinations. The deterministic screening engine
        remains the sole clinical authority.
      </div>

      {/* Backend Unavailable / Error Banner */}
      {error && (
        <div className="cohort-error-banner" role="alert" id="cohort-error">
          <h3>Backend service unavailable</h3>
          <p>{error}</p>
          <div>
            <button type="button" className="btn btn-primary" onClick={() => void loadData()}>
              Retry Connection
            </button>
          </div>
        </div>
      )}

      {/* Loading state indicator */}
      {loading && !summary && !error && (
        <div className="cohort-card" role="status" aria-live="polite">
          <p style={{ margin: 0, color: 'var(--muted)' }}>Loading Cohort Atlas artifacts…</p>
        </div>
      )}

      {/* Main Content Area */}
      {summary && !error && (
        <>
          {/* Summary Metric Cards */}
          <div className="cohort-summary-grid" id="cohort-summary-metrics">
            <div className="cohort-metric-card">
              <span className="cohort-metric-label">Participants</span>
              <span className="cohort-metric-value">{summary.participant_count}</span>
              <span className="cohort-metric-subtext">Total cohort size</span>
            </div>

            <div className="cohort-metric-card">
              <span className="cohort-metric-label">Features</span>
              <span className="cohort-metric-value">{summary.feature_dimension}</span>
              <span className="cohort-metric-subtext">Clinical feature dimensions</span>
            </div>

            <div className="cohort-metric-card">
              <span className="cohort-metric-label">Clusters</span>
              <span className="cohort-metric-value">{summary.cluster_count}</span>
              <span className="cohort-metric-subtext">DBSCAN dense clusters</span>
            </div>

            <div className="cohort-metric-card">
              <span className="cohort-metric-label">Noise Outliers</span>
              <span className="cohort-metric-value">{summary.noise_count}</span>
              <span className="cohort-metric-subtext">
                {(summary.noise_percentage * 100).toFixed(0)}% of cohort (label -1)
              </span>
            </div>

            <div className="cohort-metric-card">
              <span className="cohort-metric-label">DBSCAN Hyperparameters</span>
              <span className="cohort-metric-value" style={{ fontSize: '1.05rem' }}>
                eps={summary.dbscan_parameters.eps} · min={summary.dbscan_parameters.min_samples}
              </span>
              <span className="cohort-metric-subtext">Metric: {summary.dbscan_parameters.metric}</span>
            </div>

            <div className="cohort-metric-card">
              <span className="cohort-metric-label">PCA Explained Variance</span>
              <span className="cohort-metric-value" style={{ fontSize: '1.05rem' }}>
                {(summary.pca_explained_variance.cumulative_explained_variance * 100).toFixed(1)}%
              </span>
              <span className="cohort-metric-subtext">
                PC1: {(summary.pca_explained_variance.explained_variance_ratio.PC1 * 100).toFixed(1)}% ·
                PC2: {(summary.pca_explained_variance.explained_variance_ratio.PC2 * 100).toFixed(1)}%
              </span>
            </div>
          </div>

          {/* 2-Column Layout: PCA Scatter Plot (Left) + Nearest Neighbors (Right) */}
          <div className="cohort-layout-grid">
            {/* Left: PCA 2D Vanilla SVG Scatter Plot */}
            <div className="cohort-card">
              <div className="cohort-card-header">
                <div>
                  <h2>PCA 2D Visualization</h2>
                  <p style={{ margin: '4px 0 0', fontSize: '0.8rem', color: 'var(--muted)' }}>
                    Displaying {points.length} participants projected onto PC1 and PC2. Click any
                    point to inspect.
                  </p>
                </div>
                <div className="cohort-legend">
                  <div className="cohort-legend-item">
                    <span
                      className="cohort-legend-dot"
                      style={{ background: 'color-mix(in srgb, var(--brand) 85%, transparent)' }}
                    />
                    <span>Cluster Peers</span>
                  </div>
                  <div className="cohort-legend-item">
                    <span
                      className="cohort-legend-dot"
                      style={{ background: 'var(--muted)' }}
                    />
                    <span>Noise Outliers (-1)</span>
                  </div>
                </div>
              </div>

              {/* Selected Point Banner */}
              {selectedPoint && (
                <div className="cohort-selected-banner" role="region" aria-label="Selected participant detail">
                  <div>
                    <strong>Selected Participant:</strong>{' '}
                    <code className="provenance-tag">{selectedPoint.participant_id}</code>{' '}
                    <span>
                      (PC1: {selectedPoint.pc1.toFixed(3)}, PC2: {selectedPoint.pc2.toFixed(3)} ·{' '}
                      {selectedPoint.is_noise ? 'Noise' : `Cluster ${selectedPoint.cluster_label}`})
                    </span>
                  </div>
                  <button
                    type="button"
                    className="btn btn-sm btn-secondary"
                    onClick={() => void handleFindNearest(selectedPoint.participant_id)}
                  >
                    Find Similar Peers
                  </button>
                </div>
              )}

              {/* Vanilla SVG Scatter Canvas */}
              <div className="cohort-svg-wrapper">
                {points.length === 0 ? (
                  <div className="cohort-empty-box">No projection points found for current filter.</div>
                ) : (
                  <svg
                    viewBox={`0 0 ${svgWidth} ${svgHeight}`}
                    className="cohort-svg"
                    role="img"
                    aria-label="2D PCA scatter plot of participants"
                  >
                    {/* Background Gridlines */}
                    <line
                      x1={pad.left}
                      y1={pad.top}
                      x2={pad.left + plotWidth}
                      y2={pad.top}
                      stroke="var(--line)"
                      strokeWidth="1"
                    />
                    <line
                      x1={pad.left}
                      y1={pad.top + plotHeight}
                      x2={pad.left + plotWidth}
                      y2={pad.top + plotHeight}
                      stroke="var(--line)"
                      strokeWidth="1"
                    />
                    <line
                      x1={pad.left}
                      y1={pad.top}
                      x2={pad.left}
                      y2={pad.top + plotHeight}
                      stroke="var(--line)"
                      strokeWidth="1"
                    />
                    <line
                      x1={pad.left + plotWidth}
                      y1={pad.top}
                      x2={pad.left + plotWidth}
                      y2={pad.top + plotHeight}
                      stroke="var(--line)"
                      strokeWidth="1"
                    />

                    {/* Zero-axes if visible */}
                    {zeroX >= pad.left && zeroX <= pad.left + plotWidth && (
                      <line
                        x1={zeroX}
                        y1={pad.top}
                        x2={zeroX}
                        y2={pad.top + plotHeight}
                        stroke="var(--line)"
                        strokeDasharray="3 3"
                        strokeWidth="1.5"
                      />
                    )}
                    {zeroY >= pad.top && zeroY <= pad.top + plotHeight && (
                      <line
                        x1={pad.left}
                        y1={zeroY}
                        x2={pad.left + plotWidth}
                        y2={zeroY}
                        stroke="var(--line)"
                        strokeDasharray="3 3"
                        strokeWidth="1.5"
                      />
                    )}

                    {/* Axis Labels */}
                    <text
                      x={pad.left + plotWidth / 2}
                      y={svgHeight - 10}
                      textAnchor="middle"
                      fontSize="11"
                      fill="var(--muted)"
                      fontWeight="600"
                    >
                      Principal Component 1 (PC1)
                    </text>
                    <text
                      x={16}
                      y={pad.top + plotHeight / 2}
                      textAnchor="middle"
                      fontSize="11"
                      fill="var(--muted)"
                      fontWeight="600"
                      transform={`rotate(-90 16 ${pad.top + plotHeight / 2})`}
                    >
                      Principal Component 2 (PC2)
                    </text>

                    {/* Scatter Points */}
                    {points.map((p) => {
                      const cx = scaleX(p.pc1)
                      const cy = scaleY(p.pc2)
                      const isSelected = p.participant_id === selectedParticipantId
                      const fill = p.is_noise
                        ? 'var(--muted)'
                        : 'color-mix(in srgb, var(--brand) 85%, transparent)'

                      return (
                        <circle
                          key={p.participant_id}
                          cx={cx}
                          cy={cy}
                          r={isSelected ? 6.5 : 4}
                          fill={fill}
                          fillOpacity={isSelected ? 1 : 0.75}
                          stroke={isSelected ? 'var(--ink)' : 'var(--surface)'}
                          strokeWidth={isSelected ? 2.5 : 1}
                          className={`cohort-point ${isSelected ? 'cohort-point-selected' : ''}`}
                          onClick={() => handlePointSelect(p)}
                          role="button"
                          aria-label={`Participant ${p.participant_id}, PC1: ${p.pc1}, PC2: ${p.pc2}`}
                          tabIndex={0}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter' || e.key === ' ') {
                              handlePointSelect(p)
                            }
                          }}
                        >
                          <title>
                            {`Participant: ${p.participant_id}\nPC1: ${p.pc1.toFixed(3)}\nPC2: ${p.pc2.toFixed(3)}\nCluster: ${
                              p.cluster_label
                            } ${p.is_noise ? '(Noise)' : ''}`}
                          </title>
                        </circle>
                      )
                    })}
                  </svg>
                )}
              </div>
            </div>

            {/* Right: FAISS Nearest Neighbors Peer Query */}
            <div className="cohort-card" id="cohort-nearest-panel">
              <div className="cohort-card-header">
                <h3>Nearest Peer Discovery</h3>
                <span className="rag-status-badge rag-status-ok">FAISS IndexFlatL2</span>
              </div>
              <p style={{ margin: 0, fontSize: '0.82rem', color: 'var(--muted)' }}>
                Find the most similar participants in the cohort using exact Euclidean L2 distance on
                the standardized 33-feature clinical space.
              </p>

              <form
                className="cohort-nn-form"
                onSubmit={(e) => {
                  e.preventDefault()
                  void handleFindNearest()
                }}
              >
                <div className="cohort-nn-inputs">
                  <div className="form-field cohort-nn-id-field">
                    <label className="form-label" htmlFor="nearest-participant-id">
                      Target Participant UUID
                    </label>
                    <input
                      id="nearest-participant-id"
                      type="text"
                      className="form-input"
                      value={nearestTargetId}
                      onChange={(e) => setNearestTargetId(e.target.value)}
                      placeholder="e.g. bdd640fb-0667-4ad1-9c80-317fa3b1799d"
                      required
                    />
                  </div>

                  <div className="form-field cohort-nn-k-field">
                    <label className="form-label" htmlFor="nearest-k">
                      k Peers
                    </label>
                    <select
                      id="nearest-k"
                      className="form-input"
                      value={nearestK}
                      onChange={(e) => setNearestK(Number(e.target.value))}
                    >
                      <option value={3}>3</option>
                      <option value={5}>5</option>
                      <option value={10}>10</option>
                      <option value={20}>20</option>
                    </select>
                  </div>

                  <button
                    type="submit"
                    className="btn btn-primary"
                    disabled={nearestLoading || !nearestTargetId.trim()}
                  >
                    {nearestLoading ? 'Searching…' : 'Find Peers'}
                  </button>
                </div>
              </form>

              {/* Nearest Neighbor Inline Error */}
              {nearestError && (
                <div
                  className="cohort-error-banner"
                  role="alert"
                  style={{ padding: '12px 16px', fontSize: '0.83rem' }}
                >
                  <strong style={{ color: 'var(--danger)' }}>Search Error:</strong> {nearestError}
                </div>
              )}

              {/* Nearest Neighbor Results List */}
              {nearestResults && (
                <div>
                  <h4 style={{ margin: '0 0 8px', fontSize: '0.88rem', fontWeight: 600 }}>
                    Top {nearestResults.neighbors.length} Similar Peers for{' '}
                    <code className="provenance-tag">{nearestResults.participant_id}</code>
                  </h4>

                  {nearestResults.neighbors.length === 0 ? (
                    <p style={{ fontSize: '0.82rem', color: 'var(--muted)' }}>No peers returned.</p>
                  ) : (
                    <ul className="cohort-neighbor-list">
                      {nearestResults.neighbors.map((nb) => (
                        <li key={nb.participant_id} className="cohort-neighbor-card">
                          <div className="cohort-neighbor-header">
                            <span className="cohort-neighbor-rank">Rank #{nb.rank}</span>
                            <span className="cohort-neighbor-id">{nb.participant_id}</span>
                            <button
                              type="button"
                              className="btn btn-sm btn-secondary"
                              onClick={() => void handleFindNearest(nb.participant_id)}
                              title="Search neighbors for this peer"
                            >
                              Explore
                            </button>
                          </div>
                          <div className="cohort-neighbor-stats">
                            <span>
                              Distance: <strong>{nb.l2_distance.toFixed(3)}</strong>
                            </span>
                            <span>
                              Similarity:{' '}
                              <strong>{(nb.similarity_score * 100).toFixed(2)}%</strong>
                            </span>
                          </div>
                          <div className="cohort-neighbor-gauge">
                            <div
                              className="cohort-neighbor-gauge-fill"
                              style={{ width: `${Math.min(100, Math.max(5, nb.similarity_score * 100 * 5))}%` }}
                            />
                          </div>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Clusters Table Section */}
          <div className="cohort-card cohort-clusters-section">
            <div className="cohort-card-header">
              <h2>Cluster Phenotypes</h2>
              <span className="cohort-badge-noise">DBSCAN Discovered</span>
            </div>
            <p style={{ margin: 0, fontSize: '0.82rem', color: 'var(--muted)' }}>
              Under the pre-configured density hyperparameters (eps={summary.dbscan_parameters.eps},
              min_samples={summary.dbscan_parameters.min_samples}), all 400 synthetic participant
              trajectories are classified as noise outliers (label -1). The table below truthfully
              displays these cluster assignments.
            </p>

            <table className="cohort-clusters-table">
              <thead>
                <tr>
                  <th>Cluster Label</th>
                  <th>Status</th>
                  <th>Participant Count</th>
                  <th>Share of Cohort</th>
                </tr>
              </thead>
              <tbody>
                {clusters.map((c) => (
                  <tr key={c.cluster_label}>
                    <td>
                      <strong>{c.cluster_label === -1 ? 'Noise / Outlier (-1)' : `Cluster ${c.cluster_label}`}</strong>
                    </td>
                    <td>
                      {c.is_noise ? (
                        <span className="cohort-badge-noise">Noise</span>
                      ) : (
                        <span className="cohort-badge-cluster">Dense Cluster</span>
                      )}
                    </td>
                    <td className="cohort-cell-num">{c.size}</td>
                    <td className="cohort-cell-num">{(c.size_pct * 100).toFixed(1)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Projection Records Table with Pagination & Filtering */}
          <div className="cohort-card">
            <div className="cohort-controls-bar">
              <div className="cohort-controls-group">
                <label htmlFor="cluster-filter-select" className="form-label" style={{ margin: 0 }}>
                  Cluster Filter:
                </label>
                <select
                  id="cluster-filter-select"
                  className="form-input"
                  style={{ width: 'auto', padding: '4px 8px', fontSize: '0.83rem' }}
                  value={clusterFilter}
                  onChange={(e) => {
                    setClusterFilter(e.target.value)
                    setPage(1)
                  }}
                >
                  <option value="all">All Clusters & Noise ({summary.participant_count})</option>
                  {clusters.map((c) => (
                    <option key={c.cluster_label} value={String(c.cluster_label)}>
                      {c.cluster_label === -1 ? `Noise Only (${c.size})` : `Cluster ${c.cluster_label} (${c.size})`}
                    </option>
                  ))}
                </select>
              </div>

              <div className="cohort-controls-group">
                <label htmlFor="page-size-select" className="form-label" style={{ margin: 0 }}>
                  Page Size:
                </label>
                <select
                  id="page-size-select"
                  className="form-input"
                  style={{ width: 'auto', padding: '4px 8px', fontSize: '0.83rem' }}
                  value={pageSize}
                  onChange={(e) => {
                    setPageSize(Number(e.target.value))
                    setPage(1)
                  }}
                >
                  <option value={25}>25</option>
                  <option value={50}>50</option>
                  <option value={100}>100</option>
                </select>

                <button
                  type="button"
                  className="btn btn-sm btn-secondary"
                  disabled={page <= 1}
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  aria-label="Previous page"
                >
                  Previous
                </button>
                <span style={{ fontSize: '0.82rem', color: 'var(--muted)', minWidth: '70px', textAlign: 'center' }}>
                  Page {projectionData?.page ?? 1} of {projectionData?.total_pages ?? 1}
                </span>
                <button
                  type="button"
                  className="btn btn-sm btn-secondary"
                  disabled={!projectionData || page >= projectionData.total_pages}
                  onClick={() => setPage((p) => p + 1)}
                  aria-label="Next page"
                >
                  Next
                </button>
              </div>
            </div>

            {/* Projection Data Table */}
            <div className="cohort-table-responsive">
              <table className="cohort-data-table">
                <thead>
                  <tr>
                    <th>Participant ID</th>
                    <th>PC1</th>
                    <th>PC2</th>
                    <th>Cluster</th>
                    <th>Outlier Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {points.length === 0 ? (
                    <tr>
                      <td colSpan={6} style={{ textAlign: 'center', padding: '24px', color: 'var(--muted)' }}>
                        No participants found matching criteria.
                      </td>
                    </tr>
                  ) : (
                    points.map((pt) => {
                      const isSelected = pt.participant_id === selectedParticipantId
                      return (
                        <tr
                          key={pt.participant_id}
                          style={isSelected ? { background: 'var(--surface-tint)' } : undefined}
                        >
                          <td className="cohort-cell-id">{pt.participant_id}</td>
                          <td className="cohort-cell-num">{pt.pc1.toFixed(4)}</td>
                          <td className="cohort-cell-num">{pt.pc2.toFixed(4)}</td>
                          <td>{pt.cluster_label === -1 ? 'Noise (-1)' : pt.cluster_label}</td>
                          <td>
                            {pt.is_noise ? (
                              <span className="cohort-badge-noise">Noise</span>
                            ) : (
                              <span className="cohort-badge-cluster">Clustered</span>
                            )}
                          </td>
                          <td>
                            <button
                              type="button"
                              className="btn btn-sm btn-secondary"
                              onClick={() => {
                                handlePointSelect(pt)
                                void handleFindNearest(pt.participant_id)
                              }}
                            >
                              Similar Peers
                            </button>
                          </td>
                        </tr>
                      )
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </section>
  )
}
