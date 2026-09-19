# 번역 품질 비교

`eval/ocr`의 검수 정답 30페이지(또는 앱 OCR 출력)를 후보 번역 모델에 페이지 단위로 넣고, 블라인드 나란히 비교표를 만든다. 앱 파이프라인에는 연결하지 않는다.

## 준비

```bash
cd eval/translation
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python -m pytest
```

키는 환경 변수 또는 저장소 루트 `local.properties`(git 제외)에서 읽는다.

| 제공자 | 모델 | 환경 변수 | local.properties |
|---|---|---|---|
| `luna` | `gpt-5.6-luna` (OpenAI) | `OPENAI_API_KEY` | `openai.api.key` |
| `gemma_cf` | `@cf/google/gemma-4-26b-a4b-it` (Cloudflare Workers AI) | `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_API_TOKEN` | `cloudflare.account.id`, `cloudflare.api.token` |

## 실행

```bash
# 검수 정답 텍스트 입력
.venv/bin/python -m translation_eval.run --source gt
# 앱 OCR 출력 입력 (eval/ocr/runs 의 최신 recognition.json)
.venv/bin/python -m translation_eval.run --source ocr
# 비교표 생성 → runs/<run>/report.md, key.json
.venv/bin/python -m translation_eval.report runs/<run>
# 판정 후 A/B 매핑 공개
.venv/bin/python -m translation_eval.report runs/<run> --reveal
```

`--pages city01_p012,city01_p018`, `--limit 3`, `--providers luna`, `--prompt v1|v2|v2.1`(기본 v2.1 경량 프롬프트), `--with-image`, `--dry-run`(API 호출 없이 흐름 확인)을 쓸 수 있다. 두 실행 비교는 `python -m translation_eval.compare <run A> <run B>`.

## 읽는 법

`report.md` 첫 표에서 분모(페이지, 항목)와 누락·오류·거절 수를 먼저 본다. 이후 페이지별 표의 A/B 열은 페이지마다 무작위로 섞여 있으니 판정을 끝낸 뒤 `--reveal`로 연다. `runs/`는 저작물 텍스트를 담고 있어 git에 없다.
