import json

from translation_eval.dataset import Item
from translation_eval.prompt import build_messages, parse_output


def _item(id_, text, ocr=None, category="bubble"):
    return Item(page="p", id=id_, category=category, orientation="vertical", gt=text, ocr=ocr, uncertain=False)


def test_build_messages_uses_selected_source_and_kind():
    items = [_item("t01", "はっ", ocr="は"), _item("t02", "ドン", category="sfx")]
    messages = build_messages(items, "gt")
    assert messages[0]["role"] == "system"
    payload = json.loads(messages[1]["content"])
    assert payload == [{"id": "t01", "kind": "bubble", "text": "はっ"}, {"id": "t02", "kind": "sfx", "text": "ドン"}]
    assert json.loads(build_messages(items[:1], "ocr")[1]["content"])[0]["text"] == "は"


def test_parse_output_accepts_fenced_json_and_reports_missing_ids():
    content = '```json\n{"items": [{"id": "t01", "ko": "헉"}, {"id": "t09", "ko": "?"}]}\n```'
    out, problems = parse_output(content, ["t01", "t02"])
    assert out == {"t01": "헉", "t09": "?"}
    assert problems == ["missing ids: t02", "unexpected ids: t09"]


def test_parse_output_handles_bare_array_and_garbage():
    out, problems = parse_output('[{"id": "t01", "ko": "헉"}]', ["t01"])
    assert out == {"t01": "헉"} and problems == []
    assert parse_output("not json", ["t01"]) == ({}, ["invalid json: Expecting value"])
    assert parse_output(None, ["t01"]) == ({}, ["empty response"])


def test_build_messages_with_image_puts_image_before_text():
    items = [_item("t01", "はっ")]
    messages = build_messages(items, "gt", image_data_url="data:image/jpeg;base64,AAAA")
    assert "page image is attached" in messages[0]["content"]
    content = messages[1]["content"]
    assert content[0] == {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,AAAA"}}
    assert json.loads(content[1]["text"])[0]["id"] == "t01"


def test_v2_uses_tab_lines_and_parses_compact_map():
    items = [_item("t01", "はっ"), _item("t02", "ドン", category="sfx")]
    messages = build_messages(items, "gt", prompt="v2")
    assert messages[1]["content"] == "t01\tbubble\tはっ\nt02\tsfx\tドン"
    out, problems = parse_output('{"t01": "헉", "t02": "쿵"}', ["t01", "t02"])
    assert out == {"t01": "헉", "t02": "쿵"} and problems == []


def test_v2_1_restores_sfx_and_background_emphasis():
    from translation_eval.prompt import PROMPTS
    system = PROMPTS["v2.1"][0]
    assert "never transliterate" in system and "Do not leave Japanese script" in system
    assert "Only if a segment is unreadable garbage" in system
