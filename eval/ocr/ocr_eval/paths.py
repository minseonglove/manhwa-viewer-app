from pathlib import Path

EVAL_ROOT = Path(__file__).resolve().parent.parent
REPO_ROOT = EVAL_ROOT.parent.parent
DATA_DIR = EVAL_ROOT / "data"
VOLUMES_DIR = DATA_DIR / "volumes"
PAGES_DIR = DATA_DIR / "pages"
PROPOSALS_DIR = DATA_DIR / "proposals"
REVIEW_DIR = DATA_DIR / "review"
GT_DIR = DATA_DIR / "gt"
RUNS_DIR = EVAL_ROOT / "runs"
