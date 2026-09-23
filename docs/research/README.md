# TrialSync Research Documentation

This directory contains research-specific design documents, protocols, experimental specifications, and evaluation criteria for the TrialSync research extension.

## Modules

- [dropout/](dropout/README.md): Longitudinal participant enrollment generator, dropout-risk models (Logistic Regression, XGBoost), missed-dose scenario modeling, and SHAP explainability.
- [cohort/](cohort/README.md): Screening-derived patient cohort generation, feature encoding, and DBSCAN unsupervised clustering.
- [similarity/](similarity/README.md): Participant-to-participant vector similarity search using FAISS (fact space and screening-profile space).
- [rag/](rag/README.md): Retrieval-Augmented Generation (RAG) over versioned trial eligibility criteria using LangChain and Gemini structured summary generation.
- [evaluation/](evaluation/README.md): Research evaluation harness, benchmark metrics (Recall@k, MRR, Brier score, ROC-AUC), and validation protocols.
