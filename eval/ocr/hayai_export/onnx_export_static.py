"""Export the three static Hayai graphs to ONNX, and quantise them.

Reuses `hayai_static.py` -- the same rewrites the CoreML path needed (real-valued RoPE,
every extent a Python constant, decode's `pos` replaced by a host-computed `write_mask`).
None of those are ONNX limitations; keeping one set of modules means the ONNX and CoreML
graphs are the same computation and any score difference is the runtime, not the rewrite.

Usage: onnx_export_static.py [export|quantize|all]
"""

from __future__ import annotations

import os
import shutil
import sys
import time
from pathlib import Path

import torch

HERE = Path(__file__).resolve().parent
def _checkpoint_dir() -> Path:
    """The Hugging Face snapshot of the checkpoint: config.json, model.safetensors, modeling_hayai.py, tokenizer.json."""
    value = os.environ.get("HAYAI_CHECKPOINT_DIR")
    if not value:
        raise SystemExit("set HAYAI_CHECKPOINT_DIR to the hayai-ocr-v2 snapshot directory")
    return Path(value)


EXP = _checkpoint_dir()
OUT = HERE / "onnx-models"
sys.path.insert(0, str(HERE))

# convert.py imports coremltools at module level, so the graph list and IO names are restated
# here rather than imported; they must stay identical to the ones convert.py emits.
from hayai_static import (  # noqa: E402
    DecodeStatic, EncoderStatic, N_LAYERS, PrefillStatic, load_hayai, sample_inputs,
)

GRAPHS = ("encoder", "prefill", "decode")
ASSETS = ("position_base.npy", "tokenizer.json")
CACHE_OUT = [f"new_k_{i}" for i in range(N_LAYERS)] + [f"new_v_{i}" for i in range(N_LAYERS)]
PAST_IN = [f"past_k_{i}" for i in range(N_LAYERS)] + [f"past_v_{i}" for i in range(N_LAYERS)]

IO = {
    "encoder": (["pixel_values", "attention_mask", "pos_embeds"], ["visual_features"]),
    "prefill": (["visual_features", "text_token_ids", "cos", "sin", "mask"], ["logits"] + CACHE_OUT),
    "decode": (["token_ids", "cos", "sin", "mask", "write_mask"] + PAST_IN, ["logits"] + CACHE_OUT),
}


def build(graph, model):
    if graph == "encoder":
        vm = getattr(model.vision_encoder, "vision_model", model.vision_encoder)
        return EncoderStatic(vm).eval()
    if graph == "prefill":
        return PrefillStatic(model.decoder).eval()
    return DecodeStatic(model.decoder).eval()


def mb(path: Path) -> float:
    if path.is_dir():
        return sum(f.stat().st_size for f in path.rglob("*") if f.is_file()) / 1e6
    return path.stat().st_size / 1e6


def graph_mb(directory: Path, graph: str) -> float:
    total = mb(directory / f"hayai_{graph}.onnx")
    data = directory / f"hayai_{graph}.onnx.data"
    if data.exists():
        total += mb(data)
    return total


def export_all() -> None:
    model = load_hayai(EXP)
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    for graph in GRAPHS:
        module = build(graph, model)
        args = sample_inputs()[graph]
        in_names, out_names = IO[graph]
        path = OUT / f"hayai_{graph}.onnx"
        t0 = time.perf_counter()
        # dynamo=True routes through torch.export, the same path the CoreML conversion uses.
        # The legacy exporter refuses aten::rms_norm below opset 23; this decomposes it instead.
        torch.onnx.export(
            module, args, str(path),
            input_names=in_names, output_names=out_names,
            opset_version=18, dynamo=True, external_data=False,
        )
        print(f"  {graph:8} {graph_mb(OUT, graph):7.1f} MB  ({time.perf_counter()-t0:.0f}s)", flush=True)
    src = HERE / "models"
    for asset in ASSETS:
        shutil.copy(src / asset, OUT / asset)
    import json
    for graph in GRAPHS:
        shutil.copy(src / f"hayai_{graph}.io.json", OUT / f"hayai_{graph}.io.json")
    print(f"  TOTAL fp32: {sum(graph_mb(OUT, g) for g in GRAPHS):.1f} MB")


def quantize_all() -> None:
    from onnxruntime.quantization import QuantType, quantize_dynamic

    out = HERE / "onnx-models-int8"
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    for graph in GRAPHS:
        src = OUT / f"hayai_{graph}.onnx"
        dst = out / f"hayai_{graph}.onnx"
        t0 = time.perf_counter()
        # Weight-only int8, the closest analogue of LiteRT's wi8_afp32 recipe.
        quantize_dynamic(str(src), str(dst), weight_type=QuantType.QInt8)
        print(f"  {graph:8} {graph_mb(OUT, graph):7.1f} -> {graph_mb(out, graph):7.1f} MB "
              f"({time.perf_counter()-t0:.0f}s)", flush=True)
    for asset in ASSETS:
        shutil.copy(OUT / asset, out / asset)
    for graph in GRAPHS:
        shutil.copy(OUT / f"hayai_{graph}.io.json", out / f"hayai_{graph}.io.json")
    print(f"  TOTAL int8: {sum(graph_mb(out, g) for g in GRAPHS):.1f} MB")


if __name__ == "__main__":
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("export", "all"):
        print("=== ONNX export (fp32, opset 18) ===", flush=True)
        export_all()
    if what in ("quantize", "all"):
        print("\n=== dynamic weight-only int8 ===", flush=True)
        quantize_all()
