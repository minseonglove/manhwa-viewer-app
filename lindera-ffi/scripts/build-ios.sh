#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FFI_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$FFI_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/libs/lindera"

echo "=== Building lindera-ffi for iOS ==="
cd "$FFI_DIR"

rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios 2>/dev/null || true

echo "Building for aarch64-apple-ios (device)..."
cargo build --release --target aarch64-apple-ios

echo "Building for aarch64-apple-ios-sim (simulator arm64)..."
cargo build --release --target aarch64-apple-ios-sim

echo "Building for x86_64-apple-ios (simulator x86_64)..."
cargo build --release --target x86_64-apple-ios

echo "Creating simulator fat library..."
mkdir -p "$OUTPUT_DIR"
lipo -create \
    target/aarch64-apple-ios-sim/release/liblindera_ffi.a \
    target/x86_64-apple-ios/release/liblindera_ffi.a \
    -output "$OUTPUT_DIR/liblindera_ffi_sim.a"

if command -v cbindgen &> /dev/null; then
    cbindgen --config cbindgen.toml --crate lindera-ffi --output "$OUTPUT_DIR/lindera_ffi.h"
fi

echo "Creating xcframework..."
rm -rf "$OUTPUT_DIR/LinderaFFI.xcframework"
xcodebuild -create-xcframework \
    -library target/aarch64-apple-ios/release/liblindera_ffi.a \
    -headers "$OUTPUT_DIR" \
    -library "$OUTPUT_DIR/liblindera_ffi_sim.a" \
    -headers "$OUTPUT_DIR" \
    -output "$OUTPUT_DIR/LinderaFFI.xcframework"

echo "=== iOS build complete ==="
