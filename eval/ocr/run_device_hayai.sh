#!/usr/bin/env bash
# Hayai OCR (Kotlin host port + ONNX Runtime) on a connected Android device.
#
# Mirrors run_device.sh, with two differences: a different test class, and ~148 MB of ONNX
# graphs pushed alongside the evaluation data. The models are not in the APK — the shared-weight
# blob has to sit beside the graphs that reference it, and the whole set is too big to bundle.
set -euo pipefail

EVAL_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$EVAL_DIR/../.." && pwd)"
TEST_PKG="com.manhwaviewer.data.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.manhwaviewer.data.ocr.eval.HayaiOnnxEvalRunner"
DEVICE_DIR="files/ocr-eval"
MODEL_SRC="${HAYAI_ANDROID_MODELS:?set HAYAI_ANDROID_MODELS to the directory holding the .onnx graphs, position_base.f32 and tokenizer.json}"

cd "$EVAL_DIR"
.venv/bin/python -m ocr_eval.validate_gt
GT_FINGERPRINT="$(.venv/bin/python -m ocr_eval.fingerprint)"

cd "$REPO_ROOT"
./gradlew :data:installDebugAndroidTest

# adb push 로 공유 저장소에 넣은 파일은 SELinux 라벨 때문에 테스트 앱이 읽지 못한다. run-as 로 앱 내부 저장소에 푼다.
# 이전 실행의 out/ 이 남아 이번 결과로 읽히지 않도록 매번 비운다.
adb shell "run-as $TEST_PKG rm -rf $DEVICE_DIR"
echo "== pushing evaluation data"
COPYFILE_DISABLE=1 tar --no-mac-metadata -C "$EVAL_DIR/data" -cf - gt pages \
  | adb exec-in "run-as $TEST_PKG sh -c 'mkdir -p $DEVICE_DIR && tar -xf - -C $DEVICE_DIR'"
echo "== pushing models ($(du -sh "$MODEL_SRC" | cut -f1))"
COPYFILE_DISABLE=1 tar --no-mac-metadata -C "$MODEL_SRC" -cf - . \
  | adb exec-in "run-as $TEST_PKG sh -c 'mkdir -p $DEVICE_DIR/hayai-onnx && tar -xf - -C $DEVICE_DIR/hayai-onnx'"

RUN_DIR="$EVAL_DIR/runs/$(date +%Y%m%d-%H%M%S)-android-hayai-onnx"
PARTIAL_DIR="$RUN_DIR.partial"
mkdir -p "$PARTIAL_DIR"
COMMIT="$(git rev-parse --short HEAD)"
if ! git diff --quiet HEAD || [ -n "$(git ls-files --others --exclude-standard -- data domain presentation android-app ios-app eval)" ]; then
  COMMIT="$COMMIT-dirty"
fi
RAW_OFFSET="$(date +%z)"
CREATED_AT="$(date +%Y-%m-%dT%H:%M:%S)${RAW_OFFSET:0:3}:${RAW_OFFSET:3:2}"
MODEL_SHAS="$(cd "$MODEL_SRC" && for f in *.onnx *.data; do [ -f "$f" ] && printf '"%s": "%s", ' "$f" "$(shasum -a 256 "$f" | cut -c1-16)"; done | sed 's/, $//')"
cat > "$PARTIAL_DIR/meta.json" <<EOF
{
  "runner": "android-hayai-onnx",
  "model": "JustANormalTinkerer/hayai-ocr-v2 (onnx int8, shared weights, Kotlin host port)",
  "revision": "4cf1398f8d9a56a2d4bd7c1d2fb7648773660a62",
  "commit": "$COMMIT",
  "device": "$(adb shell getprop ro.product.model | tr -d '\r')",
  "android": "$(adb shell getprop ro.build.version.release | tr -d '\r')",
  "artifact_sha256": { $MODEL_SHAS },
  "artifacts_resolved_via": "HAYAI_ANDROID_MODELS=$MODEL_SRC",
  "settings": "onnxruntime-android, CPU execution provider; graphs exported via torch.onnx.export(dynamo=True) opset 18 from the same static modules the CoreML conversion uses, dynamic weight-only int8, prefill and decode sharing one external-data blob; host side is the Kotlin port (HayaiHost + HayaiOcrEngine + HayaiTokenizer) rather than hayai-ocr's Python -- PIL-exact uint8 resize and position-embedding resample, 2D mRoPE, block-causal masks, greedy argmax, repetition_penalty=1.0, max_new_tokens=64",
  "created_at": "$CREATED_AT",
  "gt_fingerprint": "$GT_FINGERPRINT"
}
EOF

echo "== running $TEST_CLASS#recognition"
adb shell am instrument -w -e class "$TEST_CLASS#recognition" "$TEST_PKG/$RUNNER" \
  | tee "$PARTIAL_DIR/instrument-recognition.log"
if grep -qE "FAILURES|Process crashed|INSTRUMENTATION_FAILED" "$PARTIAL_DIR/instrument-recognition.log"; then
  echo "instrumentation failed: $PARTIAL_DIR/instrument-recognition.log" >&2; exit 1
fi
# assumeTrue 로 건너뛴 실행은 out/ 을 쓰지 않는다. 조용히 통과로 읽히지 않도록 여기서 걸러낸다.
if grep -q "OK (0 tests)" "$PARTIAL_DIR/instrument-recognition.log"; then
  echo "test was skipped, not run: $PARTIAL_DIR/instrument-recognition.log" >&2; exit 1
fi
adb exec-out "run-as $TEST_PKG cat $DEVICE_DIR/out/recognition.json" > "$PARTIAL_DIR/recognition.json"
if ! "$EVAL_DIR/.venv/bin/python" -c "import json,sys; json.load(open(sys.argv[1],encoding='utf-8'))" "$PARTIAL_DIR/recognition.json" 2>/dev/null; then
  echo "missing or invalid recognition.json: $PARTIAL_DIR/instrument-recognition.log" >&2; exit 1
fi

mv "$PARTIAL_DIR" "$RUN_DIR"
cd "$EVAL_DIR"
.venv/bin/python -m ocr_eval.score "$RUN_DIR"
echo "$RUN_DIR"
