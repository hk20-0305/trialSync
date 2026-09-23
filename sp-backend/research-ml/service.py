"""FastAPI ML microservice for TrialSync Clinical Dropout Prediction (Phase R5).

Exposes:
- GET  /health: Health check, loaded models, and model version metadata.
- POST /predict: Validated clinical dropout risk inference with SHAP explanations.
"""

from __future__ import annotations

import datetime
from enum import Enum
import os
import sys
from typing import Any, Dict, List, Optional
from fastapi import FastAPI, HTTPException, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

# Ensure research-ml root is on sys.path
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
if CURRENT_DIR not in sys.path:
    sys.path.insert(0, CURRENT_DIR)

from cohort.cohort_service import CohortArtifactStore
from dropout.explainability.shap.explainer import ShapDropoutExplainer
from dropout.inference.predictor import DropoutRiskPredictor
from dropout.models.xgboost.model import FEATURE_COLUMNS


class ModelType(str, Enum):
    XGBOOST = "xgboost"
    LOGISTIC_REGRESSION = "logistic_regression"


class DropoutFeatureSchema(BaseModel):
    """Explicit clinical feature schema matching the 33 pre-cutoff features."""

    model_config = ConfigDict(extra="forbid")

    # Demographics & Baseline (6)
    age: float = Field(..., description="Participant age in years", ge=0, le=120)
    is_female: float = Field(..., description="Binary indicator: 1 if female, 0 otherwise", ge=0, le=1)
    is_experimental_arm: float = Field(..., description="Binary indicator: 1 if experimental arm, 0 for control", ge=0, le=1)
    baseline_health_score: float = Field(..., description="Baseline health score", ge=0, le=100)
    is_high_travel_burden: float = Field(..., description="Binary indicator: 1 if high travel burden", ge=0, le=1)
    frailty_index: float = Field(..., description="Baseline frailty index", ge=0, le=1)

    # Dosing Adherence (6)
    doses_scheduled_pre_cutoff: float = Field(..., description="Doses scheduled up to cutoff", ge=0)
    doses_administered_pre_cutoff: float = Field(..., description="Doses administered up to cutoff", ge=0)
    doses_missed_pre_cutoff: float = Field(..., description="Doses missed up to cutoff", ge=0)
    doses_reduced_pre_cutoff: float = Field(..., description="Doses reduced up to cutoff", ge=0)
    adherence_ratio_pre_cutoff: float = Field(..., description="Dose adherence ratio", ge=0, le=1)
    cumulative_dose_mg_pre_cutoff: float = Field(..., description="Cumulative dose in mg", ge=0)

    # Visit Attendance (4)
    visits_scheduled_pre_cutoff: float = Field(..., description="Visits scheduled up to cutoff", ge=0)
    visits_attended_pre_cutoff: float = Field(..., description="Visits attended up to cutoff", ge=0)
    visits_missed_pre_cutoff: float = Field(..., description="Visits missed up to cutoff", ge=0)
    visit_attendance_rate_pre_cutoff: float = Field(..., description="Visit attendance rate", ge=0, le=1)

    # Adverse Events & Burden (5)
    ae_count_pre_cutoff: float = Field(..., description="Count of adverse events", ge=0)
    ae_max_grade_pre_cutoff: float = Field(..., description="Maximum CTCAE grade observed", ge=0, le=5)
    ae_serious_count_pre_cutoff: float = Field(..., description="Count of serious adverse events", ge=0)
    ae_burden_score_pre_cutoff: float = Field(..., description="Adverse event burden score", ge=0)
    has_drug_related_ae_pre_cutoff: float = Field(..., description="Binary indicator for drug-related AE", ge=0, le=1)

    # Measurements & Trends (12)
    measurement_count_pre_cutoff: float = Field(..., description="Total measurement count", ge=0)
    abnormal_measurement_count_pre_cutoff: float = Field(..., description="Abnormal measurement count", ge=0)
    abnormal_measurement_rate_pre_cutoff: float = Field(..., description="Abnormal measurement rate", ge=0, le=1)
    systolic_bp_baseline: float = Field(..., description="Baseline systolic BP in mmHg", ge=50, le=250)
    systolic_bp_latest_pre_cutoff: float = Field(..., description="Latest pre-cutoff systolic BP", ge=50, le=250)
    systolic_bp_change_pre_cutoff: float = Field(..., description="Change in systolic BP", ge=-100, le=100)
    platelets_baseline: float = Field(..., description="Baseline platelet count (x10^9/L)", ge=0)
    platelets_latest_pre_cutoff: float = Field(..., description="Latest pre-cutoff platelet count", ge=0)
    platelets_pct_change_pre_cutoff: float = Field(..., description="Platelet percentage change", ge=-100, le=500)
    alt_baseline: float = Field(..., description="Baseline ALT in U/L", ge=0)
    alt_latest_pre_cutoff: float = Field(..., description="Latest pre-cutoff ALT in U/L", ge=0)
    alt_elevation_ratio_pre_cutoff: float = Field(..., description="ALT elevation ratio relative to upper normal", ge=0)


