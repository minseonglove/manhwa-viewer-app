# manhwa-viewer

[한국어](README.md) | [日本語](README.ja.md)

일본어 만화를 한국어로 번역해 주는 Android·iOS 만화 뷰어입니다. 페이지를 열면 말풍선을 찾아 번역하고, 번역문을 원래 말풍선 자리에 보여 줍니다.

## 시연

https://github.com/user-attachments/assets/03064cda-a606-4b79-af45-94c0726d8ee3

## 스크린샷

![번역 전과 번역 후](https://github.com/user-attachments/assets/63d2634d-7187-4bba-a3c6-29a8ac900352)

| 처리 단계 표시 | 선택 번역 | 홈 |
|:-:|:-:|:-:|
| <img src="https://github.com/user-attachments/assets/d04ecad7-b654-43aa-8514-985a4dddd98f" width="260" alt="처리 단계 표시"> | <img src="https://github.com/user-attachments/assets/9ff0a6ee-a02e-4a1d-bb0e-33261a8f6d76" width="260" alt="선택 번역"> | <img src="https://github.com/user-attachments/assets/78343635-4824-4d30-b251-4216f85ff215" width="260" alt="홈 화면"> |

스크린샷과 영상에 나오는 만화는 あらゐけいいち 『CITY』 3권(講談社)이며, 저작권은 원저작자에게 있습니다.

## 주요 기능

- **자동 번역**: 페이지를 열거나 넘기면 말풍선을 찾아 번역하고, 번역문을 원래 말풍선 자리에 표시합니다. 오버레이를 껐다 켜며 원문과 비교할 수 있습니다.
- **말풍선 감지**: 번역은 하지 않고 말풍선 검출과 OCR까지만 합니다. 오버레이를 켜면 그때 번역합니다.
- **선택 번역**: 고른 말풍선의 원문과 번역문을 목록으로 보여 줍니다. 원문의 한자에는 후리가나가 달립니다.
- **처리 단계 표시**: 탐지 → 크롭 → OCR → 번역 중 어디까지 진행됐는지 보여 줍니다.
- **번역 캐시**: 한 번 번역한 문장은 기기에 저장해 두고 다시 요청하지 않습니다.
- **뷰어**: 기기 저장소 탐색, zip·cbz·cbr 압축 파일과 이미지 열기, 단면·양면·웹툰 보기, 읽기 방향(RTL/LTR) 전환, 이어서 보기와 최근 감상, 앱 전용 보관함.

## 동작 방식

```
페이지 이미지
 ├─ 말풍선·텍스트 영역 검출 (YOLO, 기기 안)
 ├─ 영역별 크롭 → OCR (Hayai OCR v2, 기기 안) → 일본어 원문
 │    └─ iOS는 가로 텍스트를 Apple Vision으로도 인식
 ├─ 번역 캐시 확인 (Room)
 ├─ 번역 (번역 프록시)
 └─ 번역문을 말풍선 자리에 렌더링
```

- **Android**: 검출기와 OCR 인코더는 LiteRT로 실행합니다. GPU 자가 검사를 통과하면 GPU를, 아니면 CPU를 씁니다. OCR 디코더는 ONNX Runtime으로 실행합니다.
- **iOS**: 검출기와 OCR 모두 Core ML로 실행합니다.
- **후리가나**: Lindera 형태소 분석기를 Rust FFI로 연결했습니다.
- **번역 프록시**: Cloudflare Workers로 만든 번역 중계 서버입니다. Gemma로 먼저 번역하고, 실패하거나 늦으면 DeepSeek, 그다음 Google 번역으로 넘어갑니다. 기기 인증(Android는 Play Integrity, iOS는 App Attest)을 통과한 앱의 요청만 받습니다.

## 프로젝트 구조

Kotlin Multiplatform과 Compose Multiplatform으로 Android와 iOS가 UI까지 코드를 공유합니다. 클린 아키텍처(domain → data → presentation)와 MVI 패턴을 씁니다.

| 경로 | 내용 |
|---|---|
| `domain` | 순수 Kotlin. 엔티티, 저장소 인터페이스, 유스케이스 |
| `data` | Room, DataStore, 검출·OCR·번역 구현. 플랫폼별 코드는 `expect`/`actual`로 나눔 |
| `presentation` | Compose Multiplatform 화면과 ViewModel |
| `android-app` | Android 진입점 (Koin DI) |
| `ios-app`, `iosApp` | iOS 진입점과 Xcode 프로젝트 (XcodeGen) |
| `lindera-ffi` | 후리가나용 Lindera C FFI (Rust) |
| `proxy` | 번역 프록시 (Cloudflare Workers, TypeScript) |
| `eval/ocr`, `eval/translation` | OCR·번역 모델 비교용 평가 도구 (Python, 앱과 별개) |
| `libs/libarchive` | iOS 압축 해제용 libarchive (git submodule) |

## 빌드

### 준비물

- JDK 21, Android SDK (compileSdk 36), Android NDK
- Git LFS
- Rust와 아래 도구·타깃. 빌드 스크립트는 이것들을 설치하지 않고, 빠진 것이 있으면 설치 명령을 알려 주고 멈춥니다.

  ```bash
  cargo install cargo-ndk cbindgen
  rustup target add aarch64-linux-android x86_64-linux-android                # Android
  rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios  # iOS
  ```

- iOS: Apple Silicon Mac, Xcode, XcodeGen. Kotlin iOS 타깃이 arm64(기기·시뮬레이터)뿐이라 Intel Mac에서는 빌드되지 않습니다.
- 번역 프록시: Node.js, Cloudflare 계정

확인한 환경: macOS (Apple Silicon), JDK 21.0.10, Rust 1.94.1, cargo-ndk 4.1.2, cbindgen 0.29.2, Android NDK 29.0, Xcode 27.0, XcodeGen 2.45.3, Node.js 26.0.0

### 소스 받기

```bash
git clone https://github.com/minseonglove/manhwa-viewer-app.git
cd manhwa-viewer-app
git lfs pull
git submodule update --init --recursive
```

모델 파일은 Git LFS로 관리합니다. `libs/libarchive` 서브모듈을 받지 않으면 iOS cinterop에 필요한 헤더가 없습니다.

### Android

1. 후리가나용 네이티브 라이브러리를 빌드합니다. 빌드 결과물(`data/src/androidMain/jniLibs/`)은 저장소에 없으므로 직접 만들어야 합니다. 빌드 중에 IPADIC 사전 원본을 내려받습니다.

   ```bash
   lindera-ffi/scripts/build-android.sh
   ```

2. 프로젝트 루트의 `local.properties`에 Android SDK 경로, 번역 프록시 주소, 개발용 인증 값을 넣습니다. Android Studio로 프로젝트를 한 번 열면 `sdk.dir`은 자동으로 들어가고, `ANDROID_HOME` 환경 변수가 있으면 생략해도 됩니다.

   ```properties
   sdk.dir=/Users/<you>/Library/Android/sdk
   translation.proxy.url=https://<your-worker>.workers.dev
   proxy.dev.secret=<프록시의 DEV_ATTESTATION_SECRET과 같은 값>
   ```

   개발용 인증 값은 debug 빌드에서만 쓰입니다. release 빌드는 Play Integrity로 인증하므로 `play.integrity.cloud.project.number`(Play Integrity와 연결된 Google Cloud 프로젝트 번호)를 넣어야 합니다.

3. 빌드합니다.

   ```bash
   ./gradlew :android-app:assembleDebug
   ```

release 빌드에 자기 키로 서명하려면 프로젝트 루트에 `keystore.properties`를 만듭니다. 파일이 없으면 debug 키로 서명합니다.

```properties
storeFile=<android-app 폴더 기준 키스토어 경로>
storePassword=<키스토어 비밀번호>
keyAlias=<키 별칭>
keyPassword=<키 비밀번호>
```

### iOS

1. 후리가나용 헤더와 xcframework를 만듭니다. 빌드 결과물(`libs/lindera/`)은 저장소에 없으므로 직접 만들어야 합니다.

   ```bash
   lindera-ffi/scripts/build-ios.sh
   ```

2. `iosApp/Configuration/Secrets.xcconfig`를 만듭니다. 폴더도 저장소에 없으니 함께 만듭니다. xcconfig는 `//` 뒤를 주석으로 읽기 때문에 URL이 아니라 호스트만 적습니다.

   ```bash
   mkdir -p iosApp/Configuration
   ```

   ```
   TRANSLATION_PROXY_HOST = <your-worker>.workers.dev
   TRANSLATION_PROXY_DEV_SECRET = <프록시의 DEV_ATTESTATION_SECRET과 같은 값>
   ```

3. Xcode 프로젝트를 만들고, Xcode에서 시뮬레이터를 골라 빌드합니다. Kotlin 프레임워크는 Xcode 빌드 단계에서 Gradle이 함께 빌드합니다.

   ```bash
   cd iosApp
   xcodegen generate
   open iosApp.xcodeproj
   ```

   명령줄로 빌드할 때도 시뮬레이터 기기를 지정합니다(`-destination 'platform=iOS Simulator,name=iPhone 17'`). `generic/platform=iOS Simulator`로 지정하면 x86_64까지 빌드하려다 실패합니다.

### 직접 빌드한 앱으로 쓰기

번역 프록시는 등록된 앱 ID의 요청만 받습니다. 자기 앱 ID로 빌드하려면 아래 값을 바꾸고 프록시 설정을 맞춥니다. iOS 값을 바꾼 뒤에는 `xcodegen generate`를 다시 실행합니다.

| 항목 | 앱 쪽 위치 | 프록시 쪽 (`proxy/wrangler.toml`) |
|---|---|---|
| Android 패키지명 | `android-app/build.gradle.kts`의 `applicationId` | `ANDROID_PACKAGE_NAME` |
| iOS Team ID, Bundle ID | `iosApp/project.yml`의 `DEVELOPMENT_TEAM`, `PRODUCT_BUNDLE_IDENTIFIER` | `IOS_APP_ID` (`<Team ID>.<Bundle ID>` 형식) |

iOS 실기기에서 실행하려면 Apple Developer Team으로 서명해야 하고, App Attest 권한(`iosApp/iosApp/iosApp.entitlements`)이 들어간 프로비저닝이 필요합니다.

### 번역 프록시

```bash
cd proxy
npm install
npx wrangler login
npm run deploy
npx wrangler secret bulk secrets.json
```

배포하면 나오는 `workers.dev` 주소를 앱 설정(`translation.proxy.url`, `TRANSLATION_PROXY_HOST`)에 넣습니다. `secrets.json`은 아래 형식으로 만들고, 등록한 뒤에는 지웁니다. 저장소에 커밋하지 않습니다.

```json
{
  "SESSION_SIGNING_KEY": "<openssl rand -base64 32 로 만든 값>",
  "DEEPINFRA_API_KEY": "<DeepInfra API 키>",
  "GOOGLE_TRANSLATE_API_KEY": "<Google Cloud Translation API 키>",
  "DEV_ATTESTATION_SECRET": "<개발용 인증 값>"
}
```

| 비밀값 | 필수 | 용도 |
|---|:-:|---|
| `SESSION_SIGNING_KEY` | ✓ | 세션 토큰 서명. 임의의 긴 문자열 |
| `DEEPINFRA_API_KEY` | ✓ | DeepSeek 번역 (DeepInfra) |
| `GOOGLE_TRANSLATE_API_KEY` | ✓ | Google 번역 |
| `PLAY_INTEGRITY_SERVICE_ACCOUNT` | | Android release 빌드 인증. Play Integrity와 연결된 Google Cloud 프로젝트의 서비스 계정 JSON 키 |
| `DEV_ATTESTATION_SECRET` | | debug 빌드 인증. 배포용 Worker에서는 지웁니다 |

Gemma는 Workers AI 바인딩으로 실행되므로 키가 필요 없습니다.

배포하지 않고 로컬에서 확인할 때는 다음을 실행합니다.

```bash
npm run typecheck
npm test
npm run test:workerd
```

## 모델

| 용도 | 모델 | 위치 |
|---|---|---|
| OCR | [Hayai OCR v2](https://huggingface.co/JustANormalTinkerer/hayai-ocr-v2)를 LiteRT·ONNX(Android), Core ML(iOS)로 변환 | `data/src/androidMain/assets/hayai/`, `data/src/iosMain/resources/hayai/` |
| 말풍선·텍스트 검출 | YOLO 기반 검출기를 LiteRT(Android), Core ML(iOS)로 변환 | `data/src/androidMain/assets/mangaocr/`, `data/src/iosMain/resources/mangaocr/` |

Hayai 변환 스크립트는 `eval/ocr/hayai_export/`에 있습니다.

## 저장소에 넣지 않은 것

- **계측 테스트용 만화 페이지** (`data/src/androidInstrumentedTest/assets/test/`): 구매한 출판물을 스캔한 이미지라 넣지 않았습니다. 이미지가 없으면 해당 테스트는 건너뜁니다. 직접 소장한 이미지를 이 경로에 두면 실행됩니다.
- **비밀값**: `local.properties`, `iosApp/Configuration/Secrets.xcconfig`, `keystore.properties`
