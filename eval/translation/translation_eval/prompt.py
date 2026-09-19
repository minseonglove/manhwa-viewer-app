from __future__ import annotations

import json
import re

from translation_eval.dataset import Item

PROMPT_VERSION = "2026-09-19.1"

SYSTEM_PROMPT_V2 = """Translate the text of one Japanese manga page into Korean, as in an officially published Korean edition.

Input: one segment per line, in reading order: id<TAB>kind<TAB>text.
- bubble: speech. Natural spoken Korean; keep the speaker's politeness level and roughness.
- narration: caption. Written Korean.
- sfx: onomatopoeia or a shout outside a balloon. The Korean onomatopoeia a publisher would use, similar length.
- background: sign, label, notice in the artwork. Short and sign-like; keep proper nouns and Latin letters as they are.
- aside: a short word outside a balloon. Treat as bubble.

Rules: use the whole page as context (same speaker, running jokes, what a reply answers). Japanese names become Korean transliterations of their Japanese reading, never translations. The input may contain OCR errors: translate the evident intended text, never invent content. No explanations, notes, or warnings. If a segment cannot be translated, copy its text unchanged. Never omit a segment.

Output: exactly one JSON object mapping every input id to its Korean text, same order, nothing else. Example: {"t01":"...","t02":"..."}"""

IMAGE_NOTE = """The page image is attached. Use it to resolve what pronouns and demonstratives refer to, who is speaking, and whether a segment is a sign, a note, or speech. Translate only the listed segments; the segment text is authoritative for what is written."""

SYSTEM_PROMPT = """You are a professional manga localization engine. You translate the text of one Japanese manga page into Korean, as it would appear in an officially published Korean edition.

Input: a JSON array of the page's text segments in reading order. Each segment has "id", "kind", and "text".
- kind "bubble": speech in a balloon. Use natural spoken Korean and keep the speaker's tone (politeness level, roughness, hesitation, stuttering).
- kind "narration": narration or caption. Use written Korean.
- kind "sfx": onomatopoeia or a shout outside a balloon. Render as the Korean onomatopoeia a Korean publisher would use, keeping length similar.
- kind "background": signs, labels, notices inside the artwork. Translate short and sign-like. Keep proper nouns and Latin letters as they are.
- kind "aside": a short word said outside a balloon. Treat like "bubble".

Rules:
- Use the whole page as context: same speaker across segments, running jokes, what a reply is answering to.
- Japanese personal names stay as Korean transliterations of the Japanese reading; do not translate them.
- The input may contain OCR recognition errors. When the intended text is obvious from context, translate the intended text. Never invent content that is not implied.
- Never add explanations, notes, translator comments, or warnings anywhere.
- If a segment cannot be translated, copy its source text into "ko" unchanged. Never omit a segment.

Output: only a JSON object of the form {"items": [{"id": "...", "ko": "..."}, ...]} with exactly the same ids in the same order as the input. No other text."""

OUTPUT_SCHEMA = {
    "type": "object",
    "properties": {
        "items": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {"id": {"type": "string"}, "ko": {"type": "string"}},
                "required": ["id", "ko"],
                "additionalProperties": False,
            },
        }
    },
    "required": ["items"],
    "additionalProperties": False,
}


def build_input(items: list[Item], source: str) -> list[dict]:
    return [{"id": item.id, "kind": item.category, "text": item.text(source)} for item in items]


def build_input_lines(items: list[Item], source: str) -> str:
    return "\n".join(f"{item.id}\t{item.category}\t{item.text(source)}" for item in items)


SYSTEM_PROMPT_V2_1 = SYSTEM_PROMPT_V2.replace(
    "- sfx: onomatopoeia or a shout outside a balloon. The Korean onomatopoeia a publisher would use, similar length.",
    "- sfx: onomatopoeia or a shout outside a balloon. Always render it as the Korean onomatopoeia a Korean publisher would print, of similar length; never transliterate the Japanese sound.",
).replace(
    "- background: sign, label, notice in the artwork. Short and sign-like; keep proper nouns and Latin letters as they are.",
    "- background: sign, label, notice in the artwork. Translate it, short and sign-like; keep only proper nouns and Latin letters as they are. Do not leave Japanese script in the output.",
).replace(
    "If a segment cannot be translated, copy its text unchanged.",
    "Only if a segment is unreadable garbage, copy its text unchanged.",
)
assert SYSTEM_PROMPT_V2_1 != SYSTEM_PROMPT_V2 and SYSTEM_PROMPT_V2_1.count("never transliterate") == 1 and "Only if a segment" in SYSTEM_PROMPT_V2_1

PROMPTS = {
    # version -> (system prompt, user payload builder, response_format modes to try in order)
    "v1": (SYSTEM_PROMPT, lambda items, source: json.dumps(build_input(items, source), ensure_ascii=False), ("json_schema", "json_object", "none")),
    "v2": (SYSTEM_PROMPT_V2, build_input_lines, ("json_object", "none")),
    "v2.1": (SYSTEM_PROMPT_V2_1, build_input_lines, ("json_object", "none")),
}
PROMPT_VERSIONS = {"v1": PROMPT_VERSION, "v2": "2026-09-19.2-compact", "v2.1": "2026-09-19.3-compact-sfx"}


def build_messages(items: list[Item], source: str, image_data_url: str | None = None, prompt: str = "v1") -> list[dict]:
    system, build_user, _ = PROMPTS[prompt]
    payload = build_user(items, source)
    if image_data_url is None:
        return [
            {"role": "system", "content": system},
            {"role": "user", "content": payload},
        ]
    return [
        {"role": "system", "content": system + "\n\n" + IMAGE_NOTE},
        {
            "role": "user",
            "content": [
                {"type": "image_url", "image_url": {"url": image_data_url}},
                {"type": "text", "text": payload},
            ],
        },
    ]


_FENCE = re.compile(r"^```(?:json)?\s*|\s*```$", re.MULTILINE)


def parse_output(content: str | None, expected_ids: list[str]) -> tuple[dict[str, str], list[str]]:
    """Return (id -> ko, problems). Missing or unexpected ids are reported, not raised."""
    problems: list[str] = []
    if not content:
        return {}, ["empty response"]
    text = _FENCE.sub("", content.strip()).strip()
    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        return {}, [f"invalid json: {exc.msg}"]
    if isinstance(data, dict) and "items" not in data:
        # compact form: {"t01": "...", "t02": "..."}
        entries = [{"id": k, "ko": v} for k, v in data.items()]
    else:
        entries = data.get("items") if isinstance(data, dict) else data
    if not isinstance(entries, list):
        return {}, ["no items array"]
    out: dict[str, str] = {}
    for entry in entries:
        if not isinstance(entry, dict) or "id" not in entry:
            problems.append("malformed entry")
            continue
        ko = entry.get("ko")
        if not isinstance(ko, str):
            problems.append(f"{entry['id']}: ko is not a string")
            continue
        out[str(entry["id"])] = ko
    expected = set(expected_ids)
    missing = [i for i in expected_ids if i not in out]
    extra = [i for i in out if i not in expected]
    if missing:
        problems.append(f"missing ids: {','.join(missing)}")
    if extra:
        problems.append(f"unexpected ids: {','.join(extra)}")
    return out, problems
