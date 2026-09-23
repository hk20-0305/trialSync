"""Unit tests for FastAPI ML service (Phase R5)."""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from service import app, load_models_on_startup


@pytest.fixture(scope="module")
def client():
    """Initializes test client with pre-loaded models."""
    load_models_on_startup()
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def valid_features():
    """Sample valid clinical feature dictionary across all 33 features."""
    return {
        "age": 55.0,
        "is_female": 1.0,
        "is_experimental_arm": 1.0,
        "baseline_health_score": 75.0,
        "is_high_travel_burden": 0.0,
        "frailty_index": 0.45,
        "doses_scheduled_pre_cutoff": 5.0,
        "doses_administered_pre_cutoff": 4.0,
        "doses_missed_pre_cutoff": 1.0,
        "doses_reduced_pre_cutoff": 0.0,
        "adherence_ratio_pre_cutoff": 0.8,
        "cumulative_dose_mg_pre_cutoff": 400.0,
        "visits_scheduled_pre_cutoff": 3.0,
        "visits_attended_pre_cutoff": 3.0,
        "visits_missed_pre_cutoff": 0.0,
        "visit_attendance_rate_pre_cutoff": 1.0,
        "ae_count_pre_cutoff": 1.0,
        "ae_max_grade_pre_cutoff": 2.0,
        "ae_serious_count_pre_cutoff": 0.0,
        "ae_burden_score_pre_cutoff": 2.0,
        "has_drug_related_ae_pre_cutoff": 1.0,
        "measurement_count_pre_cutoff": 9.0,
        "abnormal_measurement_count_pre_cutoff": 1.0,
        "abnormal_measurement_rate_pre_cutoff": 0.11,
        "systolic_bp_baseline": 124.0,
        "systolic_bp_latest_pre_cutoff": 128.0,
        "systolic_bp_change_pre_cutoff": 4.0,
        "platelets_baseline": 240.0,
        "platelets_latest_pre_cutoff": 235.0,
        "platelets_pct_change_pre_cutoff": -2.0,
        "alt_baseline": 30.0,
        "alt_latest_pre_cutoff": 34.0,
        "alt_elevation_ratio_pre_cutoff": 0.6,
    }


def test_health_check(client):
    """Validates /health reports UP status and loaded model inventory."""
    resp = client.get("/health")
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "UP"
    assert "xgboost" in data["models_loaded"]
    assert "logistic_regression" in data["models_loaded"]
    assert data["shap_ready"] is True


def test_predict_xgboost_success(client, valid_features):
    """Validates /predict successfully infers risk with XGBoost and returns SHAP attributions."""
    payload = {
        "model_type": "xgboost",
        "features": valid_features,
    }
    resp = client.post("/predict", json=payload)
    assert resp.status_code == 200
    data = resp.json()

    assert 0.0 <= data["dropout_probability"] <= 1.0
    assert data["risk_tier"] in ("LOW", "MEDIUM", "HIGH")
    assert data["model_type"] == "xgboost"
    assert data["model_version"] == "0.1.0-research"
    assert "predicted_at" in data

    # Verify SHAP explanation
    assert data["shap_explanation"] is not None
    assert "base_value" in data["shap_explanation"]
    assert len(data["shap_explanation"]["top_contributions"]) > 0
    first_contrib = data["shap_explanation"]["top_contributions"][0]
    assert first_contrib["direction"] in ("INCREASES_RISK", "DECREASES_RISK", "NEUTRAL")


def test_predict_logistic_regression_success(client, valid_features):
    """Validates /predict works for Logistic Regression and includes informative notes."""
    payload = {
        "model_type": "logistic_regression",
        "features": valid_features,
    }
    resp = client.post("/predict", json=payload)
    assert resp.status_code == 200
    data = resp.json()

    assert 0.0 <= data["dropout_probability"] <= 1.0
    assert data["risk_tier"] in ("LOW", "MEDIUM", "HIGH")
    assert data["model_type"] == "logistic_regression"
    assert data["shap_explanation"] is None
    assert "SHAP TreeExplainer is only supported for XGBoost" in data["note"]


def test_predict_missing_feature_rejected(client, valid_features):
    """Validates that omitting any required feature returns 422 Unprocessable Entity."""
    incomplete = dict(valid_features)
    del incomplete["alt_latest_pre_cutoff"]  # remove required feature

    payload = {
        "model_type": "xgboost",
        "features": incomplete,
    }
    resp = client.post("/predict", json=payload)
    assert resp.status_code == 422
    data = resp.json()
    assert data["code"] == "VALIDATION_ERROR"
    assert any("alt_latest_pre_cutoff" in d["field"] for d in data["details"])


def test_predict_invalid_range_rejected(client, valid_features):
    """Validates that values outside physiological or schema bounds return 422."""
    invalid = dict(valid_features)
    invalid["age"] = -10.0  # Invalid age

    payload = {
        "model_type": "xgboost",
        "features": invalid,
    }
    resp = client.post("/predict", json=payload)
    assert resp.status_code == 422


def test_predict_unsupported_model_type(client, valid_features):
    """Validates that unsupported model names return 422."""
    payload = {
        "model_type": "random_forest",
        "features": valid_features,
    }
    resp = client.post("/predict", json=payload)
    assert resp.status_code == 422
