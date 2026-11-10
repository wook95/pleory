# pleos-hello

Pleos Connect(42dot 차량 IVI, Android Automotive 기반)용 앱. 공식 시작 가이드:
https://document.pleos.ai/docs/pleos-only/getting-started

세션별 진행 상황은 여기 적지 않는다 — `~/.claude/handoff/` (`/wrap`) 에 남긴다.
이 파일에는 세션이 바뀌어도 유지되는 사실만 둔다.

## 빌드

- Gradle 9.8.2 wrapper, AGP 9.4.1. Kotlin 은 AGP 내장이라 `org.jetbrains.kotlin.android` 플러그인을 넣지 않는다.
- compileSdk / targetSdk 34, minSdk 28 (Pleos 요구사항: 서명 키 로테이션·lineage).
- **applicationId `com.pplab.pleory`, Kotlin 패키지·namespace `ai.pleos.hello`** (2026-11-08 변경). 컴포넌트 이름은 `com.pplab.pleory/ai.pleos.hello.MainActivity` 처럼 전체 클래스명으로 쓴다 — `com.pplab.pleory/.MainActivity` 는 없는 클래스를 가리킨다.
- JDK 는 Android Studio 번들 JBR: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (`~/.zshrc` 에 설정됨).

- 제출용 APK 는 `./gradlew assembleRelease` → `apk/PPLab_Pleory_release.apk`(키스토어·비밀번호는 `local.properties` 의 `release.*`). debug 와 서명이 달라 서로 바꿔 깔면 uninstall 이 필요하고 앱 데이터가 지워진다 — 데모 녹화를 끝낸 뒤 release 를 시험한다.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.pplab.pleory/ai.pleos.hello.MainActivity
adb logcat -s PleosHello:I
```

## Vehicle SDK

- 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/` (로그인 불필요)
- **`ai.pleos.playground:Vehicle:2.3.1` 은 저장소에 없다 (404).** 시작 가이드가 2.3.1 이라고 적었지만 틀렸다.
  존재하는 버전은 2.3.2(최신)와 2.0.3. 새 버전 확인:
  `curl -s https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/maven-metadata.xml`
- 패키지: `ai.pleos.playground.vehicle.Vehicle`, `...vehicle.api.model.area.DoorArea`, `...vehicle.constant.area.DoorValue`.
  문서와 시그니처가 다르면 AAR 의 `classes.jar` 를 `javap` 로 직접 확인한다.
- AAR 이 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 권한을 자동 merge 하고, 차량 서비스 최소 버전 2.3.13 을 요구한다.
- `initialize()` 후 쓰고, `onDestroy()` 에서 `release()`.

## 그 외 Pleos SDK (같은 저장소, 2026-11 확인)

- `SpeechToText:3.3.1`, `TextToSpeech:3.3.1`, `NaviHelper:3.0.5`, `FusedLocation:1.1.3`, `LLM:2.1.3.1`.
  에뮬레이터 서비스(caas 4.2.10, navi 3.0.4, vehicle 2.3.22)가 각 SDK 최소 버전을 만족한다.
- STT/TTS/LLM 은 `pleos.car.permission.{STT,TTS,LLM}_SERVICE` 를 **직접 선언**해야 한다(자동 merge 안 됨, 없으면 SecurityException).
- STT/TTS/LLM 의 SERVER·HYBRID 모드는 Pleos Playground My Project 의 Client ID/Secret 으로 `registerApp()` 필요. ON_DEVICE 는 불필요.
  STT SERVER: `registerApp` 연결·토큰 발급은 되지만 인식 스트림이 gRPC `PERMISSION_DENIED: Unsupported endpoint or you do not have permission`(2026-11-08, 옛·새 applicationId 둘 다 같음 → 패키지 문제가 아니라 프로젝트 권한 문제). on-device 는 정상.
