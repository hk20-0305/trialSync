"""Model evaluation metrics module for TrialSync Dropout Prediction (Phase R4).

Computes AUROC, AUPRC, precision, recall, F1 score, confusion matrix,
and risk distribution statistics on held-out test partitions.
"""

from __future__ import annotations

from typing import Any, Dict, List, Optional, Tuple, Union
import numpy as np
from sklearn.metrics import (
    average_precision_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)


def compute_binary_classification_metrics(
    y_true: Union[np.ndarray, List[int]],
    y_pred_proba: Union[np.ndarray, List[float]],
    threshold: float = 0.5,
) -> Dict[str, Any]:
    """Computes standard clinical ML discrimination and calibration metrics.

    Args:
        y_true: Ground truth binary labels (0 or 1).
        y_pred_proba: Predicted probability of positive class (dropout).
        threshold: Decision threshold for discrete classification (default: 0.5).

    Returns:
        Dictionary containing auroc, auprc, precision, recall, f1, confusion_matrix,
        threshold used, and sample counts.
    """
    y_true_arr = np.asarray(y_true, dtype=int)
    y_proba_arr = np.asarray(y_pred_proba, dtype=float)
    y_pred_arr = (y_proba_arr >= threshold).astype(int)

    # AUROC
    try:
        auroc = float(roc_auc_score(y_true_arr, y_proba_arr))
    except Exception:
        auroc = 0.0

    # AUPRC (Average Precision)
    try:
        auprc = float(average_precision_score(y_true_arr, y_proba_arr))
    except Exception:
        auprc = 0.0

    # Discrete metric evaluations
    precision = float(precision_score(y_true_arr, y_pred_arr, zero_division=0))
    recall = float(recall_score(y_true_arr, y_pred_arr, zero_division=0))
    f1 = float(f1_score(y_true_arr, y_pred_arr, zero_division=0))

    # Confusion matrix: [[TN, FP], [FN, TP]]
    cm = confusion_matrix(y_true_arr, y_pred_arr).tolist()
    tn, fp, fn, tp = (cm[0][0], cm[0][1], cm[1][0], cm[1][1]) if len(cm) == 2 and len(cm[0]) == 2 else (0, 0, 0, 0)

    # Specificity
    specificity = float(tn / (tn + fp)) if (tn + fp) > 0 else 0.0

    return {
        "auroc": round(auroc, 4),
        "auprc": round(auprc, 4),
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1": round(f1, 4),
        "specificity": round(specificity, 4),
        "confusion_matrix": {
            "matrix": cm,
            "true_negatives": tn,
            "false_positives": fp,
            "false_negatives": fn,
            "true_positives": tp,
        },
        "threshold": threshold,
        "n_samples": len(y_true_arr),
        "n_positives": int(np.sum(y_true_arr == 1)),
        "n_negatives": int(np.sum(y_true_arr == 0)),
    }
