"""Unit and integration tests for FastAPI Cohort Atlas endpoints (Phase R9)."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from cohort.cohort_service import CohortArtifactStore
import service
from service import app, load_models_on_startup


@pytest.fixture(scope="module")
def client():
    """Initializes test client with pre-loaded models and cohort artifacts."""
    load_models_on_startup()
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture(scope="module")
def sample_participant_id():
    """Extracts the first valid participant ID directly from the loaded store."""
    if service.COHORT_STORE and service.COHORT_STORE.projection_records:
        return service.COHORT_STORE.projection_records[0]["participant_id"]
    return "bdd640fb-0667-4ad1-9c80-317fa3b1799d"


def test_cohort_health(client):
    """Validates /cohort/health reports UP status and loaded index metrics."""
    resp = client.get("/cohort/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "UP"
    assert data["participant_count"] == 400
    assert data["faiss_total_indexed"] == 400
    assert "cluster_count" in data


def test_cohort_summary(client):
    """Validates /cohort/summary returns all required dataset and metadata metrics."""
    resp = client.get("/cohort/summary")
    assert resp.status_code == 200
    data = resp.json()

    assert data["participant_count"] == 400
    assert data["feature_dimension"] == 33
    assert data["noise_count"] == 400
    assert data["cluster_count"] == 0

    # DBSCAN parameters
    assert "dbscan_parameters" in data
    dbscan_params = data["dbscan_parameters"]
    assert dbscan_params["eps"] == 0.6
    assert dbscan_params["min_samples"] == 10
    assert dbscan_params["metric"] == "euclidean"

    # PCA explained variance
    assert "pca_explained_variance" in data
    pca_var = data["pca_explained_variance"]
    assert "PC1" in pca_var["explained_variance_ratio"]
    assert "PC2" in pca_var["explained_variance_ratio"]
    assert pca_var["cumulative_explained_variance"] > 0.0

    # Artifact metadata
    assert "artifact_metadata" in data
    assert data["artifact_metadata"]["faiss_index_type"] == "IndexFlatL2"
    assert data["artifact_metadata"]["faiss_metric"] == "L2_EUCLIDEAN"


def test_cohort_projection_default_pagination(client):
    """Validates /cohort/projection returns paginated 2D coordinates."""
    resp = client.get("/cohort/projection")
    assert resp.status_code == 200
    data = resp.json()

    assert data["total"] == 400
    assert data["page"] == 1
    assert data["page_size"] == 50
    assert data["total_pages"] == 8
    assert len(data["items"]) == 50

    item = data["items"][0]
    assert "participant_id" in item
    assert "pc1" in item
    assert "pc2" in item
    assert "cluster_label" in item
    assert "is_noise" in item
    assert isinstance(item["pc1"], float)
    assert isinstance(item["pc2"], float)


def test_cohort_projection_cluster_filter(client):
    """Validates filtering projection items by cluster label."""
    resp = client.get("/cohort/projection?cluster_label=-1&page=1&page_size=20")
    assert resp.status_code == 200
    data = resp.json()

    assert data["total"] == 400
    assert data["cluster_filter"] == -1
    assert len(data["items"]) == 20
    for it in data["items"]:
        assert it["cluster_label"] == -1
        assert it["is_noise"] is True


def test_cohort_projection_invalid_pagination(client):
    """Validates 422 error on invalid page or page_size values."""
    resp = client.get("/cohort/projection?page=0")
    assert resp.status_code == 422

    resp2 = client.get("/cohort/projection?page_size=0")
    assert resp2.status_code == 422

    resp3 = client.get("/cohort/projection?page_size=1000")
    assert resp3.status_code == 422


def test_cohort_clusters(client):
    """Validates /cohort/clusters returns cluster breakdown and profile stats."""
    resp = client.get("/cohort/clusters")
    assert resp.status_code == 200
    data = resp.json()

    assert isinstance(data, list)
    assert len(data) >= 1
    noise_cluster = next((c for c in data if c["cluster_label"] == -1), None)
    assert noise_cluster is not None
    assert noise_cluster["size"] == 400
    assert noise_cluster["size_pct"] == 1.0
    assert noise_cluster["is_noise"] is True
    assert "means" in noise_cluster
    assert len(noise_cluster["means"]) == 33


def test_cohort_nearest_success(client, sample_participant_id):
    """Validates /cohort/nearest returns top-k nearest peers excluding query participant."""
    payload = {
        "participant_id": sample_participant_id,
        "k": 5,
    }
    resp = client.post("/cohort/nearest", json=payload)
    assert resp.status_code == 200
    data = resp.json()

    assert data["participant_id"] == sample_participant_id
    assert data["k"] == 5
    assert len(data["neighbors"]) == 5

    for rank, neighbor in enumerate(data["neighbors"], start=1):
        assert neighbor["rank"] == rank
        assert neighbor["participant_id"] != sample_participant_id
        assert neighbor["l2_distance"] >= 0.0
        assert 0.0 <= neighbor["similarity_score"] <= 1.0
        assert "faiss_index" in neighbor


def test_cohort_nearest_participant_not_found(client):
    """Validates 404 response when querying a non-existent participant ID."""
    payload = {
        "participant_id": "00000000-0000-0000-0000-000000000000",
        "k": 3,
    }
    resp = client.post("/cohort/nearest", json=payload)
    assert resp.status_code == 404
    assert "not present in cohort index" in resp.json()["detail"]


def test_cohort_nearest_invalid_k(client, sample_participant_id):
    """Validates 422 error for k <= 0 or k > 50."""
    resp1 = client.post("/cohort/nearest", json={"participant_id": sample_participant_id, "k": 0})
    assert resp1.status_code == 422

    resp2 = client.post("/cohort/nearest", json={"participant_id": sample_participant_id, "k": 100})
    assert resp2.status_code == 422


def test_cohort_endpoints_503_when_uninitialized(client, monkeypatch):
    """Validates 503 response if cohort store is not loaded."""
    monkeypatch.setattr(service, "COHORT_STORE", None)

    assert client.get("/cohort/health").status_code == 503
    assert client.get("/cohort/summary").status_code == 503
    assert client.get("/cohort/projection").status_code == 503
    assert client.get("/cohort/clusters").status_code == 503
    assert client.post("/cohort/nearest", json={"participant_id": "any", "k": 5}).status_code == 503
