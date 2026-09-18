# OCR 평가

실제 만화 페이지와 검수된 정답으로 OCR 품질을 잰다. 설계: `docs/superpowers/specs/2026-09-17-ocr-eval-infra-design.md`

## 준비

```bash
cd eval/ocr
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python -m pytest
```

`data/`, `runs/`는 저작물과 정답을 담고 있어 git에 없다. 저장소 밖에 백업한다.

## 측정

| 목적 | 명령 |
|---|---|
| 앱(실기기) 인식 + 파이프라인 | `./run_device.sh` |
| 앱 인식만 | `./run_device.sh recognition` |
| Mac 기준 모델 | `.venv/bin/python -m ocr_eval.run_reference [--model ID] [--device cpu|mps]` 후 `.venv/bin/python -m ocr_eval.score <run>` |
| Mac 모델 어댑터 | `.venv/bin/python -m ocr_eval.run_model --model <adapter> [--device auto|cpu|mps]` 후 `.venv/bin/python -m ocr_eval.score <run>` — `<adapter>`는 `ocr_eval/models/` 아래 모듈 이름 (예: `baberu`) |
| 두 실행 비교 | `.venv/bin/python -m ocr_eval.score <run B> --compare <run A>` |

결과는 `runs/<시각>-<실행기>/report.md`. 첫 줄의 분모(`pages`, `items`, `missing`, `unknown`)부터 확인한다.

### 모델 어댑터

새 모델 어댑터를 추가할 때 지켜야 할 계약은 `ocr_eval/models/__init__.py`의 모듈 docstring에 정의되어 있다.

| 어댑터 | 모델 | 실행 venv | 비고 |
|---|---|---|---|
| `baberu` | `genshiai-daichi/baberu-ocr` | `.venv` | 공식 ONNX(무손실 등급), CPU 전용 (`--device mps` 거부) |
| `hayai` | `JustANormalTinkerer/hayai-ocr-v2` | `.venv` | 공식 `hayai-ocr` 라이브러리, MPS. 매 load마다 `google/siglip2-base-patch16-naflex` 상류 revision을 온라인으로 확인하며 이동했으면 실행을 거부한다 |
| `paddleocr_vl_manga` | `jzhang533/PaddleOCR-VL-For-Manga` | `.venv-paddleocr_vl_manga` | transformers 4.57.1 필요, MPS |
| `hayai_litert_export` | 직접 내보낸 LiteRT 그래프 | `.venv-hayai-litert` | `HAYAI_EXPORT_DIR`의 `.tflite`를 잰다. `HAYAI_EXPORT_MODE=split`은 저자와 같은 3파일 구성, `merged`는 prefill/decode를 시그니처 둘로 합친 1파일 구성 |

`paddleocr_vl_manga`는 전용 venv에서 실행하고, 채점은 `.venv`로 한다.

```bash
python3 -m venv .venv-paddleocr_vl_manga
.venv-paddleocr_vl_manga/bin/pip install -r requirements-paddleocr_vl_manga.txt
.venv-paddleocr_vl_manga/bin/python -m ocr_eval.run_model --model paddleocr_vl_manga
.venv/bin/python -m ocr_eval.score runs/<run>
```

`hayai_litert_export`가 재는 `.tflite`는 `requirements-hayai-export.txt`의 `.venv-hayai-export`에서 만든다(내보내기 전용 venv이며 측정에는 쓰지 않는다).

```bash
HAYAI_EXPORT_DIR=<dir> HAYAI_EXPORT_MODE=split \
  .venv-hayai-litert/bin/python -m ocr_eval.run_model --model hayai_litert_export --device cpu
.venv/bin/python -m ocr_eval.score runs/<run>

HAYAI_EXPORT_DIR=<dir> HAYAI_EXPORT_MODE=merged \
  .venv-hayai-litert/bin/python -m ocr_eval.run_model --model hayai_litert_export --device cpu
.venv/bin/python -m ocr_eval.score runs/<run>
```

두 실행을 `.venv/bin/python -m ocr_eval.score runs/<merged> --compare runs/<split>`로 비교하는 것이 이 어댑터가 존재하는 이유다 -- 디코더 두 그래프를 한 파일로 합쳐도 모델이 읽는 내용이 바뀌지 않는지 확인한다.

