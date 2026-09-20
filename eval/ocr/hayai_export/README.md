# Hayai OCR v2.1 모델 변환 도구

앱에 들어가는 Hayai 모델(Android: ONNX, iOS: CoreML)을 torch 체크포인트에서 만들어 내는 스크립트들이다.
측정용 어댑터(`ocr_eval/models/hayai_coreml.py`, `hayai_onnx.py`)와 달리 **모델을 만드는 쪽**이며,
모델을 다시 변환해야 할 때(체크포인트 갱신, 압축 레시피 변경, 배포 타깃 변경)만 쓴다.

변환 산출물은 수 GB 라 저장소에 두지 않는다. 앱에 실리는 최종본만 아래 위치에 있다.

| 플랫폼 | 위치 | 레시피 |
|---|---|---|
| Android | `data/src/androidMain/assets/hayai/` | ONNX 동적 int8, prefill·decode 가 가중치 파일 하나를 공유 |
| iOS | `data/src/iosMain/resources/hayai/` | CoreML palette6(per-tensor), 3패키지 분리, iOS 16, `CPU_AND_NE` |

## 환경

```bash
python3.13 -m venv .venv-hayai-coreml
.venv-hayai-coreml/bin/pip install -r requirements-hayai-coreml.txt
```

macOS 전용이다(coremltools 의 모델 로딩과 `MLComputePlan` 이 시스템 Core ML 을 쓴다).

체크포인트는 Hugging Face 스냅샷 디렉터리(`config.json`, `model.safetensors`, `modeling_hayai.py`, `tokenizer.json`)를
`HAYAI_CHECKPOINT_DIR` 로 넘긴다. 저장소와 리비전은 `ocr_eval/models/hayai.py` 의 `REPO_ID` / `REVISION` 에 고정돼 있다.

## 스크립트

| 파일 | 하는 일 |
|---|---|
| `hayai_static.py` | 세 그래프(encoder, prefill, decode)의 정적 shape 재작성, 실수값 RoPE, 체크포인트 로더. 나머지 스크립트가 공유한다 |
| `convert.py` | `torch.export` → CoreML mlprogram (fp16, iOS 16). 토크나이저·위치 임베딩도 함께 내보낸다 |
| `compress.py` | 변환 후 가중치 압축. 앱의 iOS 16 배포 타깃에서 유효한 MIL op 만 쓰는 레시피(int8, palette6, palette4) |
| `placement.py` | `.mlpackage` → `.mlmodelc` 컴파일, 크기, CoreML 의 compute plan(연산별 ANE/GPU/CPU 배치) 확인 |
| `parity.py` | torch 대 CoreML 출력 최대 절대 오차 |
| `torch_parity.py` | 정적 재작성본 대 체크포인트 원래 추론 경로(torch 수준) |
| `bench.py` | compute unit 설정별 항목당·그래프당 지연 |
| `ios18.py` | iOS 18 multifunction 병합 실험. 병합본이 ANE 에 로드되지 않아 **채택하지 않았다** |
| `onnx_export_static.py` | 같은 정적 그래프를 ONNX 로 내보내고 양자화 (Android 용) |
| `onnx_share_weights.py` | decode 그래프의 initializer 를 prefill 의 바이트로 돌려 가중치 파일을 하나로 합친다 |

## iOS 최종본을 다시 만드는 순서

```bash
export HAYAI_CHECKPOINT_DIR=<스냅샷 디렉터리>
PY=.venv-hayai-coreml/bin/python
$PY hayai_export/convert.py        # models/
$PY hayai_export/compress.py       # models-palette6/ 등
$PY hayai_export/parity.py         # 압축 전후 오차 확인
$PY hayai_export/placement.py      # compiled-models-palette6/*.mlmodelc
```

`compiled-models-palette6/` 의 `.mlmodelc` 세 개와 `models/` 의 `tokenizer.json`, 그리고 `position_base.npy` 를
헤더 없는 little-endian float32 로 바꾼 `position_base.f32` 가 앱에 들어가는 파일이다.

## 알려진 사실

- CoreML 경로는 맥과 iPad A16 에서 그래프 단위로 잰 적은 있으나(encoder+decode 약 30ms/항목), **앱 안에서 실기기로 실행된 적은 없다.**
- 맥은 기본값 `ALL` 에서 GPU 를 고르고 iPad A16 은 ANE 를 고른다. 맥의 지연 수치로 기기 성능을 추정하지 말 것.
- 근거와 수치는 `docs/superpowers/handoffs/2026-09-18-hayai-ocr-portability-handoff.md`.
