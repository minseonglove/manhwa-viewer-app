import json

from translation_eval import run as run_mod


def test_into_merges_provider_results(tmp_path, monkeypatch):
    gt = tmp_path / "gt"; gt.mkdir()
    (gt / "p1.json").write_text(json.dumps({"page": "p1", "width": 1, "height": 1, "items": [
        {"id": "t01", "bbox": [0, 0, 1, 1], "category": "bubble", "orientation": "vertical", "text": "はっ", "uncertain": False, "unreadable": False}]}), encoding="utf-8")
    runs = tmp_path / "runs"; runs.mkdir()
    monkeypatch.setattr(run_mod, "GT_DIR", gt)
    monkeypatch.setattr(run_mod, "RUNS_DIR", runs)

    assert run_mod.main(["--dry-run", "--providers", "gemma_cf", "--source", "gt"]) == 0
    run_dir = next(runs.iterdir())
    assert run_mod.main(["--dry-run", "--providers", "luna", "--source", "gt", "--into", str(run_dir)]) == 0

    results = json.loads((run_dir / "results.json").read_text(encoding="utf-8"))
    meta = json.loads((run_dir / "meta.json").read_text(encoding="utf-8"))
    assert set(results) == {"gemma_cf", "luna"} and set(meta["providers"]) == {"gemma_cf", "luna"}
    assert results["luna"]["p1"]["items"]["t01"].startswith("[dry]")
    assert run_mod.main(["--dry-run", "--providers", "luna", "--source", "ocr", "--into", str(run_dir)]) == 2
    assert run_mod.main(["--dry-run", "--providers", "luna", "--source", "gt", "--prompt", "v1", "--into", str(run_dir)]) == 2
