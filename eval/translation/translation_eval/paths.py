from pathlib import Path

EVAL_ROOT = Path(__file__).resolve().parent.parent
REPO_ROOT = EVAL_ROOT.parent.parent
OCR_EVAL_ROOT = REPO_ROOT / "eval" / "ocr"
GT_DIR = OCR_EVAL_ROOT / "data" / "gt"
PAGES_DIR = OCR_EVAL_ROOT / "data" / "pages"
OCR_RUNS_DIR = OCR_EVAL_ROOT / "runs"
RUNS_DIR = EVAL_ROOT / "runs"
LOCAL_PROPERTIES = REPO_ROOT / "local.properties"
