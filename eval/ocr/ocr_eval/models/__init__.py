"""Adapter contract for ocr_eval.run_model.

Each adapter module under `ocr_eval.models` exposes a single function
`load(device: str) -> tuple[Callable[[PIL.Image.Image], str], dict]`. `device` is
one of `auto|cpu|mps`; the adapter resolves `auto` to a concrete device itself.
The returned callable receives the padded ground-truth crop (an RGB PIL image)
and returns the recognized text. The returned dict must contain at least
`model` (HF repo id), `revision` (pinned commit sha), `device` (the device
actually used, e.g. `mac-mps`), and `settings` (a short string describing the
decode/preprocess settings used, e.g. `"greedy, repetition_penalty=1.2, vision
fp16 + decoder int8"`). All loading and warm-up must happen inside `load` so
that the per-item `ms` recorded by the runner measures inference only.
"""
