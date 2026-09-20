"""Torch vs CoreML max abs diff on every output of every graph.

Random inputs plus one real ground-truth crop driven through the actual host
pipeline, so the "real crop" row exercises the input distribution the eval run
will see (real visual features, real mRoPE, real masks).

Each graph carries a *relative* tolerance, because that is what the report
reasons about: the absolute diff scales with the activation magnitude, which
differs per case, while the relative error is the stable quantity. A breach
exits non-zero and the table is kept in `parity.log` (or `parity-<dir>.log` for
a non-default model set) so the report's numbers come from an artifact.

Usage: parity.py [model_dir]
"""

from __future__ import annotations

import os
import json
import sys
from pathlib import Path

import coremltools as ct
import numpy as np
import torch
from PIL import Image

HERE = Path(__file__).resolve().parent
def _checkpoint_dir() -> Path:
    """The Hugging Face snapshot of the checkpoint: config.json, model.safetensors, modeling_hayai.py, tokenizer.json."""
    value = os.environ.get("HAYAI_CHECKPOINT_DIR")
    if not value:
        raise SystemExit("set HAYAI_CHECKPOINT_DIR to the hayai-ocr-v2 snapshot directory")
    return Path(value)


EXP = _checkpoint_dir()
sys.path.insert(0, str(HERE))

from hayai_ocr.litert import compute_pos_embeds, compute_rope, make_decode_mask, preprocess_image_pil  # noqa: E402
from hayai_static import (  # noqa: E402
    D_HEAD, H_KV, MAX_PATCHES, N_LAYERS, PREFILL_LEN, TOTAL,
    DecodeStatic, EncoderStatic, PrefillStatic, load_hayai, prefill_mask,
)
from ocr_eval.crop import crop_item  # noqa: E402
from ocr_eval.gt import load_pages, ready_pages  # noqa: E402
from ocr_eval.paths import GT_DIR, PAGES_DIR  # noqa: E402

MODELS = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "models"
LOG = HERE / ("parity.log" if MODELS.name == "models" else f"parity-{MODELS.name}.log")
RNG = np.random.default_rng(0)

# Relative tolerance per graph. Set from the worst value each graph has already
# been measured at across the fp16 and int8 model sets, with the headroom noted;
# a lossier recipe than int8 is expected to breach, which is the point.
TOLERANCES = {
    "encoder": (0.10, "worst measured 2.6e-02 fp16 / 3.9e-02 int8"),
    "prefill": (0.10, "worst measured 1.2e-02 fp16 / 3.6e-02 int8"),
    "decode": (0.15, "worst measured 2.6e-02 fp16 / 6.3e-02 int8"),
}


class _Tee:
    def __init__(self, path: Path):
        self.file = path.open("w")
        self.stream = sys.__stdout__

    def write(self, text: str) -> int:
        self.file.write(text)
        return self.stream.write(text)

    def flush(self) -> None:
        self.file.flush()
        self.stream.flush()


def load_ml(name):
    io = json.loads((MODELS / f"hayai_{name}.io.json").read_text())
    ml = ct.models.MLModel(str(MODELS / f"hayai_{name}.mlpackage"), compute_units=ct.ComputeUnit.CPU_ONLY)
    return ml, io


def diff(torch_out, ml_out, names):
    torch_out = torch_out if isinstance(torch_out, tuple) else (torch_out,)
    per, scale = [], []
    for t, n in zip(torch_out, names):
        ref = t.numpy().astype(np.float64)
        per.append(np.abs(ref - ml_out[n].astype(np.float64)).max())
        scale.append(np.abs(ref).max())
    return max(per), per, scale


