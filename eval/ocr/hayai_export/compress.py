"""Post-conversion weight compression of the three CoreML graphs.

Only recipes whose MIL op exists at the app's iOS16 deployment target are
applied; anything that would lower to an iOS18-only op is reported as blocked
rather than silently bumping the target.

A recipe's output directory is loadable only if every graph in it saved: the
directory is cleared before the recipe runs, and `hayai_*.io.json` plus the
runtime assets are written only after all three saves succeed. Without that, a
blocked graph would leave the previous recipe's `.mlpackage` beside a fresh
`io.json` and the adapter would load it under the new recipe's name.

Usage: compress.py <recipe> [...]
"""

from __future__ import annotations

import shutil
import sys
import time
from pathlib import Path

import coremltools as ct
from coremltools.optimize.coreml import (
    OpLinearQuantizerConfig, OpPalettizerConfig, OptimizationConfig,
    linear_quantize_weights, palettize_weights,
)

HERE = Path(__file__).resolve().parent
SRC = HERE / "models"
GRAPHS = ("encoder", "prefill", "decode")
ASSETS = ("position_base.npy", "tokenizer.json")

RECIPES = {
    "int8": lambda m: linear_quantize_weights(m, OptimizationConfig(
        global_config=OpLinearQuantizerConfig(mode="linear_symmetric", dtype="int8", granularity="per_channel"))),
    "int8_per_tensor": lambda m: linear_quantize_weights(m, OptimizationConfig(
        global_config=OpLinearQuantizerConfig(mode="linear_symmetric", dtype="int8", granularity="per_tensor"))),
    "int4": lambda m: linear_quantize_weights(m, OptimizationConfig(
        global_config=OpLinearQuantizerConfig(mode="linear_symmetric", dtype="int4", granularity="per_channel"))),
    "palette6": lambda m: palettize_weights(m, OptimizationConfig(
        global_config=OpPalettizerConfig(mode="kmeans", nbits=6, granularity="per_tensor"))),
    "palette4": lambda m: palettize_weights(m, OptimizationConfig(
        global_config=OpPalettizerConfig(mode="kmeans", nbits=4, granularity="per_tensor"))),
}


def mb(path: Path) -> float:
    return sum(f.stat().st_size for f in path.rglob("*") if f.is_file()) / 1e6


def run(recipe: str) -> bool:
    out = HERE / f"models-{recipe}"
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    print(f"\n=== recipe {recipe} ===", flush=True)
    saved = []
    for g in GRAPHS:
        src = SRC / f"hayai_{g}.mlpackage"
        dst = out / f"hayai_{g}.mlpackage"
        t0 = time.perf_counter()
        model = ct.models.MLModel(str(src), compute_units=ct.ComputeUnit.CPU_ONLY)
        try:
            compressed = RECIPES[recipe](model)
        except ValueError as e:
            # coremltools reports an op/target mismatch as ValueError; anything
            # else (OSError, MemoryError, ...) is not a deployment-target fact
            # and must not be reported as one.
            print(f"  {g:8} BLOCKED at this deployment target: {e}")
            continue
        compressed.save(str(dst))
        saved.append(g)
        print(f"  {g:8} {mb(src):7.1f} MB -> {mb(dst):7.1f} MB  "
              f"(x{mb(dst)/mb(src):.2f}, spec v{compressed.get_spec().specificationVersion}, "
              f"{time.perf_counter()-t0:.0f}s)", flush=True)

    if len(saved) != len(GRAPHS):
        print(f"  INCOMPLETE {recipe}: saved {saved}; {out.name} left without io.json or assets "
              "so it cannot be loaded as a model set")
        return False
    for g in GRAPHS:
        shutil.copy(SRC / f"hayai_{g}.io.json", out / f"hayai_{g}.io.json")
    for asset in ASSETS:
        shutil.copy(SRC / asset, out / asset)
    print(f"  TOTAL {recipe}: {sum(mb(out / f'hayai_{g}.mlpackage') for g in GRAPHS):.1f} MB")
    return True


if __name__ == "__main__":
    requested = sys.argv[1:] or ["int8"]
    unknown = [r for r in requested if r not in RECIPES]
    if unknown:
        raise SystemExit(f"unknown recipe(s) {unknown}; valid recipes are {sorted(RECIPES)}")
    if not all([run(r) for r in requested]):
        raise SystemExit(1)
