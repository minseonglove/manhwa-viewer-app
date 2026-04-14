#!/usr/bin/env python3
"""Convert ONNX models to CoreML (.mlpackage) for iOS deployment.

Pipeline: ONNX fp16 → fp32 → fix ops → onnx2torch → PyTorch trace → CoreML

Requirements: pip install coremltools onnx onnx2torch torch
"""

import argparse
import shutil
import sys
from pathlib import Path

import coremltools as ct
import numpy as np
import onnx
import torch
from onnx import numpy_helper, TensorProto
from onnx2torch import convert as onnx2torch_convert
from onnx2torch.node_converters.registry import (
    _CONVERTER_REGISTRY,
    OperationConverterResult,
    OperationDescription,
)
from onnx2torch.utils.common import OnnxMapping


# ---------------------------------------------------------------------------
# ONNX pre-processing helpers
# ---------------------------------------------------------------------------

def fp16_to_fp32(model: onnx.ModelProto) -> onnx.ModelProto:
    """Convert fp16 initializers, I/O, and Cast nodes to fp32."""
    for init in model.graph.initializer:
        if init.data_type == TensorProto.FLOAT16:
            arr = numpy_helper.to_array(init).astype(np.float32)
            init.CopyFrom(numpy_helper.from_array(arr, name=init.name))
    for io in list(model.graph.input) + list(model.graph.output) + list(model.graph.value_info):
        if io.type.tensor_type.elem_type == TensorProto.FLOAT16:
            io.type.tensor_type.elem_type = TensorProto.FLOAT
    for node in model.graph.node:
        if node.op_type == "Cast":
            for attr in node.attribute:
                if attr.name == "to" and attr.i == TensorProto.FLOAT16:
                    attr.i = TensorProto.FLOAT
    return model


def remove_identity_casts(model: onnx.ModelProto) -> onnx.ModelProto:
    """Remove Cast nodes that became identity after fp16→fp32 conversion."""
    rename_map: dict[str, str] = {}
    keep = []
    for node in model.graph.node:
        if node.op_type == "Cast":
            to = next((a.i for a in node.attribute if a.name == "to"), None)
            if to in (TensorProto.FLOAT16, TensorProto.FLOAT):
                rename_map[node.output[0]] = node.input[0]
                continue
        keep.append(node)

    def resolve(name: str) -> str:
        visited: set[str] = set()
        while name in rename_map and name not in visited:
            visited.add(name)
            name = rename_map[name]
        return name

    for node in keep:
        for i in range(len(node.input)):
            node.input[i] = resolve(node.input[i])
        for i in range(len(node.output)):
            node.output[i] = resolve(node.output[i])
    for out in model.graph.output:
        out.name = resolve(out.name)
    del model.graph.node[:]
    model.graph.node.extend(keep)
    return model


# ---------------------------------------------------------------------------
# onnx2torch patches
# ---------------------------------------------------------------------------

class _LessOrEqualModule(torch.nn.Module):
    def forward(self, x, y):
        return torch.le(x, y)


def _le_converter(node, graph):
    return OperationConverterResult(
        torch_module=_LessOrEqualModule(),
        onnx_mapping=OnnxMapping(inputs=node.input_values, outputs=node.output_values),
    )


def _register_missing_converters():
    """Register onnx2torch converters for ops not natively supported."""
    for ver in range(1, 20):
        desc = OperationDescription("", "LessOrEqual", ver)
        if desc not in _CONVERTER_REGISTRY:
            _CONVERTER_REGISTRY[desc] = _le_converter


# ---------------------------------------------------------------------------
# Core conversion
# ---------------------------------------------------------------------------

def convert_model(
    onnx_path: Path,
    output_path: Path,
    input_shapes: dict[str, list[int]],
    needs_cast_cleanup: bool = False,
):
    """Convert a single ONNX model to CoreML via PyTorch."""
    print(f"Converting {onnx_path.name}...")

    model = onnx.load(str(onnx_path))
    model = fp16_to_fp32(model)
    if needs_cast_cleanup:
        model = remove_identity_casts(model)

    print("  Loading as PyTorch model...")
    torch_model = onnx2torch_convert(model)
    torch_model.eval()

    print("  Tracing...")
    dummy_inputs = []
    ct_inputs = []
    for name, shape in input_shapes.items():
        if "input_ids" in name:
            dummy_inputs.append(torch.zeros(shape, dtype=torch.long))
            ct_inputs.append(ct.TensorType(name=name, shape=shape, dtype=np.int32))
        else:
            dummy_inputs.append(torch.randn(shape, dtype=torch.float32))
            ct_inputs.append(ct.TensorType(name=name, shape=shape, dtype=np.float32))

    traced = (
        torch.jit.trace(torch_model, tuple(dummy_inputs))
        if len(dummy_inputs) > 1
        else torch.jit.trace(torch_model, dummy_inputs[0])
    )

    print("  Converting to CoreML...")
    mlmodel = ct.convert(
        traced,
        inputs=ct_inputs,
        convert_to="mlprogram",
        compute_precision=ct.precision.FLOAT16,
        minimum_deployment_target=ct.target.iOS16,
    )

    if output_path.exists():
        shutil.rmtree(output_path)
    mlmodel.save(str(output_path))

    total_size = sum(f.stat().st_size for f in output_path.rglob("*") if f.is_file())
    print(f"  -> {output_path} ({total_size / 1024 / 1024:.1f} MB)")


def main():
    parser = argparse.ArgumentParser(description="Convert ONNX models to CoreML")
    parser.add_argument(
        "--input-dir",
        type=Path,
        default=Path("data/src/androidMain/assets/mangaocr"),
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("data/src/iosMain/resources/mangaocr"),
    )
    args = parser.parse_args()

    if not args.input_dir.exists():
        print(f"Error: {args.input_dir} not found")
        sys.exit(1)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    _register_missing_converters()

    models = [
        {
            "onnx": "encoder_model_fp16.onnx",
            "coreml": "encoder.mlpackage",
            "inputs": {"pixel_values": [1, 3, 224, 224]},
        },
        {
            "onnx": "decoder_model_fp16.onnx",
            "coreml": "decoder.mlpackage",
            "inputs": {
                "input_ids": [1, 1],
                "encoder_hidden_states": [1, 197, 192],
            },
        },
        {
            "onnx": "manga_panel_detector_fp16.onnx",
            "coreml": "yolo_detector.mlpackage",
            "inputs": {"images": [1, 3, 640, 640]},
            "needs_cast_cleanup": True,
        },
    ]

    for spec in models:
        onnx_path = args.input_dir / spec["onnx"]
        if not onnx_path.exists():
            print(f"Warning: {onnx_path} not found, skipping")
            continue
        try:
            convert_model(
                onnx_path,
                args.output_dir / spec["coreml"],
                spec["inputs"],
                needs_cast_cleanup=spec.get("needs_cast_cleanup", False),
            )
        except Exception as e:
            print(f"  ERROR: {e}")

    print("\nDone! Xcode will compile .mlpackage → .mlmodelc during build.")


if __name__ == "__main__":
    main()
