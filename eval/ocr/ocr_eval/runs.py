from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from ocr_eval.paths import REPO_ROOT, RUNS_DIR


@dataclass(frozen=True)
class Run:
    path: Path
    meta: dict
    recognition: dict[str, dict] | None
    pipeline: dict[str, dict] | None


def _read_optional(path: Path) -> dict | None:
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else None


def load_run(path: Path) -> Run:
    return Run(
        path=path,
        meta=json.loads((path / "meta.json").read_text(encoding="utf-8")),
        recognition=_read_optional(path / "recognition.json"),
        pipeline=_read_optional(path / "pipeline.json"),
    )


def new_run_dir(runner: str, now: datetime | None = None) -> Path:
    stamp = (now or datetime.now()).strftime("%Y%m%d-%H%M%S")
    path = RUNS_DIR / f"{stamp}-{runner}"
    path.mkdir(parents=True, exist_ok=False)
    return path


def write_json(path: Path, data) -> None:
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def git_commit() -> str:
    def git(*args: str) -> subprocess.CompletedProcess:
        return subprocess.run(["git", *args], cwd=REPO_ROOT, capture_output=True, text=True)

    rev = git("rev-parse", "--short", "HEAD")
    if rev.returncode != 0:
        raise RuntimeError(rev.stderr)
    modified = git("diff", "--quiet", "HEAD").returncode != 0
    untracked = git("ls-files", "--others", "--exclude-standard", "--",
                     "data", "domain", "presentation", "android-app", "ios-app", "eval").stdout
    dirty = modified or bool(untracked.strip())
    commit = rev.stdout.strip()
    return f"{commit}-dirty" if dirty else commit
