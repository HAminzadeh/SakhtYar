from __future__ import annotations
import os
from dataclasses import dataclass
from pathlib import Path
from uuid import UUID

PIPELINE_VERSION = "knowledge-preparation-v3"
SUPPORTED = {".pdf", ".txt", ".md", ".html", ".htm", ".json", ".jsonl", ".csv"}

@dataclass(frozen=True)
class PipelineConfig:
    run_id: UUID
    workflow_id: UUID
    correlation_id: UUID
    input_root: Path
    output_root: Path

def db_dsn() -> tuple[str, str, str]:
    url = os.environ.get("DB_URL", "jdbc:postgresql://localhost:5432/sakhtyar")
    user = os.environ.get("DB_USERNAME", "sakhtyar")
    password = os.environ.get("DB_PASSWORD")
    if not password:
        raise RuntimeError("DB_PASSWORD is required; no database password fallback is allowed")
    return url.replace("jdbc:", "", 1), user, password
