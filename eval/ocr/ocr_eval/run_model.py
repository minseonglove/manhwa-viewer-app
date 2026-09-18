import argparse
import hashlib
import importlib
import sys
from datetime import datetime
from importlib.metadata import PackageNotFoundError, version
from pathlib import Path

from ocr_eval.fingerprint import gt_fingerprint
from ocr_eval.gt import load_pages, ready_pages
from ocr_eval.paths import GT_DIR, PAGES_DIR
from ocr_eval.run_reference import recognize_pages
from ocr_eval.runs import git_commit, new_run_dir, write_json

# Packages whose versions materially affect model output; recorded per run so a
# run can be tied back to the environment it was measured in.
TRACKED_PACKAGES = ("torch", "transformers", "onnxruntime", "hayai-ocr", "pillow", "numpy")
REQUIRED_ADAPTER_KEYS = ("model", "revision", "device", "settings")
# Keys the runner itself adds to meta.json; an adapter's info dict must not set these.
RUNNER_OWNED_KEYS = ("runner", "adapter", "commit", "gt_fingerprint", "created_at", "packages", "adapter_sha256")


def _installed_packages() -> dict[str, str]:
    packages = {}
    for name in TRACKED_PACKAGES:
        try:
            packages[name] = version(name)
        except PackageNotFoundError:
            pass
    return packages


def _validate_adapter_info(info: dict) -> None:
    missing = [key for key in REQUIRED_ADAPTER_KEYS if key not in info]
    if missing:
        raise ValueError(f"adapter info missing required keys: {missing}")
    collisions = [key for key in RUNNER_OWNED_KEYS if key in info]
    if collisions:
        raise ValueError(f"adapter info must not set runner-owned keys: {collisions}")


def run(adapter_name: str, device: str) -> Path:
    adapter = importlib.import_module(f"ocr_eval.models.{adapter_name}")
    pages = ready_pages(load_pages(GT_DIR))
    if not pages:
        raise SystemExit("no ready ground truth pages")
    ocr, info = adapter.load(device)
    _validate_adapter_info(info)
    results = recognize_pages(pages, PAGES_DIR, ocr)
    expected = sum(len(page.items) for page in pages)
    if len(results) != expected:
        raise SystemExit(f"processed {len(results)} items, expected {expected}")
    run_dir = new_run_dir(f"mac-{adapter_name.replace('_', '-')}")
    write_json(run_dir / "meta.json", {
        "runner": "mac-model",
        "adapter": adapter_name,
        **info,
        "packages": _installed_packages(),
        "adapter_sha256": hashlib.sha256(Path(adapter.__file__).read_bytes()).hexdigest()[:16],
        "commit": git_commit(),
        "gt_fingerprint": gt_fingerprint(pages, PAGES_DIR),
        "created_at": datetime.now().astimezone().isoformat(timespec="seconds"),
    })
    write_json(run_dir / "recognition.json", results)
    return run_dir


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True, help="adapter module name under ocr_eval.models")
    parser.add_argument("--device", choices=["auto", "cpu", "mps"], default="auto")
    args = parser.parse_args()
    print(run(args.model, args.device))
    return 0


if __name__ == "__main__":
    sys.exit(main())