class PredictRequest(BaseModel):
    model_type: ModelType = Field(default=ModelType.XGBOOST, description="Model to use for inference")
    features: DropoutFeatureSchema = Field(..., description="Complete 33-feature dictionary")


class ShapContributionItem(BaseModel):
    feature: str
    value: float
    shap_value: float
    abs_magnitude: float
    direction: str


class ShapExplanationResponse(BaseModel):
    base_value: float
    predicted_probability: float
    top_contributions: List[ShapContributionItem]


class PredictResponse(BaseModel):
    dropout_probability: float
    predicted_dropout: bool
    risk_tier: str
    model_type: str
    model_version: str
    predicted_at: str
    threshold: float
    shap_explanation: Optional[ShapExplanationResponse] = None
    note: Optional[str] = None


# --- Cohort Atlas Schemas (Phase R9) ---


class CohortHealthResponse(BaseModel):
    status: str
    participant_count: int
    faiss_total_indexed: int
    cluster_count: int


class PCAExplainedVariance(BaseModel):
    explained_variance_ratio: Dict[str, float]
    cumulative_explained_variance: float


class CohortSummaryResponse(BaseModel):
    participant_count: int
    feature_dimension: int
    cluster_count: int
    noise_count: int
    noise_percentage: float
    dbscan_parameters: Dict[str, Any]
    pca_explained_variance: PCAExplainedVariance
    artifact_metadata: Dict[str, Any]


class ProjectionItem(BaseModel):
    participant_id: str
    pc1: float
    pc2: float
    cluster_label: int
    is_noise: bool


class CohortProjectionResponse(BaseModel):
    total: int
    page: int
    page_size: int
    total_pages: int
    cluster_filter: Optional[int] = None
    items: List[ProjectionItem]


class ClusterSummaryItem(BaseModel):
    cluster_label: int
    size: int
    size_pct: float
    is_noise: bool
    means: Dict[str, float] = {}


class NearestNeighborQuery(BaseModel):
    participant_id: str = Field(..., description="Target participant ID")
    k: int = Field(default=5, ge=1, le=50, description="Number of nearest neighbors to retrieve (1-50)")


class NeighborItem(BaseModel):
    rank: int
    participant_id: str
    faiss_index: int
    l2_distance: float
    similarity_score: float


class NearestNeighborResponse(BaseModel):
    participant_id: str
    k: int
    neighbors: List[NeighborItem]


# Initialize FastAPI App
app = FastAPI(
    title="TrialSync Research ML Service",
    description="Microservice providing dropout probability prediction, SHAP explainability, and Cohort Atlas discovery.",
    version="0.2.0-research",
)

# Global model & artifact caches (loaded once at startup)
MODEL_CACHE: Dict[str, DropoutRiskPredictor] = {}
SHAP_EXPLAINER: Optional[ShapDropoutExplainer] = None
COHORT_STORE: Optional[CohortArtifactStore] = None


@app.on_event("startup")
def load_models_on_startup():
    """Pre-loads serialized models, SHAP explainer, and Cohort Atlas artifacts at application startup."""
    global SHAP_EXPLAINER, COHORT_STORE
    models_dir = os.path.abspath(os.path.join(CURRENT_DIR, "dropout", "models"))
    lr_dir = os.path.join(models_dir, "logistic_regression")
    xgb_dir = os.path.join(models_dir, "xgboost")

    try:
        MODEL_CACHE["logistic_regression"] = DropoutRiskPredictor(
            model_type="logistic_regression", model_dir=lr_dir
        )
        MODEL_CACHE["xgboost"] = DropoutRiskPredictor(
            model_type="xgboost", model_dir=xgb_dir
        )
        SHAP_EXPLAINER = ShapDropoutExplainer(model_dir=xgb_dir)
        print("[ML Service] Successfully loaded Logistic Regression, XGBoost, and SHAP TreeExplainer.")
    except Exception as e:
        print(f"[ML Service] Error pre-loading models: {e}", file=sys.stderr)

    try:
        cohort_store = CohortArtifactStore()
        cohort_store.load_artifacts()
        COHORT_STORE = cohort_store
        print(f"[ML Service] Successfully loaded Cohort Atlas artifacts ({len(cohort_store.projection_records)} participants indexed).")
    except Exception as e:
        print(f"[ML Service] Error pre-loading Cohort Atlas artifacts: {e}", file=sys.stderr)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request, exc: RequestValidationError):
    """Returns structured 422 error detailing missing or invalid features."""
    errors = []
    for err in exc.errors():
        field_path = " -> ".join(str(loc) for loc in err.get("loc", []))
        errors.append({"field": field_path, "message": err.get("msg"), "type": err.get("type")})
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={"code": "VALIDATION_ERROR", "message": "Invalid feature schema or payload", "details": errors},
    )


