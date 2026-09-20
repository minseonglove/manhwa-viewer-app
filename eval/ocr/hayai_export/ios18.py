"""iOS18 conversion + multifunction weight-sharing measurement.

The iOS16 path (`convert.py` / `compress.py` -> `models*/`) is left untouched so the
shipped numbers stay reproducible; this script only adds the iOS18 variants beside it.

The question it answers: merging prefill+decode with `save_multifunction` saved a measured
124.9 MB at fp16, but merging the *palettised* pair failed with
`ValueError: Unknown input 'shape' for op 'constexpr_lut_to_dense'`, so the palette6 saving
was only ever an arithmetic estimate (~46 MB). That failure was on iOS16-style LUT ops.
Converting and palettising at iOS18 emits a different LUT op, which may merge.

A control merge of the iOS16 fp16 pair runs first: without it, any delta between this run and
the reported 124.9 MB could be toolchain drift rather than a property of the deployment target.

Usage: ios18.py [convert|compress|merge|all]
"""

from __future__ import annotations

import os
import json
import shutil
import sys
import time
from pathlib import Path

import coremltools as ct
import numpy as np
import torch
from coremltools.optimize.coreml import (
    OpPalettizerConfig, OptimizationConfig, palettize_weights,
)

HERE = Path(__file__).resolve().parent
def _checkpoint_dir() -> Path:
    """The Hugging Face snapshot of the checkpoint: config.json, model.safetensors, modeling_hayai.py, tokenizer.json."""
    value = os.environ.get("HAYAI_CHECKPOINT_DIR")
    if not value:
        raise SystemExit("set HAYAI_CHECKPOINT_DIR to the hayai-ocr-v2 snapshot directory")
    return Path(value)


EXP = _checkpoint_dir()
sys.path.insert(0, str(HERE))

from convert import assets, build, specs  # noqa: E402  (same recipe, only the target differs)
from hayai_static import load_hayai, sample_inputs  # noqa: E402

OUT18 = HERE / "models-ios18"
GRAPHS = ("encoder", "prefill", "decode")
ASSETS = ("position_base.npy", "tokenizer.json")


def mb(path: Path) -> float:
    return sum(f.stat().st_size for f in path.rglob("*") if f.is_file()) / 1e6


def spec_version(path: Path) -> int:
    return ct.models.MLModel(str(path), skip_model_load=True).get_spec().specificationVersion


def convert_all() -> None:
    model = load_hayai(EXP)
    if OUT18.exists():
        shutil.rmtree(OUT18)
    OUT18.mkdir(parents=True)
    for graph in GRAPHS:
        mod = build(graph, model)
        inputs, out_names = specs(graph)
        t0 = time.perf_counter()
        program = torch.export.export(mod, sample_inputs()[graph]).run_decompositions({})
        ml = ct.convert(
            program, inputs=inputs, outputs=[ct.TensorType(name=n) for n in out_names],
            convert_to="mlprogram", compute_precision=ct.precision.FLOAT16,
            minimum_deployment_target=ct.target.iOS18,
        )
        path = OUT18 / f"hayai_{graph}.mlpackage"
        ml.save(str(path))
        spec = ml.get_spec()
        (OUT18 / f"hayai_{graph}.io.json").write_text(json.dumps(
            {"inputs": [i.name for i in spec.description.input],
             "outputs": [o.name for o in spec.description.output]}, indent=2))
        print(f"  {graph:8} {mb(path):7.1f} MB  spec v{spec.specificationVersion}  "
              f"({time.perf_counter()-t0:.0f}s)", flush=True)
    # `assets` writes into convert.OUT; copy rather than re-deriving so both sets share one source.
    src = HERE / "models"
    for a in ASSETS:
        shutil.copy(src / a, OUT18 / a)
    print(f"  TOTAL iOS18 fp16: {sum(mb(OUT18 / f'hayai_{g}.mlpackage') for g in GRAPHS):.1f} MB")


PALETTE_RECIPES = {
    # per-tensor 6-bit: the iOS16 recipe, re-applied on iOS18 graphs
    "palette6": OpPalettizerConfig(mode="kmeans", nbits=6, granularity="per_tensor"),
    # iOS18-only: grouped-channel LUT, the "blockwise" option iOS16 cannot express
    "palette4-grouped": OpPalettizerConfig(
        mode="kmeans", nbits=4, granularity="per_grouped_channel", group_size=16),
    "palette6-grouped": OpPalettizerConfig(
        mode="kmeans", nbits=6, granularity="per_grouped_channel", group_size=16),
}


