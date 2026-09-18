import json
import sys
import types

import pytest
from PIL import Image

import ocr_eval.run_model as run_model
import ocr_eval.runs as runs
from ocr_eval.fingerprint import gt_fingerprint
from ocr_eval.gt import load_pages, ready_pages
from ocr_eval.runs import write_json


def _write_gt_page(gt_dir, pages_dir):
    gt_dir.mkdir(parents=True, exist_ok=True)
    pages_dir.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", (1125, 1600), "white").save(pages_dir / "city01_p158.jpg")
    write_json(gt_dir / "city01_p158.json", {
        "page": "city01_p158",
        "width": 1125,
        "height": 1600,
        "items": [
            {"id": "t01", "bbox": [200, 300, 100, 150], "category": "bubble", "orientation": "vertical", "text": "あ"},
            {"id": "t02", "bbox": [400, 500, 80, 120], "category": "narration", "orientation": "horizontal", "text": "い"},
        ],
    })


def _register_fake_adapter(name="fake", load=None):
    module = types.ModuleType(f"ocr_eval.models.{name}")
    module.__file__ = __file__  # adapter_sha256 hashes this file's bytes for the fake adapter
    module.load = load or (lambda device: (
        lambda image: "x",
        {"model": "fake/model", "revision": "abc", "device": "mac-cpu", "settings": "none"},
    ))
    sys.modules[f"ocr_eval.models.{name}"] = module
    return module


@pytest.fixture(autouse=True)
def _cleanup_fake_modules():
    yield
    for key in [k for k in sys.modules if k.startswith("ocr_eval.models.fake")]:
        del sys.modules[key]


def test_run_writes_recognition_and_meta(tmp_path, monkeypatch):
    gt_dir = tmp_path / "gt"
    pages_dir = tmp_path / "pages"
    runs_dir = tmp_path / "runs"
    _write_gt_page(gt_dir, pages_dir)
    _register_fake_adapter()

    monkeypatch.setattr(run_model, "GT_DIR", gt_dir)
    monkeypatch.setattr(run_model, "PAGES_DIR", pages_dir)
    monkeypatch.setattr(runs, "RUNS_DIR", runs_dir)
    monkeypatch.setattr(run_model, "git_commit", lambda: "abc1234")

    run_dir = run_model.run("fake", "auto")

    assert run_dir.name.endswith("-mac-fake")

    recognition = json.loads((run_dir / "recognition.json").read_text(encoding="utf-8"))
    assert len(recognition) == 2
    assert all(v["text"] == "x" for v in recognition.values())
    assert all(isinstance(v["ms"], int) for v in recognition.values())

    meta = json.loads((run_dir / "meta.json").read_text(encoding="utf-8"))
    for key in (
        "adapter", "model", "revision", "device", "settings", "commit",
        "gt_fingerprint", "packages", "adapter_sha256",
    ):
        assert key in meta

    assert meta["commit"] == "abc1234"
    assert meta["device"] == "mac-cpu"
    expected_fingerprint = gt_fingerprint(ready_pages(load_pages(gt_dir)), pages_dir)
    assert meta["gt_fingerprint"] == expected_fingerprint

    assert isinstance(meta["packages"], dict)
    assert "pillow" in meta["packages"]

    assert len(meta["adapter_sha256"]) == 16
    int(meta["adapter_sha256"], 16)  # raises ValueError if not hex


def test_run_propagates_adapter_errors_and_creates_no_run_dir(tmp_path, monkeypatch):
    gt_dir = tmp_path / "gt"
    pages_dir = tmp_path / "pages"
    runs_dir = tmp_path / "runs"
    runs_dir.mkdir()
    _write_gt_page(gt_dir, pages_dir)

    def raising_ocr(image):
        raise RuntimeError("boom")

    _register_fake_adapter(name="fake", load=lambda device: (
        raising_ocr,
        {"model": "fake/model", "revision": "abc", "device": "mac-cpu", "settings": "none"},
    ))

    monkeypatch.setattr(run_model, "GT_DIR", gt_dir)
    monkeypatch.setattr(run_model, "PAGES_DIR", pages_dir)
    monkeypatch.setattr(runs, "RUNS_DIR", runs_dir)
    monkeypatch.setattr(run_model, "git_commit", lambda: "abc1234")

    with pytest.raises(RuntimeError, match="boom"):
        run_model.run("fake", "auto")

    assert list(runs_dir.iterdir()) == []


def test_run_raises_when_adapter_info_missing_required_key(tmp_path, monkeypatch):
    gt_dir = tmp_path / "gt"
    pages_dir = tmp_path / "pages"
    runs_dir = tmp_path / "runs"
    runs_dir.mkdir()
    _write_gt_page(gt_dir, pages_dir)
    _register_fake_adapter(load=lambda device: (
        lambda image: "x",
        {"model": "fake/model", "revision": "abc", "device": "mac-cpu"},  # missing "settings"
    ))

    monkeypatch.setattr(run_model, "GT_DIR", gt_dir)
    monkeypatch.setattr(run_model, "PAGES_DIR", pages_dir)
    monkeypatch.setattr(runs, "RUNS_DIR", runs_dir)
    monkeypatch.setattr(run_model, "git_commit", lambda: "abc1234")

    with pytest.raises(ValueError, match="settings"):
        run_model.run("fake", "auto")

    assert list(runs_dir.iterdir()) == []


def test_run_raises_when_adapter_info_collides_with_runner_owned_key(tmp_path, monkeypatch):
    gt_dir = tmp_path / "gt"
    pages_dir = tmp_path / "pages"
    runs_dir = tmp_path / "runs"
    runs_dir.mkdir()
    _write_gt_page(gt_dir, pages_dir)
    _register_fake_adapter(load=lambda device: (
        lambda image: "x",
        {
            "model": "fake/model", "revision": "abc", "device": "mac-cpu", "settings": "none",
            "commit": "should-not-be-here",
        },
    ))

    monkeypatch.setattr(run_model, "GT_DIR", gt_dir)
    monkeypatch.setattr(run_model, "PAGES_DIR", pages_dir)
    monkeypatch.setattr(runs, "RUNS_DIR", runs_dir)
    monkeypatch.setattr(run_model, "git_commit", lambda: "abc1234")

    with pytest.raises(ValueError, match="commit"):
        run_model.run("fake", "auto")

    assert list(runs_dir.iterdir()) == []
