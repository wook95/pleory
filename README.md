# Pleory (플레오리)

▶ 시연 영상과 APK: https://github.com/wook95/pleory/releases/tag/v0.3 (`pleory-demo-v3.mp4` 1분 54초, `PPLab_Pleory_release.apk`)

## 앱 이름 및 한 줄 소개

Pleory(플레오리) — 운전 중 한마디를 카드로 남기는 Pleos Connect 앱.

운전 중 아무 말이나 길게 늘어놓아도 AI가 감정 / 일 / 아이디어로 정리해, 도착하면 묶음마다 큰 문장 한 줄로 보여 준다. 줄을 누르면 노션 템플릿처럼 정리된 상세가 열리고, 시간·장소·주행 맥락은 그 아래에 붙는다. 주행 중 앱은 먼저 말을 걸지 않고, 카드와 요약은 도착해서 기어를 P로 넣은 뒤에만 나온다.

## 주요 기능

| 스토리 | 기능 |
|---|---|
| US-01 | 기어 P→D 에서 세션이 저절로 열리고 주행 화면(S0)에 "퇴근길 · 기록 중"이 뜬다 |
| US-02 | 말하기 버튼 한 번으로 듣기 시작, 다시 누르면 취소 |
| US-03 | 저장 뒤 TTS "적어뒀어" 한 문장만 답하고 화면은 그대로 둔다(길게 말해도 대답은 한 번) |
| US-04 | 주제가 6~7개 섞인 긴 혼잣말을 LLM 이 항목(최대 8개)으로 나눠 감정·일·아이디어 묶음에 넣는다. 실패하거나 12초를 넘기면 원문으로 남긴다 |
| US-05 | 목적지 도착 + 기어 P 에서 그날 카드(S1): 맨 위에 감정 / 일 / 아이디어 대표 문장 한 줄씩, 맨 아래에 접힌 "이번 주행"(시간·거리·날씨·정체·감정 흐름) |
| US-06 | 카드가 뜨면 15초 안의 요약을 음성과 자막으로 읽는다(건너뛰기 있음) |
| US-07 | 정차 중 묶음 상세에서 잘못 들린 항목 지우기(대표 문장도 다시 만든다) |
| US-08 | 첫 실행 동의 화면(S3), 설정의 동의 철회와 전체 삭제 |
| US-09 | Gleo 에게 "플레오리 앱에서 … 적어줘"로 남기기(`ADD_DRIVE_NOTE`) |
| US-10 | 지난 기록(S2): 최근 7일 세션 목록, "할 일만" 필터, 행을 누르면 그날 카드 |
| US-11 | 도착 리마인드: 이전 주행에서 말한 미완료 할 일을 한 줄로 읽고 카드 위에 띄운다. "했어"를 누르면 다시 알리지 않는다 |
| US-12 | "폰에서 열기": 카드 내용을 QR 평문으로 담아 서버 없이 폰으로 옮긴다 |
| US-13 | 묶음 줄을 누르면 상세: 묶음 탭, 일은 체크 상자, 감정은 ↓→↑, 항목마다 시각·장소 |

카드·지난 기록·QR·설정·동의 화면은 기어 P 이고 UX 제한이 없을 때만 열리고, 주행이 시작되면 주행 화면으로 돌아간다.

## 실행 방법

### 1. 에뮬레이터

Pleos Connect 에뮬레이터 이미지(`system-images;android-34;pleos_car_emulator;arm64-v8a`)로 AVD 를 만들고 띄운다.

```bash
emulator -avd Pleos_Connect_API_34
adb shell getprop sys.boot_completed   # 1 이면 부팅 완료
```

### 2. 빌드와 설치

JDK 17 이상(Android Studio 번들 JBR 가능)이 필요하다. 키 설정은 아래 "테스트 환경 및 필요한 설정"을 본다.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

release 빌드는 `./gradlew assembleRelease` 로 만든다. `local.properties`에 `release.storeFile`, `release.storePassword`, `release.keyAlias`, `release.keyPassword`가 모두 있으면 그 키스토어로 서명하고, 없으면 심사용으로 debug 서명을 쓴다.

### 3. 권한

`pleos.car.permission.FUSED_LOCATION` 은 런타임 권한이라 앱이 처음 실행될 때 묻는다. 에뮬레이터에서 미리 주려면 다음을 실행한다(앱은 user 10 으로 돈다). `pm grant` 가 예외를 출력해도 `dumpsys package com.pplab.pleory` 에 `granted=true` 가 보이면 부여된 것이다.