@app.get("/health")
def health_check():
    """Health check verifying model and cohort readiness."""
    models_loaded = list(MODEL_CACHE.keys())
    is_ready = ("xgboost" in MODEL_CACHE) and ("logistic_regression" in MODEL_CACHE) and (SHAP_EXPLAINER is not None)
    cohort_ready = COHORT_STORE is not None and COHORT_STORE.is_loaded
    return {
        "status": "UP" if is_ready else "INITIALIZING",
        "service": "trialsync-research-ml",
        "version": "0.2.0-research",
        "models_loaded": models_loaded,
        "shap_ready": SHAP_EXPLAINER is not None,
        "cohort_ready": cohort_ready,
    }


# --- Cohort Atlas API Endpoints (Phase R9) ---


@app.get("/cohort/health", response_model=CohortHealthResponse)
def cohort_health():
    """Returns health and readiness status of the Cohort Atlas artifact store."""
    if COHORT_STORE is None or not COHORT_STORE.is_loaded:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Cohort Atlas artifacts are not loaded.",
        )
    return COHORT_STORE.get_health()


@app.get("/cohort/summary", response_model=CohortSummaryResponse)
def cohort_summary():
    """Returns dataset summary, DBSCAN parameters, PCA variance, and artifact metadata."""
    if COHORT_STORE is None or not COHORT_STORE.is_loaded:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Cohort Atlas artifacts are not loaded.",
        )
    return COHORT_STORE.get_summary()


@app.get("/cohort/projection", response_model=CohortProjectionResponse)
def cohort_projection(
    page: int = 1,
    page_size: int = 50,
    cluster_label: Optional[int] = None,
):
    """Returns 2D PCA projection coordinates with optional cluster filtering and pagination."""
    if COHORT_STORE is None or not COHORT_STORE.is_loaded:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Cohort Atlas artifacts are not loaded.",
        )
    if page < 1:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Page must be greater than or equal to 1.",
        )
    if page_size < 1 or page_size > 500:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Page size must be between 1 and 500.",
        )
    return COHORT_STORE.get_projection(
        page=page, page_size=page_size, cluster_label=cluster_label
    )


@app.get("/cohort/clusters", response_model=List[ClusterSummaryItem])
def cohort_clusters():
    """Returns cluster profiles, participant counts, noise flag, and feature means."""
    if COHORT_STORE is None or not COHORT_STORE.is_loaded:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Cohort Atlas artifacts are not loaded.",
        )
    return COHORT_STORE.get_clusters()


@app.post("/cohort/nearest", response_model=NearestNeighborResponse)
def cohort_nearest(query: NearestNeighborQuery):
    """Finds top-k nearest participant peers using the exact CPU FAISS index."""
    if COHORT_STORE is None or not COHORT_STORE.is_loaded:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Cohort Atlas artifacts are not loaded.",
        )
    try:
        return COHORT_STORE.get_nearest_neighbors(
            participant_id=query.participant_id, k=query.k
        )
    except KeyError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(e),
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Nearest-neighbor search failed: {str(e)}",
        )



@app.post("/predict", response_model=PredictResponse)
def predict_dropout(req: PredictRequest):
    """Infers dropout probability and computes local SHAP explanations."""
    model_key = req.model_type.value
    if model_key not in MODEL_CACHE:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Model '{model_key}' is not available in loaded registry.",
        )

    predictor = MODEL_CACHE[model_key]
    feature_dict = req.features.model_dump()

    # Inference
    try:
        prediction_result = predictor.predict_record(feature_dict)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Inference execution failed: {str(e)}",
        )

    proba = prediction_result["dropout_probability"]
    tier = prediction_result["risk_tier"]
    pred = prediction_result["predicted_dropout"]
    now_iso = datetime.datetime.now(datetime.timezone.utc).isoformat()

    shap_resp: Optional[ShapExplanationResponse] = None
    note: Optional[str] = None

    if req.model_type == ModelType.XGBOOST and SHAP_EXPLAINER is not None:
        try:
            local_exp = SHAP_EXPLAINER.explain_local_prediction(feature_dict, top_k=8)
            shap_resp = ShapExplanationResponse(
                base_value=local_exp["base_value"],
                predicted_probability=local_exp["predicted_probability"],
                top_contributions=[
                    ShapContributionItem(**item) for item in local_exp["top_contributions"]
                ],
            )
        except Exception as e:
            note = f"SHAP explanation computation failed: {str(e)}"
    elif req.model_type == ModelType.LOGISTIC_REGRESSION:
        note = "SHAP TreeExplainer is only supported for XGBoost. Logistic Regression provides global coefficients via model metadata."

    return PredictResponse(
        dropout_probability=proba,
        predicted_dropout=pred,
        risk_tier=tier,
        model_type=model_key,
        model_version="0.1.0-research",
        predicted_at=now_iso,
        threshold=0.5,
        shap_explanation=shap_resp,
        note=note,
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
