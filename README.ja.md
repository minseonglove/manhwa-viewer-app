# manhwa-viewer

[한국어](README.md) | [日本語](README.ja.md)

日本語の漫画を韓国語に翻訳して表示する、Android・iOS 向けの漫画ビューアです。ページを開くと吹き出しを検出して翻訳し、訳文を元の吹き出しの位置に表示します。アプリの画面表示は韓国語です。

## デモ

https://github.com/user-attachments/assets/03064cda-a606-4b79-af45-94c0726d8ee3

## スクリーンショット

![翻訳前と翻訳後](https://github.com/user-attachments/assets/63d2634d-7187-4bba-a3c6-29a8ac900352)

| 処理ステップの表示 | 選択翻訳 | ホーム |
|:-:|:-:|:-:|
| <img src="https://github.com/user-attachments/assets/d04ecad7-b654-43aa-8514-985a4dddd98f" width="260" alt="処理ステップの表示"> | <img src="https://github.com/user-attachments/assets/9ff0a6ee-a02e-4a1d-bb0e-33261a8f6d76" width="260" alt="選択翻訳"> | <img src="https://github.com/user-attachments/assets/78343635-4824-4d30-b251-4216f85ff215" width="260" alt="ホーム画面"> |

スクリーンショットと動画に登場する漫画は、あらゐけいいち『CITY』3巻（講談社）です。著作権は原著作者に帰属します。

## 主な機能

アプリ内にある表記（韓国語）は括弧内に併記しています。

- **自動翻訳**（자동 번역）: ページを開いたりめくったりすると吹き出しを検出して翻訳し、訳文を元の吹き出しの位置に表示します。オーバーレイのオン・オフで原文と見比べられます。
- **吹き出し検出**（말풍선 감지）: 翻訳はせず、吹き出しの検出と OCR までを行います。オーバーレイをオンにした時点で翻訳します。
- **選択翻訳**（선택 번역）: 選んだ吹き出しの原文と訳文を一覧で表示します。原文の漢字にはふりがなが付きます。
- **処理ステップの表示**: 検出（탐지）→ 切り出し（크롭）→ OCR → 翻訳（번역）のどこまで進んだかを表示します。
- **翻訳キャッシュ**（번역 캐시）: 一度翻訳した文は端末に保存し、再度リクエストしません。
- **ビューア**: 端末ストレージの閲覧、zip・cbz・cbr 形式の圧縮ファイルと画像の表示、単ページ・見開き・縦スクロール（ウェブトゥーン）表示、読む方向（右→左／左→右）の切り替え、続きから読む・最近読んだ作品、アプリ専用の保管庫。

## 仕組み

```
ページ画像
 ├─ 吹き出し・テキスト領域の検出（YOLO、端末内）
 ├─ 領域ごとに切り出し → OCR（Hayai OCR v2、端末内）→ 日本語の原文
 │    └─ iOS では横書きテキストを Apple Vision でも認識
 ├─ 翻訳キャッシュの確認（Room）
 ├─ 翻訳（翻訳プロキシ）
 └─ 訳文を吹き出しの位置に描画
```

- **Android**: 検出器と OCR エンコーダは LiteRT で実行します。GPU の自己チェックに通れば GPU を、通らなければ CPU を使います。OCR デコーダは ONNX Runtime で実行します。
- **iOS**: 検出器・OCR ともに Core ML で実行します。
- **ふりがな**: 形態素解析器 Lindera を Rust の FFI 経由で組み込んでいます。
- **翻訳プロキシ**: Cloudflare Workers で動く翻訳の中継サーバーです。まず Gemma で翻訳し、失敗したり時間がかかったりした場合は DeepSeek、その次に Google 翻訳へ切り替えます。端末認証（Android は Play Integrity、iOS は App Attest）を通ったアプリからのリクエストだけを受け付けます。

## プロジェクト構成

Kotlin Multiplatform と Compose Multiplatform で、Android と iOS が UI までコードを共有しています。クリーンアーキテクチャ（domain → data → presentation）と MVI パターンを採用しています。

| パス | 内容 |
|---|---|
| `domain` | 純粋な Kotlin。エンティティ、リポジトリのインターフェース、ユースケース |
| `data` | Room、DataStore、検出・OCR・翻訳の実装。プラットフォーム別のコードは `expect`/`actual` で分離 |
| `presentation` | Compose Multiplatform の画面と ViewModel |
| `android-app` | Android のエントリポイント（Koin による DI） |
| `ios-app`, `iosApp` | iOS のエントリポイントと Xcode プロジェクト（XcodeGen） |
| `lindera-ffi` | ふりがな用の Lindera C FFI（Rust） |
| `proxy` | 翻訳プロキシ（Cloudflare Workers、TypeScript） |
| `eval/ocr`, `eval/translation` | OCR・翻訳モデルの比較用評価ツール（Python、アプリとは独立） |
| `libs/libarchive` | iOS の圧縮ファイル展開用 libarchive（git submodule） |

## ビルド

### 必要なもの

- JDK 21、Android SDK（compileSdk 36）、Android NDK
- Git LFS
- Rust と以下のツール・ターゲット。ビルドスクリプトはこれらをインストールせず、足りないものがあればインストールコマンドを表示して終了します。

  ```bash
  cargo install cargo-ndk cbindgen
  rustup target add aarch64-linux-android x86_64-linux-android                # Android
  rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios  # iOS
  ```

- iOS: Apple Silicon の Mac、Xcode、XcodeGen。Kotlin の iOS ターゲットが arm64（実機・シミュレータ）のみのため、Intel Mac ではビルドできません。
- 翻訳プロキシ: Node.js、Cloudflare アカウント

動作を確認した環境: macOS（Apple Silicon）、JDK 21.0.10、Rust 1.94.1、cargo-ndk 4.1.2、cbindgen 0.29.2、Android NDK 29.0、Xcode 27.0、XcodeGen 2.45.3、Node.js 26.0.0

### ソースの取得

```bash
git clone https://github.com/minseonglove/manhwa-viewer-app.git
cd manhwa-viewer-app
git lfs pull
git submodule update --init --recursive
```

モデルファイルは Git LFS で管理しています。`libs/libarchive` サブモジュールを取得しないと、iOS の cinterop に必要なヘッダが揃いません。

### Android

1. ふりがな用のネイティブライブラリをビルドします。成果物（`data/src/androidMain/jniLibs/`）はリポジトリに含まれていないため、各自でビルドが必要です。ビルド中に IPADIC 辞書のソースをダウンロードします。

   ```bash
   lindera-ffi/scripts/build-android.sh
   ```

2. プロジェクトルートの `local.properties` に、Android SDK のパス、翻訳プロキシの URL、開発用の認証値を設定します。Android Studio でプロジェクトを一度開くと `sdk.dir` は自動で書き込まれます。環境変数 `ANDROID_HOME` が設定されていれば省略できます。

   ```properties
   sdk.dir=/Users/<you>/Library/Android/sdk
   translation.proxy.url=https://<your-worker>.workers.dev
   proxy.dev.secret=<プロキシの DEV_ATTESTATION_SECRET と同じ値>
   ```

   開発用の認証値は debug ビルドでのみ使われます。release ビルドは Play Integrity で認証するため、`play.integrity.cloud.project.number`（Play Integrity と連携した Google Cloud プロジェクトの番号）の設定が必要です。

3. ビルドします。

   ```bash
   ./gradlew :android-app:assembleDebug
   ```

release ビルドを自分の鍵で署名する場合は、プロジェクトルートに `keystore.properties` を作成します。このファイルがない場合は debug 鍵で署名されます。

```properties
storeFile=<android-app フォルダからのキーストアのパス>
storePassword=<キーストアのパスワード>
keyAlias=<鍵のエイリアス>
keyPassword=<鍵のパスワード>
```

### iOS

1. ふりがな用のヘッダと xcframework を作成します。成果物（`libs/lindera/`）はリポジトリに含まれていないため、各自でビルドが必要です。

   ```bash
   lindera-ffi/scripts/build-ios.sh
   ```

2. `iosApp/Configuration/Secrets.xcconfig` を作成します。フォルダもリポジトリにないため、あわせて作成します。xcconfig は `//` 以降をコメントとして扱うため、URL ではなくホスト名だけを書きます。

   ```bash
   mkdir -p iosApp/Configuration
   ```

   ```
   TRANSLATION_PROXY_HOST = <your-worker>.workers.dev
   TRANSLATION_PROXY_DEV_SECRET = <プロキシの DEV_ATTESTATION_SECRET と同じ値>
   ```

3. Xcode プロジェクトを生成し、Xcode でシミュレータを選んでビルドします。Kotlin のフレームワークは Xcode のビルドフェーズで Gradle が一緒にビルドします。

   ```bash
   cd iosApp
   xcodegen generate
   open iosApp.xcodeproj
   ```

   コマンドラインでビルドする場合も、シミュレータの機種を指定してください（`-destination 'platform=iOS Simulator,name=iPhone 17'`）。`generic/platform=iOS Simulator` を指定すると x86_64 までビルドしようとして失敗します。

### 自分でビルドしたアプリで使う

翻訳プロキシは登録済みのアプリ ID からのリクエストだけを受け付けます。自分のアプリ ID でビルドする場合は、以下の値を変更してプロキシの設定を合わせてください。iOS の値を変更したあとは `xcodegen generate` を再実行します。

| 項目 | アプリ側の場所 | プロキシ側（`proxy/wrangler.toml`） |
|---|---|---|
| Android のパッケージ名 | `android-app/build.gradle.kts` の `applicationId` | `ANDROID_PACKAGE_NAME` |
| iOS の Team ID、Bundle ID | `iosApp/project.yml` の `DEVELOPMENT_TEAM`、`PRODUCT_BUNDLE_IDENTIFIER` | `IOS_APP_ID`（`<Team ID>.<Bundle ID>` の形式） |

iOS の実機で動かすには Apple Developer の Team で署名する必要があり、App Attest の権限（`iosApp/iosApp/iosApp.entitlements`）を含むプロビジョニングが必要です。

### 翻訳プロキシ

```bash
cd proxy
npm install
npx wrangler login
npm run deploy
npx wrangler secret bulk secrets.json
```

デプロイ後に表示される `workers.dev` の URL をアプリの設定（`translation.proxy.url`、`TRANSLATION_PROXY_HOST`）に設定します。`secrets.json` は以下の形式で作成し、登録後は削除してください。リポジトリにはコミットしません。

```json
{
  "SESSION_SIGNING_KEY": "<openssl rand -base64 32 で生成した値>",
  "DEEPINFRA_API_KEY": "<DeepInfra の API キー>",
  "GOOGLE_TRANSLATE_API_KEY": "<Google Cloud Translation の API キー>",
  "DEV_ATTESTATION_SECRET": "<開発用の認証値>"
}
```

| シークレット | 必須 | 用途 |
|---|:-:|---|
| `SESSION_SIGNING_KEY` | ✓ | セッショントークンの署名。任意の長い文字列 |
| `DEEPINFRA_API_KEY` | ✓ | DeepSeek による翻訳（DeepInfra） |
| `GOOGLE_TRANSLATE_API_KEY` | ✓ | Google 翻訳 |
| `PLAY_INTEGRITY_SERVICE_ACCOUNT` | | Android の release ビルドの認証。Play Integrity と連携した Google Cloud プロジェクトのサービスアカウント JSON キー |
| `DEV_ATTESTATION_SECRET` | | debug ビルドの認証。本番用の Worker からは削除します |

Gemma は Workers AI のバインディングで動くため、キーは不要です。

デプロイせずにローカルで確認する場合は、次を実行します。

```bash
npm run typecheck
npm test
npm run test:workerd
```

## モデル

| 用途 | モデル | 場所 |
|---|---|---|
| OCR | [Hayai OCR v2](https://huggingface.co/JustANormalTinkerer/hayai-ocr-v2) を LiteRT・ONNX（Android）、Core ML（iOS）に変換 | `data/src/androidMain/assets/hayai/`, `data/src/iosMain/resources/hayai/` |
| 吹き出し・テキスト検出 | YOLO ベースの検出器を LiteRT（Android）、Core ML（iOS）に変換 | `data/src/androidMain/assets/mangaocr/`, `data/src/iosMain/resources/mangaocr/` |

Hayai の変換スクリプトは `eval/ocr/hayai_export/` にあります。

## リポジトリに含めていないもの

- **インストルメンテーションテスト用の漫画ページ**（`data/src/androidInstrumentedTest/assets/test/`）: 購入した出版物をスキャンした画像のため含めていません。画像がない場合、該当するテストはスキップされます。自分で所有している画像をこのパスに置くと実行されます。
- **シークレット**: `local.properties`、`iosApp/Configuration/Secrets.xcconfig`、`keystore.properties`
