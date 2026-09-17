#!/usr/bin/env bash
set -euo pipefail

EVAL_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$EVAL_DIR/../.." && pwd)"
TEST_PKG="com.manhwaviewer.data.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.manhwaviewer.data.ocr.eval.OcrEvalRunner"
DEVICE_DIR="files/ocr-eval"
MODES="${1:-recognition,pipeline}"

cd "$EVAL_DIR"
.venv/bin/python -m ocr_eval.validate_gt
GT_FINGERPRINT="$(.venv/bin/python -m ocr_eval.fingerprint)"

cd "$REPO_ROOT"
./gradlew :data:installDebugAndroidTest

# adb push 로 공유 저장소에 넣은 파일은 SELinux 라벨 때문에 테스트 앱이 읽지 못한다. 디버그 APK 의 run-as 로 앱 내부 저장소에 푼다.
# 이전 실행의 out/ 이 남아 이번 결과로 읽히지 않도록 매번 비운다.
adb shell "run-as $TEST_PKG rm -rf $DEVICE_DIR"
COPYFILE_DISABLE=1 tar --no-mac-metadata -C "$EVAL_DIR/data" -cf - gt pages \
  | adb exec-in "run-as $TEST_PKG sh -c 'mkdir -p $DEVICE_DIR && tar -xf - -C $DEVICE_DIR'"

RUN_DIR="$EVAL_DIR/runs/$(date +%Y%m%d-%H%M%S)-android-app"
PARTIAL_DIR="$RUN_DIR.partial"
mkdir -p "$PARTIAL_DIR"
COMMIT="$(git rev-parse --short HEAD)"
if ! git diff --quiet HEAD || [ -n "$(git ls-files --others --exclude-standard -- data domain presentation android-app ios-app eval)" ]; then
  COMMIT="$COMMIT-dirty"
fi
AVD="$(adb shell getprop ro.boot.qemu.avd_name | tr -d '\r')"
RAW_OFFSET="$(date +%z)"
CREATED_AT="$(date +%Y-%m-%dT%H:%M:%S)${RAW_OFFSET:0:3}:${RAW_OFFSET:3:2}"
cat > "$PARTIAL_DIR/meta.json" <<EOF
{
  "runner": "android-app",
  "model": "jzhang533/manga-ocr-base-2025 (fp16 onnx, app decoding)",
  "commit": "$COMMIT",
  "device": "$(adb shell getprop ro.product.model | tr -d '\r')",
  "android": "$(adb shell getprop ro.build.version.release | tr -d '\r')",
  "avd": "$AVD",
  "created_at": "$CREATED_AT",
  "gt_fingerprint": "$GT_FINGERPRINT"
}
EOF

IFS=',' read -ra MODE_LIST <<< "$MODES"
for mode in "${MODE_LIST[@]}"; do
  echo "== $mode"
  adb shell am instrument -w -e class "$TEST_CLASS#$mode" "$TEST_PKG/$RUNNER" | tee "$PARTIAL_DIR/instrument-$mode.log"
  if grep -qE "FAILURES|Process crashed|INSTRUMENTATION_FAILED" "$PARTIAL_DIR/instrument-$mode.log"; then
    echo "instrumentation failed: $PARTIAL_DIR/instrument-$mode.log" >&2
    exit 1
  fi
  adb exec-out "run-as $TEST_PKG cat $DEVICE_DIR/out/$mode.json" > "$PARTIAL_DIR/$mode.json"
  if ! "$EVAL_DIR/.venv/bin/python" -c "import json, sys; json.load(open(sys.argv[1], encoding='utf-8'))" "$PARTIAL_DIR/$mode.json" 2>/dev/null; then
    echo "missing or invalid $mode.json (skipped or crashed): $PARTIAL_DIR/instrument-$mode.log" >&2
    exit 1
  fi
done

mv "$PARTIAL_DIR" "$RUN_DIR"

cd "$EVAL_DIR"
.venv/bin/python -m ocr_eval.score "$RUN_DIR"