- 자격증명은 `local.properties` 의 `pleos.clientId` / `pleos.clientSecret` → `BuildConfig.PLEOS_CLIENT_ID/SECRET`.
- Pleos LLM SERVER: registerApp 은 되지만 generateContent 가 HTTP 400 — 프로젝트 토큰에 `/bsm/v1/runbsm` 권한 없음(게이트웨이 `e:cloud:gateway:00401`, 2026-11-08 확인). Playground 에서 LLM 권한을 받아야 한다. 공개 SDK 에는 UA 토큰 경로가 없다. 서비스가 contextMap(좌표 등)을 붙이는데 현재는 하드코딩 템플릿 값으로 보이며 앱에서 끌 수 없다.
- 구조화 LLM 체인: Claude `claude-haiku-5-5`(공식 Java SDK `com.anthropic:anthropic-java`, 4.5초) → Gemini `gemini-2.5-flash` → RAW, 전체 8초. Claude 키는 `local.properties` `llm.claudeKey` 또는 셸 `PLEORY_CLAUDE_KEY`(→ `BuildConfig.CLAUDE_API_KEY`, 비면 Claude 건너뜀). **`ANTHROPIC_API_KEY` 는 읽지 않는다**(사용자 Claude Code 과금 키). Gemini 키는 `llm.geminiKey` 또는 셸 `GEMINI_API_KEY`(→ `BuildConfig.GEMINI_API_KEY`). logcat `PleoryFlow` 에 `llm ok: claude` / `llm <단계> failed: <분류>`. 셸 env 를 쓰려면 `zsh -ic './gradlew assembleDebug'` 처럼 env 가 있는 셸에서 빌드(데몬이 env 없이 떠 있으면 `./gradlew --stop`). 앱 내 확인: `adb shell am start -n com.pplab.pleory/ai.pleos.hello.spike.GeminiSpikeActivity`.
- STT `sendAudio()` 는 ON_DEVICE 전용 — SERVER 모드 시험은 실제 마이크로.
- 에뮬레이터: on-device LLM 비활성. on-device STT·TTS 한국어 동작 — 로케일은 `Locale.KOREA`(`Locale.KOREAN` 은 unsupported).
- TTS 는 `initialize(listener)` 성공 뒤에 `addEventListener` (먼저 부르면 lateinit 크래시).
- Odometer 는 에뮬레이터에서 0.0, trip 거리는 ERROR_NOT_SUPPORTED_PROPERTY.
- 공식 샘플: github.com/Pleos-Playground 의 `pleos-sample-vehicle-app`, `-navi-app`, `-fusedlocation-app` (LICENSE 없음 → 패턴만 참고, 코드 복사 금지).
  Pleos 전용 테마·폰트·버튼 dp 리소스는 샘플에 없다(최소 터치 48dp 만 확인). 샘플 vehicle 의 libs.versions.toml 은 404 인 2.3.1 을 가리켜 그대로는 빌드 안 된다.
  FusedLocation 순서: `isSupported()` → 권한 허용 → `initialize()` → 콜백 등록. 근접 알림 `addProximityAlert` 도 있다.
- SDK 스파이크 화면: `adb shell am start -n com.pplab.pleory/ai.pleos.hello.spike.SpikeActivity`, `adb logcat -s PleorySpike:I` (debug 전용).

## 권한

- 차량 권한(예: `pleos.car.permission.CAR_DOORS`)은 매니페스트 선언만으로 설치 시 부여된다.
  **예외: `pleos.car.permission.FUSED_LOCATION` 은 dangerous** — 런타임 요청 필요(없으면 `start()` 가 SecurityException).
  에뮬레이터 수동 부여: `adb shell pm grant --user 10 com.pplab.pleory pleos.car.permission.FUSED_LOCATION` (+ ACCESS_FINE/COARSE_LOCATION).
  `adb shell pm grant` 는 예외를 내지만 `dumpsys package com.pplab.pleory` 에서 `granted=true` 면 정상이다.
- 이름 체계: `pleos.car.permission.{CAR_X, CONTROL_CAR_X, PRIVATE_CAR_X}` — 조회는 `CAR_`, 제어(set)는 `CONTROL_CAR_`.
  X = CLIMATE, DOORS, DRIVE, ENERGY, INFO, LIGHTS, MIRRORS, SAFETY, SEATS, TOLL, WINDOWS (Vehicle AAR 기준).
  이 목록은 전부가 아니다 — 공식 샘플 매니페스트에는 `CAR_SPEED`, `CAR_POWERTRAIN`, `CAR_MILEAGE`, `CAR_TIRES`, `READ_*` 계열도 있다.
  내비: `NAVI_ROUTE`(조회), `NAVI_ROUTE_SEARCH`(경로 요청), `NAVI_CUSTOM_MAP`.
  어떤 API 가 어떤 권한을 쓰는지는 문서로 확인. 목록 자체는 AAR `classes.jar` 를 풀어 `grep -rhoa 'pleos\.car\.permission\.[A-Z_]*'`.

