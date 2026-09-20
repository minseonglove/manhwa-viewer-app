"""Compile each .mlpackage to .mlmodelc, report size, and read CoreML's compute plan.

The compute plan is what the CoreML runtime on THIS machine says it would do;
it is a real placement report for this Mac, not for an iPhone's ANE.

Every row carries its denominator. A MIL program is mostly `const` and
`constexpr_*` weight ops, for which the runtime returns no placement at all, so
a bare "ANE 295" is unreadable without knowing that 434 of the graph's 733 ops
were never candidates for a device in the first place. "On the ANE" here means
compute ops, and the constant ops are counted separately rather than folded in.

Usage: placement.py <model_dir> [...]
"""

from __future__ import annotations

import collections
import subprocess
import sys
from pathlib import Path

import coremltools as ct
from coremltools.models.compute_plan import MLComputePlan

GRAPHS = ("encoder", "prefill", "decode")
UNITS = (ct.ComputeUnit.ALL, ct.ComputeUnit.CPU_AND_NE, ct.ComputeUnit.CPU_ONLY)
DEVICE_NAMES = {"MLNeuralEngineComputeDevice": "ANE", "MLCPUComputeDevice": "CPU", "MLGPUComputeDevice": "GPU"}


def mb(path: Path) -> float:
    return sum(f.stat().st_size for f in path.rglob("*") if f.is_file()) / 1e6


def is_constant(op_type: str) -> bool:
    """A compiled graph names ops by opset (`ios16.constexpr_lut_to_dense`)."""
    bare = op_type.rsplit(".", 1)[-1]
    return bare == "const" or bare.startswith("constexpr")


def compile_package(pkg: Path, out_dir: Path) -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    try:
        subprocess.run(["xcrun", "coremlcompiler", "compile", str(pkg), str(out_dir)],
                       check=True, capture_output=True, text=True)
    except subprocess.CalledProcessError as e:
        print(f"  coremlcompiler failed ({e.returncode}) on {pkg.name}:\n{e.stderr}", flush=True)
        raise
    return out_dir / f"{pkg.stem}.mlmodelc"


def plan_for(compiled: Path) -> tuple[dict, int, int]:
    """(per-unit placement, total MIL ops, constant ops) for one compiled graph."""
    rows, total, constants = {}, 0, 0
    for units in UNITS:
        plan = MLComputePlan.load_from_path(str(compiled), compute_units=units)
        program = plan.model_structure.program
        if program is None:
            rows[units.name] = {"error": "compute plan carries no MIL program"}
            continue
        operations = list(program.functions["main"].block.operations)
        total = len(operations)
        constants = sum(1 for op in operations if is_constant(op.operator_name))
        placed = collections.Counter()
        unplaced_compute = unplaced_const = placed_const = 0
        for op in operations:
            usage = plan.get_compute_device_usage_for_mlprogram_operation(op)
            constant = is_constant(op.operator_name)
            if usage is None:
                unplaced_const += constant
                unplaced_compute += not constant
                continue
            placed_const += constant
            placed[DEVICE_NAMES.get(type(usage.preferred_compute_device).__name__,
                                    type(usage.preferred_compute_device).__name__)] += 1
        rows[units.name] = {"placed": dict(sorted(placed.items())), "placed_const": placed_const,
                            "unplaced_compute": unplaced_compute, "unplaced_const": unplaced_const}
    return rows, total, constants


def main() -> int:
    for raw in sys.argv[1:]:
        model_dir = Path(raw)
        out = model_dir.parent / f"compiled-{model_dir.name}"
        print(f"\n=== {model_dir.name} ===")
        total_pkg = total_c = 0.0
        for g in GRAPHS:
            pkg = model_dir / f"hayai_{g}.mlpackage"
            if not pkg.is_dir():
                print(f"  {g:8} MISSING")
                continue
            compiled = compile_package(pkg, out)
            p, c = mb(pkg), mb(compiled)
            total_pkg += p
            total_c += c
            rows, total, constants = plan_for(compiled)
            print(f"  {g:8} mlpackage {p:7.1f} MB   mlmodelc {c:7.1f} MB")
            print(f"           MIL ops {total}: {constants} const/constexpr, {total - constants} compute")
            for unit, row in rows.items():
                if "error" in row:
                    print(f"           {unit:12} {row['error']}")
                    continue
                placed = "  ".join(f"{d} {n}" for d, n in row["placed"].items()) or "none"
                total_placed = sum(row["placed"].values())
                print(f"           {unit:12} placed {total_placed}/{total}: {placed}"
                      f"   (of those, {row['placed_const']} const)"
                      f"   no placement: {row['unplaced_const']} const + {row['unplaced_compute']} compute")
        print(f"  TOTAL    mlpackage {total_pkg:7.1f} MB   mlmodelc {total_c:7.1f} MB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