`run_model`의 `--device` 기본값은 `auto`(MPS가 있으면 MPS)이고, `run_reference`의 기본값은 `cpu`다.

### Android 실행 대상

- 대상은 `ANDROID_SERIAL`로 고른다: `ANDROID_SERIAL=<ANDROID_SERIAL> ./run_device.sh`. 기기가 여러 대 연결돼 있으면 필수다.
- 실기기가 없으면 arm64 에뮬레이터로 돌릴 수 있다 (예: `emulator -avd Pixel_9a -no-window`). 결과 `meta.json`의 `device`/`avd`로 실기기 결과와 구분되며, 수치를 실기기 결과와 같다고 보지 않는다.
- 테스트 APK가 모델을 포함해 약 220MB라 에뮬레이터 데이터 파티션에 여유 공간이 필요하다.
- 데이터는 `run-as com.manhwaviewer.data.test`로 테스트 앱 내부 저장소(`files/ocr-eval`)에 넣고 꺼낸다. 공유 저장소에 `adb push`한 파일은 SELinux 라벨 때문에 테스트 앱이 읽지 못한다.

## 지표

- 인식 단독: 정답 박스(앱과 같은 여백 규칙)를 크롭해 읽힌 CER. 대표 점수는 `primary`(말풍선 + 내레이션)
- 파이프라인: 페이지를 앱에 넣은 결과를 정답과 겹침(교집합/작은 쪽 넓이 ≥ 0.5)으로 연결. 검출률, 연결된 것만의 CER, 누락을 포함한 종합 CER, 오검출 블록 수
- strict는 공백만 제거, lenient는 NFKC·말줄임·물결·장식 기호까지 정규화

## 데이터셋 준비

1. `.venv/bin/python -m ocr_eval.fetch_volume --device-path /sdcard/Download/city01.zip --name city01` — 기기에서 원본 볼륨을 내려받아 `data/volumes/`에 풀고 접촉지(contact sheet)를 만든다
2. 접촉지로 페이지를 고른 뒤 `.venv/bin/python -m ocr_eval.select_pages --name city01 --source /sdcard/Download/city01.zip --selection '[{"file": "0012.jpg", "reason": "말풍선+내레이션 혼합"}]'` — 고른 이미지를 `data/pages/`에 `<name>_p<번호>.jpg`로 복사

## 정답 추가·수정

1. `.venv/bin/python -m ocr_eval.propose_boxes <page>` — 외부 검출 모델 후보
2. `.venv/bin/python -m ocr_eval.draft_gt --page <page>` — 초안 (`"draft": true`)
3. `.venv/bin/python -m ocr_eval.render_review --page <page> [--gt | --quadrants | --box X Y W H]` — 검수 이미지. 모드 플래그를 생략하면 후보 박스(propose_boxes 결과) 크롭을 렌더링한다
4. `data/gt/<page>.json` 편집 후 `draft` 제거
5. `.venv/bin/python -m ocr_eval.validate_gt`

표기 규칙: 후리가나 제외, 보이는 표기 그대로, 항목 안 줄바꿈·공백 없음, 읽을 수 없는 효과음은 `text: ""` + `unreadable: true`.

정답 박스(bbox)를 수정하면 그 페이지를 사용한 기존 실행은 무효(`gt=STALE`)가 된다. `score`가 STALE을 표시하면 해당 실행을 다시 만들어야 한다. 텍스트만 고치는 것은 실행을 무효화하지 않는다.

## 새 실행기 추가

`runs/<시각>-<이름>/`에 `meta.json`과 `recognition.json`(`{"<page>/<id>": {"text", "ms"}}`) 또는 `pipeline.json`(`{"<page>": {"ms", "blocks": [{"bbox", "text"}]}}`)을 쓰면 `score`가 그대로 채점한다. 크롭은 `ocr_eval.crop.crop_item`을 쓴다. `recognition.json`은 읽을 수 없는(`unreadable`) 항목도 다른 실행기와 동일하게 포함해야 한다(채점에서는 제외되지만 실행 결과의 항목 수를 맞추기 위함이다). `meta.json`에는 실행 당시 정답과의 대응을 검증할 `gt_fingerprint`(`ocr_eval.fingerprint.gt_fingerprint`)를 반드시 넣는다.