```bash
for p in pleos.car.permission.FUSED_LOCATION android.permission.ACCESS_FINE_LOCATION \
         android.permission.ACCESS_COARSE_LOCATION android.permission.RECORD_AUDIO; do
  adb shell pm grant --user 10 com.pplab.pleory $p
done
```

나머지 차량 권한은 매니페스트 선언만으로 설치 때 부여된다.

### 4. 실행

```bash
adb shell am start -n com.pplab.pleory/ai.pleos.hello.MainActivity
adb logcat -s PleoryFlow:I PleoryPlatform:I
```

앱 화면은 에뮬레이터 오른쪽 패널(약 1682×1210)에 뜬다. 첫 실행 때 동의 화면이 나오고, 기어를 D 로 바꾸면(에뮬레이터 Virtual Console) 기록이 시작된다.

### 5. 데모 재현

데모 스크립트는 debug 빌드의 디버그 패널과 receiver 를 쓰므로 debug APK 를 설치한 상태에서 돌린다. 시연 영상(이틀 이야기, `docs/pleory/video-script.md`)은 하루에 명령 하나로 전 장면을 재현한다.

```bash
tools/run-demo.sh v3 reset          # 앱 데이터 초기화 + 권한 + 영상용 지난 기록 5건 (day1 이 알아서 한다)
REC=1 tools/run-demo.sh v3 day1     # 동의 → 퇴근길 18:40, 긴 혼잣말 한 번 → 카드(대표 문장 3줄)·요약 → 일·아이디어 상세 → 짬뽕 지우기 → 이번 주행 → QR → 지난 기록·할 일만 → 시동 OFF (약 2분 40초)
REC=1 tools/run-demo.sh v3 day2     # 같은 DB 로 다음 날 출근길 08:10 → Gleo 호출 → 회사 도착 → 리마인드 → 했어 (약 2분, 다시 돌려도 된다)
```

- 배속은 셋째 인자로 준다(기본 30). DemoClock 이 앱 시계를 그만큼 빨리 흘리고, 같은 배속으로 `tools/demo-route.gpx`(2일차는 `demo-route-day2.gpx`)를 에뮬레이터에 재생한다.
- 호스트가 바쁘면 `REC_SIZE=1920x1080 REC_FPS=20`(녹화 부하 줄이기), `LLM_BUDGET_MS=20000`(디버그 앱의 Claude 대기 시간 늘리기)을 준다.
- 결과물은 `apk/video/v3/day1|day2/` 의 `flow.log`(logcat), `rec_start.txt`, `raw.webm`(REC=1 일 때)이다. `SHOTS=<디렉터리>` 를 주면 주요 장면 PNG 도 남긴다.
- 탭은 디버그 receiver(`DemoReceiver`)가 버튼을 접근성 이름으로 찾아 누르고, 화면 좌표를 `PleoryDemo tap <이름> x= y=` 로 남긴다. 창 크기가 바뀌어도 같은 버튼을 누른다.
- 시뮬레이션한 부분: 운전자 대사는 텍스트로 넣고(듣는 중은 대사 길이만큼 유지), Gleo 호출은 테스트 ASR broadcast 로 넣고, Gleo 가 우리 앱으로 보내지 않으면 `ADD_DRIVE_NOTE` intent 를 직접 넣은 뒤 `gleo simulated` 를 남긴다.

이전 데모(v1, 한마디 2개)는 `tools/run-demo.sh [배속=30] [pcm|text]` 로 돌린다. `pcm` 은 준비한 PCM 을 on-device STT 에 넣고 `text` 는 STT 를 건너뛴다. PCM 준비 방법은 스크립트 상단 주석에 있다.

## 테스트 환경 및 필요한 설정

### 환경

- Pleos Connect 에뮬레이터 v2.1.0, AVD `Pleos_Connect_API_34`(디바이스 `Pleos_Connect_v2_0`, 2560×1440, RAM 4GB)
- 앱 영역 약 1682×1210, 밀도 160
- Android API 34(compileSdk·targetSdk 34), minSdk 28
- 에뮬레이터 차량 서비스: caas 4.2.10, navi 3.0.4, vehicle 2.3.22
- 빌드: Gradle 9.8.2 wrapper, AGP 9.4.1, JDK 17 이상

### 사용 SDK

