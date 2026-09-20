"""Per-item and per-graph latency for a converted model set, per compute-unit setting.

Runs the real adapter over a fixed sample of ground-truth crops, timing each
graph call from inside the interpreter shim so the split is measured, not
apportioned. The gap between the per-item mean and the sum of the per-graph
means is printed as its own line rather than left for the reader to subtract:
it is the host work `generate` does outside the graphs (NaFlex preprocessing,
position-embedding resize, mRoPE and mask construction in numpy, the argmax,
the tokenizer decode) plus the shim's own tensor marshalling.

Usage: bench.py <model_dir> [ALL CPU_ONLY CPU_AND_NE ...]
"""

from __future__ import annotations

import os
import statistics
import sys
import time
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from ocr_eval.crop import crop_item  # noqa: E402
from ocr_eval.gt import load_pages, ready_pages  # noqa: E402
from ocr_eval.models.hayai_coreml import load  # noqa: E402
from ocr_eval.paths import GT_DIR, PAGES_DIR  # noqa: E402

MODEL_DIR = Path(sys.argv[1])
UNITS = sys.argv[2:] or ["ALL", "CPU_ONLY"]
N_ITEMS = 60
GRAPHS = ("encoder", "prefill", "decode")


def crops():
    out = []
    for page in ready_pages(load_pages(GT_DIR)):
        img = Image.open(PAGES_DIR / f"{page.page}.jpg").convert("RGB")
        for item in page.items:
            out.append(crop_item(img, item.bbox))
            if len(out) >= N_ITEMS:
                return out
    return out


def main() -> int:
    os.environ["HAYAI_COREML_DIR"] = str(MODEL_DIR)
    samples = crops()
    print(f"model_dir={MODEL_DIR.name}  items={len(samples)}\n")
    for unit in UNITS:
        # `load` reads HAYAI_COREML_UNITS on every call, so the module is reusable.
        os.environ["HAYAI_COREML_UNITS"] = unit
        ocr, _ = load("auto")
        timings = {name: [] for name in GRAPHS}
        engine = ocr.__self__  # `ocr` is the bound LitertOcrEngine.generate
        for name, graph in zip(GRAPHS, (engine.enc, engine.prefill, engine.decode)):
            original = graph.invoke

            def timed(_o=original, _n=name):
                t = time.perf_counter()
                _o()
                timings[_n].append((time.perf_counter() - t) * 1000)
            graph.invoke = timed

        per_item = []
        for crop in samples:
            t = time.perf_counter()
            ocr(crop)
            per_item.append((time.perf_counter() - t) * 1000)

        item_mean = statistics.mean(per_item)
        graph_mean = {n: sum(timings[n]) / len(samples) for n in GRAPHS}
        print(f"--- compute_units={unit} ---")
        print(f"  per item        median {statistics.median(per_item):7.1f} ms   mean {item_mean:7.1f} ms")
        for name in GRAPHS:
            v = timings[name]
            print(f"  {name:8} call  median {statistics.median(v):7.1f} ms   total/item {graph_mean[name]:7.1f} ms"
                  f"   calls/item {len(v) / len(samples):5.1f}")
        residual = item_mean - sum(graph_mean.values())
        print(f"  host outside the graphs (preprocess, mRoPE/masks, marshalling, tokenizer decode): "
              f"{residual:7.1f} ms/item  ({residual / item_mean * 100:.0f}% of the mean)")
        print(f"  decode steps per item: {len(timings['decode']) / len(samples):.1f}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
