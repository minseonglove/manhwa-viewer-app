#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FFI_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$FFI_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/data/src/androidMain/jniLibs"
RUST_TARGETS="aarch64-linux-android x86_64-linux-android"

echo "=== Building lindera-ffi for Android ==="

if ! command -v cargo-ndk &> /dev/null; then
    echo "cargo-ndk is not installed. Install it with: cargo install cargo-ndk" >&2
    exit 1
fi

if command -v rustup &> /dev/null; then
    installed="$(rustup target list --installed)"
    missing=""
    for target in $RUST_TARGETS; do
        echo "$installed" | grep -qx "$target" || missing="$missing $target"
    done
    if [ -n "$missing" ]; then
        echo "Missing Rust targets. Install them with: rustup target add$missing" >&2
        exit 1
    fi
fi

cd "$FFI_DIR"

echo "Building for arm64-v8a..."
cargo ndk -t arm64-v8a -o "$OUTPUT_DIR" build --release --features android

echo "Building for x86_64..."
cargo ndk -t x86_64 -o "$OUTPUT_DIR" build --release --features android

echo "=== Android build complete ==="
ls -la "$OUTPUT_DIR"/*/liblindera_ffi.so
