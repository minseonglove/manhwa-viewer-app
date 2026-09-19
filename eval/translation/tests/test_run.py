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


def test_nmt_provider_translates_page_items_in_one_call(tmp_path, monkeypatch):
    gt = tmp_path / "gt"; gt.mkdir()
    (gt / "p1.json").write_text(json.dumps({"page": "p1", "width": 1, "height": 1, "items": [
        {"id": "t01", "bbox": [0, 0, 1, 1], "category": "bubble", "orientation": "vertical", "text": "はっ", "uncertain": False, "unreadable": False},
        {"id": "t02", "bbox": [0, 0, 1, 1], "category": "bubble", "orientation": "vertical", "text": "うん", "uncertain": False, "unreadable": False}]}), encoding="utf-8")
    runs = tmp_path / "runs"; runs.mkdir()
    monkeypatch.setattr(run_mod, "GT_DIR", gt)
    monkeypatch.setattr(run_mod, "RUNS_DIR", runs)
    monkeypatch.setenv("GOOGLE_TRANSLATE_API_KEY", "gk")
    requests = []

    def fake_translate_nmt(spec, texts):
        requests.append(texts)
        return [f"번역:{t}" for t in texts], run_mod.providers.PageResult(finish_reason="stop", latency_ms=7, settings={"nmt": True, "characters": 4})

    def no_chat_client(spec):
        raise AssertionError("NMT provider must not build a chat client")

    monkeypatch.setattr(run_mod.providers, "translate_nmt", fake_translate_nmt)
    monkeypatch.setattr(run_mod.providers, "make_client", no_chat_client)

    assert run_mod.main(["--providers", "google_nmt", "--source", "gt"]) == 0

    results = json.loads((next(runs.iterdir()) / "results.json").read_text(encoding="utf-8"))
    assert requests == [["はっ", "うん"]]
    assert results["google_nmt"]["p1"]["items"] == {"t01": "번역:はっ", "t02": "번역:うん"}
    assert results["google_nmt"]["p1"]["settings"]["characters"] == 4


def test_segment_workers_option_limits_concurrent_segment_calls(tmp_path, monkeypatch):
    import threading, time
    gt = tmp_path / "gt"; gt.mkdir()
    (gt / "p1.json").write_text(json.dumps({"page": "p1", "width": 1, "height": 1, "items": [
        {"id": f"t{i:02d}", "bbox": [0, 0, 1, 1], "category": "bubble", "orientation": "vertical", "text": f"あ{i}", "uncertain": False, "unreadable": False} for i in range(6)]}), encoding="utf-8")
    runs = tmp_path / "runs"; runs.mkdir()
    monkeypatch.setattr(run_mod, "GT_DIR", gt)
    monkeypatch.setattr(run_mod, "RUNS_DIR", runs)
    monkeypatch.setenv("OPENROUTER_API_KEY", "k")
    lock, state = threading.Lock(), {"now": 0, "peak": 0}

    def fake_segment(client, spec, text):
        with lock:
            state["now"] += 1; state["peak"] = max(state["peak"], state["now"])
        time.sleep(0.02)
        with lock:
            state["now"] -= 1
        return run_mod.providers.PageResult(content="번역:" + text, finish_reason="stop", latency_ms=20, settings={})

    monkeypatch.setattr(run_mod.providers, "translate_segment", fake_segment)
    monkeypatch.setattr(run_mod.providers, "make_client", lambda spec: object())

    assert run_mod.main(["--providers", "hymt2_30b", "--source", "gt", "--segment-workers", "2"]) == 0
    assert state["peak"] == 2
    results = json.loads((next(runs.iterdir()) / "results.json").read_text(encoding="utf-8"))
    assert results["hymt2_30b"]["p1"]["items"]["t05"] == "번역:あ5"
