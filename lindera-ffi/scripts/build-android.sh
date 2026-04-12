#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FFI_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$FFI_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/data/src/androidMain/jniLibs"

echo "=== Building lindera-ffi for Android ==="

if ! command -v cargo-ndk &> /dev/null; then
    echo "Installing cargo-ndk..."
    cargo install cargo-ndk
fi

cd "$FFI_DIR"

echo "Building for arm64-v8a..."
cargo ndk -t arm64-v8a -o "$OUTPUT_DIR" build --release --features android

echo "Building for x86_64..."
cargo ndk -t x86_64 -o "$OUTPUT_DIR" build --release --features android

echo "=== Android build complete ==="
ls -la "$OUTPUT_DIR"/*/liblindera_ffi.so 2>/dev/null || echo "Warning: .so files not found"