def compress(recipe: str) -> bool:
    out = HERE / f"models-ios18-{recipe}"
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    print(f"\n=== iOS18 {recipe} ===", flush=True)
    saved = []
    for g in GRAPHS:
        src = OUT18 / f"hayai_{g}.mlpackage"
        dst = out / f"hayai_{g}.mlpackage"
        t0 = time.perf_counter()
        model = ct.models.MLModel(str(src), compute_units=ct.ComputeUnit.CPU_ONLY)
        try:
            compressed = palettize_weights(model, OptimizationConfig(
                global_config=PALETTE_RECIPES[recipe]))
        except ValueError as e:
            print(f"  {g:8} BLOCKED: {e}")
            continue
        compressed.save(str(dst))
        saved.append(g)
        print(f"  {g:8} {mb(src):7.1f} -> {mb(dst):7.1f} MB  spec v"
              f"{compressed.get_spec().specificationVersion}  ({time.perf_counter()-t0:.0f}s)",
              flush=True)
    if len(saved) != len(GRAPHS):
        print(f"  INCOMPLETE {recipe}: saved {saved}; {out.name} left without io.json or assets")
        return False
    for g in GRAPHS:
        shutil.copy(OUT18 / f"hayai_{g}.io.json", out / f"hayai_{g}.io.json")
    for a in ASSETS:
        shutil.copy(OUT18 / a, out / a)
    print(f"  TOTAL iOS18 {recipe}: {sum(mb(out / f'hayai_{g}.mlpackage') for g in GRAPHS):.1f} MB")
    return True


def merge(label: str, src_dir: Path) -> None:
    """Merge prefill+decode into one multifunction package and report what it saved."""
    prefill, decode = (src_dir / f"hayai_{g}.mlpackage" for g in ("prefill", "decode"))
    if not (prefill.exists() and decode.exists()):
        print(f"  {label:26} SKIPPED: {src_dir.name} has no prefill/decode pair")
        return
    separate = mb(prefill) + mb(decode)
    out = HERE / f"mf-{label}.mlpackage"
    if out.exists():
        shutil.rmtree(out)
    desc = ct.utils.MultiFunctionDescriptor()
    desc.add_function(str(prefill), "main", "prefill")
    desc.add_function(str(decode), "main", "decode")
    desc.default_function_name = "prefill"
    t0 = time.perf_counter()
    try:
        ct.utils.save_multifunction(desc, str(out))
    except Exception as e:  # noqa: BLE001 — the failure mode itself is the measurement
        print(f"  {label:26} separate {separate:7.1f} MB -> FAILED  "
              f"{type(e).__name__}: {e}")
        return
    merged = mb(out)
    enc = mb(src_dir / "hayai_encoder.mlpackage")
    print(f"  {label:26} separate {separate:7.1f} MB -> merged {merged:7.1f} MB  "
          f"saves {separate - merged:6.1f} MB   spec v{spec_version(out)}   "
          f"whole model {enc + separate:7.1f} -> {enc + merged:7.1f} MB   "
          f"({time.perf_counter()-t0:.0f}s)", flush=True)


def merge_all() -> None:
    print("\n=== multifunction merge (prefill + decode) ===", flush=True)
    merge("ios16-fp16(control)", HERE / "models")
    merge("ios16-palette6", HERE / "models-palette6")
    merge("ios18-fp16", OUT18)
    for recipe in PALETTE_RECIPES:
        merge(f"ios18-{recipe}", HERE / f"models-ios18-{recipe}")


def modelset(recipe: str) -> None:
    """Assemble a loadable model set whose decoder is the merged two-function package.

    `hayai_coreml.py` with `HAYAI_COREML_MODE=merged` expects `hayai_encoder.mlpackage`
    plus `hayai_decoder.mlpackage`; the three `io.json` still describe the IO order of
    each function, so the adapter's positional checks are unchanged.
    """
    src = HERE / f"models-ios18-{recipe}"
    merged = HERE / f"mf-ios18-{recipe}.mlpackage"
    out = HERE / f"models-ios18-{recipe}-merged"
    if not merged.exists():
        print(f"  {recipe:22} SKIPPED: {merged.name} not built")
        return
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    shutil.copytree(src / "hayai_encoder.mlpackage", out / "hayai_encoder.mlpackage")
    shutil.copytree(merged, out / "hayai_decoder.mlpackage")
    for g in GRAPHS:
        shutil.copy(src / f"hayai_{g}.io.json", out / f"hayai_{g}.io.json")
    for a in ASSETS:
        shutil.copy(src / a, out / a)
    print(f"  {recipe:22} -> {out.name}  {mb(out):7.1f} MB "
          f"(encoder {mb(out / 'hayai_encoder.mlpackage'):.1f} + "
          f"decoder {mb(out / 'hayai_decoder.mlpackage'):.1f})")


def main() -> int:
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("convert", "all"):
        print("=== iOS18 fp16 conversion ===", flush=True)
        convert_all()
    if what in ("compress", "all"):
        for recipe in PALETTE_RECIPES:
            compress(recipe)
    if what in ("merge", "all"):
        merge_all()
    if what in ("modelset", "all"):
        print("\n=== loadable merged model sets ===", flush=True)
        for recipe in ("palette6", "palette4-grouped"):
            modelset(recipe)
    return 0


if __name__ == "__main__":
    sys.exit(main())
