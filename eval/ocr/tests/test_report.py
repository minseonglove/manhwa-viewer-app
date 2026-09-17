import json

from ocr_eval.gt import GtItem, GtPage
from ocr_eval.report import count_gaps, render_compare, render_report
from ocr_eval.runs import load_run, new_run_dir


def _pages():
    return [GtPage(page="p1", width=1000, height=1000, items=(
        GtItem("t01", (100, 100, 60, 200), "bubble", "vertical", "あいう"),
        GtItem("t02", (500, 500, 60, 200), "sfx", "vertical", "", unreadable=True),
    ))]


def _write_run(tmp_path, name, text):
    run_dir = tmp_path / name
    run_dir.mkdir()
    (run_dir / "meta.json").write_text(json.dumps({"runner": name}), encoding="utf-8")
    (run_dir / "recognition.json").write_text(
        json.dumps({"p1/t01": {"text": text, "ms": 100}}), encoding="utf-8")
    (run_dir / "pipeline.json").write_text(json.dumps(
        {"p1": {"ms": 900, "blocks": [{"bbox": [100, 100, 60, 200], "text": text}]}}), encoding="utf-8")
    return load_run(run_dir)


def test_report_starts_with_denominators(tmp_path):
    markdown, worst = render_report(_write_run(tmp_path, "a", "あいx"), _pages(), skipped_drafts=2)
    first_line = markdown.splitlines()[0]
    assert first_line == ("pages=1 items=2 readable=1 unreadable=1 draft pages skipped=2 | "
                          "recognition predictions=1 missing=0 unknown=0 | pipeline pages=1 missing=0")
    assert [r.key for r in worst] == ["p1/t01"]
    assert "| p1/t01 |" in markdown


def test_report_without_pipeline_section(tmp_path):
    run = _write_run(tmp_path, "a", "あいう")
    (run.path / "pipeline.json").unlink()
    markdown, worst = render_report(load_run(run.path), _pages(), skipped_drafts=0)
    assert "pipeline pages=0" in markdown.splitlines()[0]
    assert "## 파이프라인" not in markdown
    assert worst == []


def test_report_marks_uncertain_items(tmp_path):
    pages = [GtPage(page="p1", width=1000, height=1000, items=(
        GtItem("t01", (100, 100, 60, 200), "bubble", "vertical", "あいう", uncertain=True),
    ))]
    markdown, _ = render_report(_write_run(tmp_path, "a", "あいx"), pages, skipped_drafts=0)
    assert "uncertain 항목(정답 판독이 애매한 항목, 위 표에 포함): 1개, CER 33.33%" in markdown
    assert "| p1/t01 | bubble (uncertain) |" in markdown


def test_compare_lists_improved_items(tmp_path):
    before = _write_run(tmp_path, "before", "あいx")
    after = _write_run(tmp_path, "after", "あいう")
    markdown = render_compare(before, after, _pages(), skipped_drafts=0)
    assert markdown.splitlines()[0] == ("pages=1 items=2 draft pages skipped=0 | "
                                        "A recognition predictions=1 | B recognition predictions=1")
    assert "## 좋아진 항목 (1)" in markdown
    assert "## 나빠진 항목 (0)" in markdown


def test_report_appends_gt_status_when_provided(tmp_path):
    markdown, _ = render_report(_write_run(tmp_path, "a", "あいう"), _pages(), skipped_drafts=0, gt_status="STALE")
    assert markdown.splitlines()[0].endswith(" | gt=STALE")


def test_count_gaps_reports_missing_and_unknown(tmp_path):
    run = _write_run(tmp_path, "a", "あいう")
    (run.path / "recognition.json").write_text(json.dumps({"p9/unknown": {"text": "x", "ms": 1}}), encoding="utf-8")
    run = load_run(run.path)
    assert count_gaps(run, _pages()) == {"missing_recognition": 1, "unknown": 1, "missing_pipeline": 0}


def test_pipeline_table_includes_category_row(tmp_path):
    pages = [GtPage(page="p1", width=1000, height=1000, items=(
        GtItem("t01", (100, 100, 60, 200), "sfx", "vertical", "ドン"),
    ))]
    run_dir = tmp_path / "a"
    run_dir.mkdir()
    (run_dir / "meta.json").write_text(json.dumps({"runner": "a"}), encoding="utf-8")
    (run_dir / "pipeline.json").write_text(json.dumps(
        {"p1": {"ms": 100, "blocks": [{"bbox": [100, 100, 60, 200], "text": "ドン"}]}}), encoding="utf-8")
    markdown, _ = render_report(load_run(run_dir), pages, skipped_drafts=0)
    assert "| category:sfx |" in markdown


def test_report_renders_dash_for_zero_denominator(tmp_path):
    pages = [GtPage(page="p1", width=1000, height=1000, items=(
        GtItem("t01", (100, 100, 60, 200), "sfx", "vertical", "", unreadable=True),
    ))]
    run_dir = tmp_path / "a"
    run_dir.mkdir()
    (run_dir / "meta.json").write_text(json.dumps({"runner": "a"}), encoding="utf-8")
    (run_dir / "pipeline.json").write_text(json.dumps(
        {"p1": {"ms": 100, "blocks": [{"bbox": [100, 100, 60, 200], "text": "ドン"}]}}), encoding="utf-8")
    markdown, _ = render_report(load_run(run_dir), pages, skipped_drafts=0)
    assert "| primary | 0 | — | — | — |" in markdown


def test_new_run_dir_name(tmp_path, monkeypatch):
    from datetime import datetime
    import ocr_eval.runs as runs
    monkeypatch.setattr(runs, "RUNS_DIR", tmp_path)
    path = new_run_dir("mac-reference", now=datetime(2026, 9, 17, 15, 30, 0))
    assert path.name == "20260917-153000-mac-reference"
    assert path.is_dir()
