#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FFI_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$FFI_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/libs/lindera"
RUST_TARGETS="aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios"

# Same as deploymentTarget in iosApp/project.yml. Without it the C code in zstd-sys targets the
# SDK's newest iOS while Rust links for iOS 10, and the device build fails on ___chkstk_darwin.
export IPHONEOS_DEPLOYMENT_TARGET=16.0

echo "=== Building lindera-ffi for iOS ==="

if ! command -v cbindgen &> /dev/null; then
    echo "cbindgen is not installed. Install it with: cargo install cbindgen" >&2
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

cbindgen --config cbindgen.toml --crate lindera-ffi --output "$OUTPUT_DIR/lindera_ffi.h"

echo "Creating xcframework..."
# -headers copies the whole directory, and OUTPUT_DIR also holds the xcframework being written.
HEADERS_DIR="$(mktemp -d)"
trap 'rm -rf "$HEADERS_DIR"' EXIT
cp "$OUTPUT_DIR/lindera_ffi.h" "$HEADERS_DIR/"

rm -rf "$OUTPUT_DIR/LinderaFFI.xcframework"
xcodebuild -create-xcframework \
    -library target/aarch64-apple-ios/release/liblindera_ffi.a \
    -headers "$HEADERS_DIR" \
    -library "$OUTPUT_DIR/liblindera_ffi_sim.a" \
    -headers "$HEADERS_DIR" \
    -output "$OUTPUT_DIR/LinderaFFI.xcframework"

echo "=== iOS build complete ==="