| SDK (`ai.pleos.playground`) | 버전 | 용도 |
|---|---|---|
| Vehicle | 2.3.2 | 기어(P 감지), 주행 정보 |
| SpeechToText | 3.3.1 | 한마디 인식 (on-device, ko_KR) |
| TextToSpeech | 3.3.1 | "적어뒀어", 요약·리마인드 음성 |
| NaviHelper | 3.0.5 | 목적지, 도착 이벤트 |
| FusedLocation | 1.1.3 | 위치·속도 (궤적, 도착 반경 판정) |

저장소는 `https://nexus-playground.pleos.ai/repository/maven-releases/` 이다(로그인 불필요). 그 밖에 Anthropic Java SDK(Claude), zxing core(QR), Room + SQLCipher(로컬 DB)를 쓴다. Pleos LLM SDK 는 release 에 넣지 않는다(debug 스파이크 전용).

### 키 설정 (`local.properties`)

루트의 `local.properties`(gitignore 대상)에 아래 이름으로 값을 넣는다. 값은 커밋하지 않는다.

| 키 | 용도 | 없을 때 |
|---|---|---|
| `llm.proxyUrl` | 구조화 LLM 프록시 주소(`proxy/`, Claude `claude-haiku-5-5` → Gemini `gemini-2.5-flash`) | LLM 을 건너뛴다 |
| `llm.proxyToken` | 프록시 앱 토큰(Worker 의 `APP_TOKEN` 과 같은 값) | 같음 |
| `pleos.clientId` | Pleos Playground 앱 등록(debug 스파이크 화면 전용) | 본 기능에는 영향 없음 |
| `pleos.clientSecret` | 같음 | 같음 |

Claude·Gemini API 키는 앱에 넣지 않는다. 키는 Cloudflare Worker(`proxy/`)에만 두고 앱은 프록시를 부른다(배포 방법은 `proxy/README.md`). 프록시 설정이 없거나 두 LLM 이 모두 실패하면 한마디는 말한 원문 그대로(RAW) 저장되고, 카드에도 원문 한 줄로 나온다. 인식·응답·카드·요약·지난 기록·QR 은 프록시 없이도 동작한다.

### 권한

| 권한 | 용도 |
|---|---|
| `pleos.car.permission.CAR_DRIVE` | 기어 조회 |
| `pleos.car.permission.CAR_INFO` | 차량 정보 조회 |
| `pleos.car.permission.NAVI_ROUTE` | 목적지·도착 |
| `pleos.car.permission.STT_SERVICE` | 음성 인식 |
| `pleos.car.permission.TTS_SERVICE` | 음성 합성 |
| `pleos.car.permission.FUSED_LOCATION` | 위치 서비스 (런타임 요청) |
| `android.permission.RECORD_AUDIO` | 마이크 |
| `android.permission.ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | 위치 |
| `android.permission.INTERNET` | 구조화용 LLM 호출 |

Vehicle SDK 가 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING` 을 자동으로 merge 한다. 매니페스트에 `distractionOptimized` 를 선언해 주행 중에도 실행된다.

## APK 파일 위치 또는 GitHub Release 링크

- 저장소: `apk/PPLab_Pleory_release.apk` (release 빌드, PPLab 키로 서명)
- GitHub Release: https://github.com/wook95/pleory/releases/tag/v0.3
  - `pleory-demo-v3.mp4`: 시연 영상 (1분 54초)
  - `PPLab_Pleory_release.apk`: 위와 같은 release APK

설치는 `adb install -r apk/PPLab_Pleory_release.apk` 로 한다. 빌드 시점의 Claude 키(지출 한도 있음)가 APK 에 들어간다. Gemini 키는 넣지 않았다(Claude 가 실패하면 원문 그대로 남는다). `apk/` 의 다른 빌드 산출물은 git 에 넣지 않는다.

## 시연 영상

1분 54초, 두 테이크(1일차 퇴근길, 2일차 출근길)를 이어 붙였다. 1일차는 운전자가 긴 혼잣말을 한 번 하고 카드가 감정 / 일 / 아이디어로 정리해 보여 준다. 에뮬레이터 화면 녹화이고 운전자 대사는 ElevenLabs 로 만든 음성이다(앱에는 같은 단어를 텍스트로 입력). 파일은 위 Release 의 `pleory-demo-v3.mp4` 이고(렌더 출력은 `apk/video/pleory-film.mp4`), 편집 소스는 `video/`(Remotion)에 있다.

## 데이터 원칙

