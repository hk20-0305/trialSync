"""Fixed-Horizon Feature Builder for TrialSync Dropout Prediction (Phase R3).

Extracts leakage-free tabular features strictly from pre-cutoff longitudinal events
(e.g., study day <= 30) to predict participant dropout before an evaluation horizon
(e.g., study day <= 90).
"""

from __future__ import annotations

import csv
import json
import os
from collections import defaultdict
from typing import Any, Dict, List, Optional, Tuple


class FixedHorizonFeatureBuilder:
    """Builds tabular features for fixed-horizon clinical dropout prediction."""

    def __init__(self, observation_cutoff_day: int = 30, prediction_horizon_day: int = 90):
        self.cutoff_day = observation_cutoff_day
        self.horizon_day = prediction_horizon_day

    def build_features_from_records(
        self,
        participants: List[Dict[str, Any]],
        enrollments: List[Dict[str, Any]],
        dose_events: List[Dict[str, Any]],
        visit_events: List[Dict[str, Any]],
        measurements: List[Dict[str, Any]],
        adverse_events: List[Dict[str, Any]],
        outcomes: List[Dict[str, Any]],
    ) -> List[Dict[str, Any]]:
        """Transforms relational longitudinal event records into a flat tabular feature dataset."""
        part_by_id = {p["id"]: p for p in participants}
        outcome_by_enr = {o["enrollment_id"]: o for o in outcomes}

        # Group pre-cutoff events strictly by enrollment_id
        doses_by_enr: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
        for d in dose_events:
            s_day = int(d["scheduled_day"])
            if s_day <= self.cutoff_day:
                doses_by_enr[d["enrollment_id"]].append(d)

        visits_by_enr: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
        for v in visit_events:
            s_day = int(v["scheduled_day"])
            if s_day <= self.cutoff_day:
                visits_by_enr[v["enrollment_id"]].append(v)

        measurements_by_enr: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
        for m in measurements:
            m_day = int(m["measurement_day"])
            if m_day <= self.cutoff_day:
                measurements_by_enr[m["enrollment_id"]].append(m)

        aes_by_enr: Dict[str, List[Dict[str, Any]]] = defaultdict(list)
        for a in adverse_events:
            e_day = int(a["event_day"])
            if e_day <= self.cutoff_day:
                aes_by_enr[a["enrollment_id"]].append(a)

        feature_rows: List[Dict[str, Any]] = []

        for enr in enrollments:
            e_id = enr["id"]
            p_id = enr["participant_id"]
            p = part_by_id.get(p_id, {})
            out = outcome_by_enr.get(e_id, {})

            # 1. Baseline Demographics
            age = int(p.get("age", 2026 - int(p.get("birth_year", 1970))))
            is_female = 1 if p.get("sex") == "FEMALE" else 0
            is_experimental_arm = 1 if enr.get("arm") == "EXPERIMENTAL" else 0
            site_id = p.get("site_id", "SITE-01")

            demographics_raw = p.get("demographics", "{}")
            if isinstance(demographics_raw, str):
                try:
                    demographics_dict = json.loads(demographics_raw)
                except Exception:
                    demographics_dict = {}
            else:
                demographics_dict = demographics_raw or {}

            baseline_health_score = float(demographics_dict.get("baseline_health_score", 75.0))
            is_high_travel_burden = 1 if demographics_dict.get("travel_burden") == "HIGH" else 0
            frailty_index = float(demographics_dict.get("frailty_index", 0.3))

            # 2. Dose & Adherence Features (<= cutoff_day)
            d_list = doses_by_enr.get(e_id, [])
            doses_scheduled = len(d_list)
            doses_administered = sum(1 for d in d_list if d.get("status") == "ADMINISTERED")
            doses_missed = sum(1 for d in d_list if d.get("status") == "MISSED")
            doses_reduced = sum(1 for d in d_list if d.get("status") == "REDUCED")

            total_prescribed = sum(float(d.get("prescribed_dose_amount") or 0) for d in d_list)
            total_actual = sum(float(d.get("actual_dose_amount") or 0) for d in d_list)
            adherence_ratio = (total_actual / total_prescribed) if total_prescribed > 0 else 1.0

            # 3. Visit Adherence Features (<= cutoff_day)
            v_list = visits_by_enr.get(e_id, [])
            visits_scheduled = len(v_list)
            visits_attended = sum(1 for v in v_list if v.get("status") == "ATTENDED")
            visits_missed = sum(1 for v in v_list if v.get("status") == "MISSED")
            visit_attendance_rate = (visits_attended / visits_scheduled) if visits_scheduled > 0 else 1.0

            # 4. Adverse Event Burden (<= cutoff_day)
            a_list = aes_by_enr.get(e_id, [])
            ae_count = len(a_list)
            ae_max_grade = max([int(a.get("ctcae_grade") or 0) for a in a_list], default=0)
            ae_serious_count = sum(1 for a in a_list if str(a.get("is_serious")).lower() in ("true", "1"))
            ae_burden_score = sum(int(a.get("ctcae_grade") or 0) for a in a_list)
            has_drug_related_ae = 1 if any(a.get("relatedness") in ("PROBABLY_RELATED", "DEFINITELY_RELATED") for a in a_list) else 0

            # 5. Measurements & Vitals Dynamics (<= cutoff_day)
            m_list = measurements_by_enr.get(e_id, [])
            measurement_count = len(m_list)
            abnormal_count = sum(1 for m in m_list if str(m.get("is_abnormal")).lower() in ("true", "1"))
            abnormal_measurement_rate = (abnormal_count / measurement_count) if measurement_count > 0 else 0.0

            # Filter by code
            sbp_vals = sorted([(int(m["measurement_day"]), float(m["numeric_value"])) for m in m_list if m.get("code") == "systolic_bp"], key=lambda x: x[0])
            plt_vals = sorted([(int(m["measurement_day"]), float(m["numeric_value"])) for m in m_list if m.get("code") == "platelets"], key=lambda x: x[0])
            alt_vals = sorted([(int(m["measurement_day"]), float(m["numeric_value"])) for m in m_list if m.get("code") == "alt"], key=lambda x: x[0])

            sbp_baseline = sbp_vals[0][1] if sbp_vals else 120.0
            sbp_latest = sbp_vals[-1][1] if sbp_vals else sbp_baseline
            sbp_change = round(sbp_latest - sbp_baseline, 2)

            plt_baseline = plt_vals[0][1] if plt_vals else 250.0
            plt_latest = plt_vals[-1][1] if plt_vals else plt_baseline
            plt_pct_change = round(((plt_latest - plt_baseline) / plt_baseline) * 100.0, 2) if plt_baseline > 0 else 0.0

            alt_baseline = alt_vals[0][1] if alt_vals else 25.0
            alt_latest = alt_vals[-1][1] if alt_vals else alt_baseline
            alt_elevation_ratio = round(alt_latest / 56.0, 3)

            # 6. Target Outcome
            raw_dropout = out.get("dropout_within_horizon", False)
            dropout_within_horizon = 1 if (str(raw_dropout).lower() in ("true", "1") or raw_dropout is True) else 0
            dropout_day = out.get("dropout_day")
            dropout_reason = out.get("dropout_reason") or "NONE"

            row = {
                # Identifiers
                "enrollment_id": e_id,
                "participant_id": p_id,
                "enrollment_code": enr.get("enrollment_code", ""),
                "participant_code": p.get("participant_code", ""),
                "site_id": site_id,
                # Baseline Demographics
                "age": age,
                "is_female": is_female,
                "is_experimental_arm": is_experimental_arm,
                "baseline_health_score": baseline_health_score,
                "is_high_travel_burden": is_high_travel_burden,
                "frailty_index": frailty_index,
                # Dose Adherence (pre-cutoff)
                "doses_scheduled_pre_cutoff": doses_scheduled,
                "doses_administered_pre_cutoff": doses_administered,
                "doses_missed_pre_cutoff": doses_missed,
                "doses_reduced_pre_cutoff": doses_reduced,
                "adherence_ratio_pre_cutoff": round(adherence_ratio, 4),
                "cumulative_dose_mg_pre_cutoff": round(total_actual, 2),
                # Visit Adherence (pre-cutoff)
                "visits_scheduled_pre_cutoff": visits_scheduled,
                "visits_attended_pre_cutoff": visits_attended,
                "visits_missed_pre_cutoff": visits_missed,
                "visit_attendance_rate_pre_cutoff": round(visit_attendance_rate, 4),
                # Adverse Events (pre-cutoff)
                "ae_count_pre_cutoff": ae_count,
                "ae_max_grade_pre_cutoff": ae_max_grade,
                "ae_serious_count_pre_cutoff": ae_serious_count,
                "ae_burden_score_pre_cutoff": ae_burden_score,
                "has_drug_related_ae_pre_cutoff": has_drug_related_ae,
                # Clinical Measurements & Vitals (pre-cutoff)
                "measurement_count_pre_cutoff": measurement_count,
                "abnormal_measurement_count_pre_cutoff": abnormal_count,
                "abnormal_measurement_rate_pre_cutoff": round(abnormal_measurement_rate, 4),
                "systolic_bp_baseline": sbp_baseline,
                "systolic_bp_latest_pre_cutoff": sbp_latest,
                "systolic_bp_change_pre_cutoff": sbp_change,
                "platelets_baseline": plt_baseline,
                "platelets_latest_pre_cutoff": plt_latest,
                "platelets_pct_change_pre_cutoff": plt_pct_change,
                "alt_baseline": alt_baseline,
                "alt_latest_pre_cutoff": alt_latest,
                "alt_elevation_ratio_pre_cutoff": alt_elevation_ratio,
                # Outcome Target
                "dropout_within_horizon": dropout_within_horizon,
                "dropout_day": dropout_day if dropout_day is not None else "",
                "dropout_reason": dropout_reason,
            }

            feature_rows.append(row)

        return feature_rows

    def build_from_csv_dir(self, data_dir: str) -> List[Dict[str, Any]]:
        """Loads CSV files from data_dir and generates the feature dataset."""
        def load_csv(filename: str) -> List[Dict[str, Any]]:
            path = os.path.join(data_dir, filename)
            if not os.path.exists(path):
                return []
            with open(path, "r", encoding="utf-8") as f:
                return list(csv.DictReader(f))

        participants = load_csv("participants.csv")
        enrollments = load_csv("enrollments.csv")
        dose_events = load_csv("dose_events.csv")
        visit_events = load_csv("visit_events.csv")
        measurements = load_csv("measurements.csv")
        adverse_events = load_csv("adverse_events.csv")
        outcomes = load_csv("outcomes.csv")

        return self.build_features_from_records(
            participants=participants,
            enrollments=enrollments,
            dose_events=dose_events,
            visit_events=visit_events,
            measurements=measurements,
            adverse_events=adverse_events,
            outcomes=outcomes,
        )

    def save_features_to_csv(self, features: List[Dict[str, Any]], output_filepath: str) -> str:
        """Writes feature rows to a CSV file."""
        os.makedirs(os.path.dirname(os.path.abspath(output_filepath)), exist_ok=True)
        if not features:
            return output_filepath

        fields = list(features[0].keys())
        with open(output_filepath, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fields)
            writer.writeheader()
            for r in features:
                writer.writerow(r)

        return output_filepath


if __name__ == "__main__":
    current_dir = os.path.dirname(os.path.abspath(__file__))
    data_dir = os.path.abspath(os.path.join(current_dir, "..", "data"))
    output_file = os.path.join(data_dir, "features_horizon_90_cutoff_30.csv")

    builder = FixedHorizonFeatureBuilder(observation_cutoff_day=30, prediction_horizon_day=90)
    features = builder.build_from_csv_dir(data_dir=data_dir)
    saved = builder.save_features_to_csv(features, output_file)
    print(f"Successfully engineered {len(features)} feature rows with {len(features[0]) if features else 0} columns -> {saved}")