def main() -> int:
    sys.stdout = _Tee(LOG)
    print(f"model_dir={MODELS}")
    model = load_hayai(EXP)
    vm = getattr(model.vision_encoder, "vision_model", model.vision_encoder)
    mods = {"encoder": EncoderStatic(vm).eval(), "prefill": PrefillStatic(model.decoder).eval(),
            "decode": DecodeStatic(model.decoder).eval()}
    mls = {n: load_ml(n) for n in mods}
    pos_base = vm.embeddings.position_embedding.weight.detach().numpy()

    def encoder_case(pv, am, pe):
        return ((torch.from_numpy(pv), torch.from_numpy(am.astype(np.int64)), torch.from_numpy(pe)),
                {"pixel_values": pv, "attention_mask": am.astype(np.int32), "pos_embeds": pe})

    def prefill_case(vis, cos, sin):
        msk = prefill_mask().numpy()
        return ((torch.from_numpy(vis), torch.tensor([[1]], dtype=torch.long), torch.from_numpy(cos),
                 torch.from_numpy(sin), torch.from_numpy(msk)),
                {"visual_features": vis, "text_token_ids": np.array([[1]], np.int32),
                 "cos": cos, "sin": sin, "mask": msk})

    def decode_case(cos, sin, pos, caches):
        msk = make_decode_mask(pos)
        wm = (np.arange(TOTAL) == pos).reshape(1, 1, TOTAL, 1).astype(np.float32)
        t_args = (torch.tensor([[7]], dtype=torch.long), torch.from_numpy(cos), torch.from_numpy(sin),
                  torch.from_numpy(msk), torch.from_numpy(wm), *[torch.from_numpy(c) for c in caches])
        feed = {"token_ids": np.array([[7]], np.int32), "cos": cos, "sin": sin, "mask": msk, "write_mask": wm}
        feed.update({f"past_k_{i}": caches[i] for i in range(N_LAYERS)})
        feed.update({f"past_v_{i}": caches[N_LAYERS + i] for i in range(N_LAYERS)})
        return t_args, feed

    cases = {"encoder": [], "prefill": [], "decode": []}
    for _ in range(2):
        pv = RNG.standard_normal((1, MAX_PATCHES, 768), dtype=np.float32)
        am = np.ones((1, MAX_PATCHES), np.int64)
        am[0, 200:] = 0
        cases["encoder"].append(("random", encoder_case(pv, am, RNG.standard_normal((1, MAX_PATCHES, 768), dtype=np.float32))))
        cos, sin = compute_rope(16, 16)
        cases["prefill"].append(("random", prefill_case(RNG.standard_normal((1, MAX_PATCHES, 768), dtype=np.float32), cos, sin)))
        caches = [np.concatenate([RNG.standard_normal((1, H_KV, PREFILL_LEN, D_HEAD), dtype=np.float32),
                                  np.zeros((1, H_KV, TOTAL - PREFILL_LEN, D_HEAD), np.float32)], 2)
                  for _ in range(2 * N_LAYERS)]
        cases["decode"].append(("random", decode_case(cos[:, 257:258, :].copy(), sin[:, 257:258, :].copy(), PREFILL_LEN, caches)))

    # one real ground-truth crop, through the real host pipeline and the real graph chain
    page = ready_pages(load_pages(GT_DIR))[0]
    item = page.items[0]
    img = crop_item(Image.open(PAGES_DIR / f"{page.page}.jpg").convert("RGB"), item.bbox)
    pv, am, (ph, pw) = preprocess_image_pil(img)
    pe = compute_pos_embeds(pos_base, ph, pw)
    cos, sin = compute_rope(ph, pw)
    cases["encoder"].append((f"real {page.page}/{item.id}", encoder_case(pv, am, pe)))
    enc_ml, _ = mls["encoder"]
    visual = enc_ml.predict({"pixel_values": pv, "attention_mask": am.astype(np.int32), "pos_embeds": pe})["visual_features"]
    cases["prefill"].append(("real crop", prefill_case(visual, cos, sin)))
    pre_ml, pre_io = mls["prefill"]
    pre_out = pre_ml.predict(cases["prefill"][-1][1][1])
    real_caches = [pre_out[n] for n in pre_io["outputs"][1:]]
    cases["decode"].append(("real crop", decode_case(cos[:, 257:258, :].copy(), sin[:, 257:258, :].copy(), PREFILL_LEN, real_caches)))

    failures = []
    hdr = (f"{'graph':9} {'case':22} {'outs':>5} {'max abs diff':>13} {'ref max |x|':>12} "
           f"{'rel':>9} {'tol':>7} {'':6} worst output")
    print(hdr)
    print("-" * len(hdr))
    with torch.no_grad():
        for name in ("encoder", "prefill", "decode"):
            ml, io = mls[name]
            tol, why = TOLERANCES[name]
            for label, (t_args, feed) in cases[name]:
                worst, per, scale = diff(mods[name](*t_args), ml.predict(feed), io["outputs"])
                i = int(np.argmax(per))
                rel = per[i] / scale[i] if scale[i] else (0.0 if per[i] == 0 else float("inf"))
                ok = rel <= tol
                if not ok:
                    failures.append(f"{name} / {label}: rel {rel:.2e} > {tol:.2f}")
                print(f"{name:9} {label:22} {len(per):>5} {worst:>13.3e} {scale[i]:>12.3e} "
                      f"{rel:>9.2e} {tol:>7.2f} {'ok' if ok else 'BREACH':6} {io['outputs'][i]}")
    print()
    for name, (tol, why) in TOLERANCES.items():
        print(f"tolerance {name:9} rel <= {tol:.2f}   ({why})")

    if failures:
        print(f"\nFAIL: {len(failures)} tolerance breach(es)")
        for line in failures:
            print(f"  {line}")
        return 1
    print(f"\nPASS: {sum(len(v) for v in cases.values())} cases within tolerance")
    return 0


if __name__ == "__main__":
    sys.exit(main())
