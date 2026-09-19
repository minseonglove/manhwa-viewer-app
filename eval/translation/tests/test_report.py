import json

from translation_eval.report import blind_key, summarize


def test_blind_key_is_deterministic_and_covers_all_providers():
    a = blind_key(["p1", "p2", "p3"], ["luna", "gemma_cf"], seed=1)
    b = blind_key(["p1", "p2", "p3"], ["luna", "gemma_cf"], seed=1)
    assert a == b
    for mapping in a.values():
        assert sorted(mapping.values()) == ["gemma_cf", "luna"] and list(mapping) == ["A", "B"]


def test_summarize_counts_missing_errors_and_cost():
    results = {
        "luna": {
            "p1": {"items": {"t01": "a", "t02": None}, "problems": ["missing ids: t02"], "error": None, "refusal": None,
                    "finish_reason": "stop", "latency_ms": 100, "prompt_tokens": 1_000_000, "completion_tokens": 1_000_000, "reasoning_tokens": 0},
            "p2": {"items": {"t01": None}, "problems": [], "error": "boom", "refusal": None, "finish_reason": None,
                    "latency_ms": 5, "prompt_tokens": None, "completion_tokens": None, "reasoning_tokens": None},
        }
    }
    meta = {"providers": {"luna": {"model": "gpt-5.6-luna"}}}
    row = summarize(results, meta)[-1]
    assert "| luna | gpt-5.6-luna | 2 | 3 | 2 | 1 | 0 | 100 | 1000000 | 1000000 | 0 | - | 1.4000 |" == row