- 음성 원문은 저장하지 않는다. 텍스트만 차량 로컬(Room)에 둔다.
- 차 밖으로 나가는 데이터는 두 가지뿐이다. LLM 처리용 발화 텍스트, 날씨·주소 조회용 좌표. 식별자는 붙이지 않는다.
- LLM 에는 발화 텍스트만 보낸다. 위치, 주행값, 음성은 보내지 않는다.
- QR 은 카드 평문을 그대로 담는다. 서버나 URL 을 거치지 않는다.
- 캘린더·메신저·SNS 등 외부 서비스와 연동하지 않는다.

## 개인정보 처리

- 처음 실행하면(주차 상태에서) 동의 화면이 뜬다. "동의"를 눌러야만 동의다. "동의 안 함"을 고르거나 답하지 않으면 AI 정리는 꺼지고, 기록은 말한 원문 그대로 남는다.
- 보내는 것: 말한 내용의 텍스트만 외부 LLM으로 보내 정리한다. 먼저 Anthropic(Claude `claude-haiku-5-5`)로 보내고, 실패하면 Google(Gemini `gemini-2.5-flash`)로 보낸다. 음성, 위치, 이름은 보내지 않는다.
- 위치: 좌표는 날씨·주소 조회에 쓰고(지금은 fixture 라 밖으로 나가지 않는다), 경로와 거리는 차 안에서 계산한다.
- Gleo AI: Gleo 에게 말해 남긴 메모는 Pleos 플랫폼(Gleo AI)이 처리한 뒤 앱으로 전달한다.
- 앱이 모으는 것: 발화 텍스트, 발화 시각·위치·장소 이름·주행 거리·속도, 주행 경로, 정리된 항목(유형·감정·키워드), 동의 상태와 시각. 동의 화면에 같은 목록을 보여준다.
- 저장: 기록은 이 차(Room, SQLCipher 암호화)에만 저장하고, 사용자가 지울 때까지 보관한다. DB 비밀번호는 Android Keystore 키로 감싸 둔다. 동의 상태와 시각은 같은 기기의 SharedPreferences에 둔다.
- 철회와 삭제: 설정(카드 또는 주행 화면의 "설정", 주차 중에만 열림)에서 "동의 철회"를 누르면 그 순간부터 외부 전송이 멈춘다. "전체 삭제"는 확인을 한 번 더 거쳐 모든 기록을 지운다.
- 문의·신고: 설정 화면의 "문의·신고"에 연락처를 보여준다. TODO(제출 전, 사람 작업): 지금은 임시 주소 `pleory.team@example.com` 이다. 실제 연락처로 바꾼다(`ui/S3Views.kt` `CONTACT_EMAIL`).
- 개인정보처리방침: (URL 미정 — 담당자가 제공하면 여기에 넣는다)

## 데모 fixture

실서비스와 연동하지 않은 값이다.

- 날씨: 항상 "비".
- 장소 라벨: 좌표를 가장 가까운 기준점(반경 1.5km)에 맞춘다.

| 기준점 | 라벨 |
|---|---|
| (37.5133, 127.1000) | 회사 |
| (37.5210, 127.0300), (37.5190, 127.0030), (37.5170, 126.9760) | 올림픽대로 |
| (37.5165, 126.9590) | 한강대교 |
| (37.5400, 126.8400) | 집 |

- 경로: `tools/demo-route.gpx`(23km, 51분, 정체 구간 포함)와 그 역방향 `tools/demo-route-day2.gpx` 는 합성 경로라 실제 도로를 따르지 않는다.

## LLM

구조화는 외부 LLM API를 쓴다. Claude(`claude-haiku-5-5`, 공식 Java SDK)가 1순위이고 6.5초 안에 실패하면 Gemini, 그래도 실패하면 말한 원문 그대로 남긴다(전체 12초 안, 운전자는 기다리지 않는다). 카드의 묶음별 대표 문장도 같은 순서로 만들고, 실패하면 규칙(묶음 첫 항목 + "외 N개")으로 보인다. 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 전환한다.

## 알려진 한계

- on-device STT는 인식 오류가 날 수 있다.
- Odometer는 에뮬레이터에서 0이라 거리는 위치 궤적을 적분해 계산한다.
- 기록 수정과 나의 지도는 다음 단계다.
- 날씨와 장소 라벨은 위 fixture다.

## 개발자 메모

- APK에는 빌드 시점의 Claude·Gemini 키가 들어간다. 최종 제출 APK는 사용량 한도를 건 전용 키로 다시 빌드하고, 그 키는 심사 뒤 폐기한다.
- `local.properties`와 키스토어는 커밋하지 않는다.
