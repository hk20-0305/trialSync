"""Pytest configuration for research-ml test suite."""

import os
import sys

# Ensure research-ml root is on sys.path
RESEARCH_ML_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if RESEARCH_ML_DIR not in sys.path:
    sys.path.insert(0, RESEARCH_ML_DIR)