## 에뮬레이터

- AVD: `Pleos_Connect_API_34` (이미지 `system-images;android-34;pleos_car_emulator;arm64-v8a`, Pleos Connect v2.1.0,
  디바이스 `Pleos_Connect_v2_0`, 2560×1440, RAM 4GB, cold boot)
- 실행: `emulator -avd Pleos_Connect_API_34` — 콜드 부팅 약 22초. 부팅 확인: `adb shell getprop sys.boot_completed` 가 `1`.
- 앱 화면은 2560×1440 전체가 아니라 오른쪽 패널 **약 1682×1210** (x 853–2535, y 90–1300)에 뜬다. 왼쪽 차량 패널·하단 공조 바는 시스템 몫. 실측: logcat `HOME_SCREEN TaskViewContainer` 의 bounds.
- 화면 밀도 160 (`adb shell wm density`) — 1dp = 1px 이라 앱 영역을 dp 그대로 설계하면 된다.
- 기본 테마 `Theme.DeviceDefault` 는 앱 영역 상단에 타이틀 바(약 75px, 앱 이름 표시)를 넣는다. 전체 화면이 필요하면 `NoActionBar` 계열 테마를 쓴다.
- 앱은 **user 10** 으로 돈다. 앱 내부 파일에 넣을 땐 `adb shell run-as com.pplab.pleory --user 10 ...`.
- 기어 등 VHAL 값은 adb 로 못 바꾼다(`inject-vhal-event` 는 user 빌드라 거부) — Virtual Console 로 조작.
- 스크린샷은 `am start -W` 로 실행 완료를 기다린 뒤 찍는다 — 실행이 3초 넘게 걸려 고정 sleep 이면 빈 화면이 찍힌다.
- SDK 업데이트 사이트(Mac arm64): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
  (`~/.android/repositories.cfg` 에 등록됨. Windows/Linux 는 `crp-sys-img.xml`)
- 새 이미지 버전으로 바꿀 때는 기존 이미지를 먼저 지운다 (가이드 지시).
- Virtual Console 은 에뮬레이터 2.1.0 이상이 필요하다.
- 콜드 부팅 직후 시간대가 America/Los_Angeles 로 뜨고, 한국 좌표(`adb emu geo fix`)를 받은 뒤 Asia/Seoul 로 바뀐다. 앱 시계를 쓰는 데모는 바뀐 뒤에 시작한다(`tools/run-demo.sh` 가 기다린다).
- 녹화(`adb emu screenrecord`)는 창 있는 에뮬레이터로 한다. `-no-window` 에서는 앱 TTS 가 나오는 순간 "Error while writing audio frame" 으로 멈춘다. 호스트 부하가 높으면 창이 있어도 "hanging thread" 로 꺼질 수 있다 — 다시 띄워 다시 찍는다.
- `wm size`/`wm density` 로는 앱 창(TaskView)이 좁아지지 않는다. 좁은 창 확인은 debug `CardPreviewActivity --ei width 900`.

## 시연 영상 (`video/`, Remotion)

- `public/` 에 새 음원·영상을 넣으면 `scripts/scan-assets.mjs` 의 목록에도 넣는다. 목록에 없으면 컴포지션이 그 자산을 소리 없이 건너뛴다(렌더는 성공한다). 렌더 뒤 구간별 음량(astats RMS)으로 확인한다.
- 녹화 소리가 지지직거리면 원본 `raw.webm` 의 오디오 패킷 수 × 23ms 를 그 구간의 실제 경과 시간과 비교한다(`ffprobe -select_streams a -show_entries packet=pts_time -read_intervals A%B`). 짧으면 부하로 녹화 단계에서 소리를 잃은 것이라 편집으로는 못 고친다 — 그날을 다시 찍는다.
