#!/usr/bin/env python3
"""Convert ONNX models to CoreML (.mlpackage) for iOS deployment."""

import argparse
import sys
from pathlib import Path

import coremltools as ct
import onnx


def convert_model(onnx_path: Path, output_path: Path, compute_precision: ct.precision):
    """Convert a single ONNX model to CoreML."""
    print(f"Converting {onnx_path.name}...")

    onnx_model = onnx.load(str(onnx_path))
    onnx.checker.check_model(onnx_model)

    mlmodel = ct.convert(
        onnx_model,
        convert_to="mlprogram",
        compute_precision=compute_precision,
        minimum_deployment_target=ct.target.iOS16,
    )

    mlmodel.save(str(output_path))
    print(f"  -> Saved to {output_path} ({output_path.stat().st_size / 1024 / 1024:.1f} MB)")


def main():
    parser = argparse.ArgumentParser(description="Convert ONNX models to CoreML")
    parser.add_argument(
        "--input-dir",
        type=Path,
        default=Path("data/src/androidMain/assets/mangaocr"),
        help="Directory containing ONNX models",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("data/src/iosMain/resources/mangaocr"),
        help="Directory to save CoreML models",
    )
    args = parser.parse_args()

    if not args.input_dir.exists():
        print(f"Error: Input directory {args.input_dir} not found")
        sys.exit(1)

    args.output_dir.mkdir(parents=True, exist_ok=True)

    models = [
        ("encoder_model_fp16.onnx", "encoder.mlpackage"),
        ("decoder_model_fp16.onnx", "decoder.mlpackage"),
        ("manga_panel_detector_fp16.onnx", "yolo_detector.mlpackage"),
    ]

    for onnx_name, coreml_name in models:
        onnx_path = args.input_dir / onnx_name
        if not onnx_path.exists():
            print(f"Warning: {onnx_path} not found, skipping")
            continue

        output_path = args.output_dir / coreml_name
        convert_model(onnx_path, output_path, ct.precision.FLOAT16)

    print("\nDone! CoreML models saved to", args.output_dir)
    print("\nNote: Xcode will compile .mlpackage to .mlmodelc during build.")


if __name__ == "__main__":
    main()
