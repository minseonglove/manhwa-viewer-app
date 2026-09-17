import subprocess

import ocr_eval.runs as runs
from ocr_eval.runs import git_commit


def _init_repo(tmp_path):
    subprocess.run(["git", "init", "-q"], cwd=tmp_path, check=True)
    subprocess.run(["git", "config", "user.email", "test@example.com"], cwd=tmp_path, check=True)
    subprocess.run(["git", "config", "user.name", "test"], cwd=tmp_path, check=True)
    (tmp_path / "data").mkdir()
    (tmp_path / "data" / "tracked.txt").write_text("a", encoding="utf-8")
    subprocess.run(["git", "add", "."], cwd=tmp_path, check=True)
    subprocess.run(["git", "commit", "-q", "-m", "init"], cwd=tmp_path, check=True)
    return tmp_path


def test_clean_repo_has_no_dirty_suffix(tmp_path, monkeypatch):
    repo = _init_repo(tmp_path)
    monkeypatch.setattr(runs, "REPO_ROOT", repo)
    assert not git_commit().endswith("-dirty")


def test_modified_tracked_file_is_dirty(tmp_path, monkeypatch):
    repo = _init_repo(tmp_path)
    monkeypatch.setattr(runs, "REPO_ROOT", repo)
    (repo / "data" / "tracked.txt").write_text("b", encoding="utf-8")
    assert git_commit().endswith("-dirty")


def test_staged_change_is_dirty(tmp_path, monkeypatch):
    repo = _init_repo(tmp_path)
    monkeypatch.setattr(runs, "REPO_ROOT", repo)
    (repo / "data" / "tracked.txt").write_text("c", encoding="utf-8")
    subprocess.run(["git", "add", "."], cwd=repo, check=True)
    assert git_commit().endswith("-dirty")


def test_untracked_file_in_scoped_dir_is_dirty(tmp_path, monkeypatch):
    repo = _init_repo(tmp_path)
    monkeypatch.setattr(runs, "REPO_ROOT", repo)
    (repo / "eval").mkdir()
    (repo / "eval" / "new.py").write_text("x", encoding="utf-8")
    assert git_commit().endswith("-dirty")


def test_untracked_file_outside_scoped_dirs_is_clean(tmp_path, monkeypatch):
    repo = _init_repo(tmp_path)
    monkeypatch.setattr(runs, "REPO_ROOT", repo)
    (repo / ".claude" / "worktrees").mkdir(parents=True)
    (repo / ".claude" / "worktrees" / "new.txt").write_text("x", encoding="utf-8")
    assert not git_commit().endswith("-dirty")
