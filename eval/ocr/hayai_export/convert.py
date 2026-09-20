"""Convert the three Hayai OCR v2.1 graphs to CoreML mlprograms (fp16, iOS16).

`torch.export` rather than `torch.jit.trace`, which torch 2.14 deprecates.
Every graph is fully static, so no dynamic shapes are declared.

Usage: convert.py [encoder|prefill|decode|all]
"""

from __future__ import annotations

import os
import json
import sys
import time
from pathlib import Path

import coremltools as ct
import numpy as np
import torch

HERE = Path(__file__).resolve().parent
def _checkpoint_dir() -> Path:
    """The Hugging Face snapshot of the checkpoint: config.json, model.safetensors, modeling_hayai.py, tokenizer.json."""
    value = os.environ.get("HAYAI_CHECKPOINT_DIR")
    if not value:
        raise SystemExit("set HAYAI_CHECKPOINT_DIR to the hayai-ocr-v2 snapshot directory")
    return Path(value)


EXP = _checkpoint_dir()
OUT = HERE / "models"
sys.path.insert(0, str(HERE))

from hayai_static import (  # noqa: E402
    D_AXIS, D_HEAD, H_KV, MAX_PATCHES, N_LAYERS, TOTAL,
    DecodeStatic, EncoderStatic, PrefillStatic, load_hayai, sample_inputs,
)

CACHE_OUT = [f"new_k_{i}" for i in range(N_LAYERS)] + [f"new_v_{i}" for i in range(N_LAYERS)]
PAST_IN = [f"past_k_{i}" for i in range(N_LAYERS)] + [f"past_v_{i}" for i in range(N_LAYERS)]
F32 = np.float32


def specs(graph):
    if graph == "encoder":
        return ([ct.TensorType(name="pixel_values", shape=(1, MAX_PATCHES, 768), dtype=F32),
                 ct.TensorType(name="attention_mask", shape=(1, MAX_PATCHES), dtype=np.int32),
                 ct.TensorType(name="pos_embeds", shape=(1, MAX_PATCHES, 768), dtype=F32)],
                ["visual_features"])
    if graph == "prefill":
        return ([ct.TensorType(name="visual_features", shape=(1, MAX_PATCHES, 768), dtype=F32),
                 ct.TensorType(name="text_token_ids", shape=(1, 1), dtype=np.int32),
                 ct.TensorType(name="cos", shape=(1, TOTAL, D_AXIS), dtype=F32),
                 ct.TensorType(name="sin", shape=(1, TOTAL, D_AXIS), dtype=F32),
                 ct.TensorType(name="mask", shape=(1, 1, TOTAL, TOTAL), dtype=F32)],
                ["logits"] + CACHE_OUT)
    return ([ct.TensorType(name="token_ids", shape=(1, 1), dtype=np.int32),
             ct.TensorType(name="cos", shape=(1, 1, D_AXIS), dtype=F32),
             ct.TensorType(name="sin", shape=(1, 1, D_AXIS), dtype=F32),
             ct.TensorType(name="mask", shape=(1, 1, 1, TOTAL), dtype=F32),
             ct.TensorType(name="write_mask", shape=(1, 1, TOTAL, 1), dtype=F32)]
            + [ct.TensorType(name=n, shape=(1, H_KV, TOTAL, D_HEAD), dtype=F32) for n in PAST_IN],
            ["logits"] + CACHE_OUT)


def build(graph, model):
    if graph == "encoder":
        vm = getattr(model.vision_encoder, "vision_model", model.vision_encoder)
        return EncoderStatic(vm).eval()
    if graph == "prefill":
        return PrefillStatic(model.decoder).eval()
    return DecodeStatic(model.decoder).eval()


def convert(graph, model):
    mod = build(graph, model)
    args = sample_inputs()[graph]
    inputs, out_names = specs(graph)
    print(f"\n=== {graph}: torch.export ===", flush=True)
    t0 = time.perf_counter()
    program = torch.export.export(mod, args).run_decompositions({})
    print(f"    exported in {time.perf_counter() - t0:.1f}s", flush=True)
    print(f"=== {graph}: ct.convert (fp16, iOS16) ===", flush=True)
    t0 = time.perf_counter()
    ml = ct.convert(
        program, inputs=inputs, outputs=[ct.TensorType(name=n) for n in out_names],
        convert_to="mlprogram", compute_precision=ct.precision.FLOAT16,
        minimum_deployment_target=ct.target.iOS16,
    )
    print(f"    converted in {time.perf_counter() - t0:.1f}s", flush=True)
    OUT.mkdir(exist_ok=True)
    path = OUT / f"hayai_{graph}.mlpackage"
    ml.save(str(path))
    spec = ml.get_spec()
    io = {"inputs": [i.name for i in spec.description.input],
          "outputs": [o.name for o in spec.description.output]}
    (OUT / f"hayai_{graph}.io.json").write_text(json.dumps(io, indent=2))
    mb = sum(f.stat().st_size for f in path.rglob("*") if f.is_file()) / 1e6
    print(f"RESULT {graph}: SUCCESS -> {path.name} ({mb:.1f} MB)")
    print(f"    inputs : {io['inputs']}")
    print(f"    outputs: {io['outputs'][:3]} ... ({len(io['outputs'])} total)")


def assets(model):
    """Runtime assets beside the graphs, mirroring export_kv.py's per-quant folder."""
    import shutil
    OUT.mkdir(exist_ok=True)
    vm = getattr(model.vision_encoder, "vision_model", model.vision_encoder)
    np.save(OUT / "position_base.npy", vm.embeddings.position_embedding.weight.detach().numpy())
    shutil.copy(EXP / "tokenizer.json", OUT / "tokenizer.json")
    print(f"RESULT assets: position_base.npy + tokenizer.json -> {OUT}")


def main():
    which = sys.argv[1] if len(sys.argv) > 1 else "all"
    model = load_hayai(EXP)
    if which == "assets":
        assets(model)
        return
    for g in (["encoder", "prefill", "decode"] if which == "all" else [which]):
        convert(g, model)
    if which == "all":
        assets(model)


if __name__ == "__main__":
    main()
