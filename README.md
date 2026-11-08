# Pleory (플레오리)

운전 중 떠오르는 대로 말하면 AI가 정리하고, 차가 시간·장소·주행 맥락을 붙여 통근마다 한 편씩 쌓아주는 Pleos 드라이브 저널.

## 핵심 흐름

1. 시동 ON. 화면의 말하기 버튼은 "기록 중" 상태로 대기한다.
2. 운전 중 버튼을 한 번 누르고 한마디 한다. 앱은 "적어뒀어" 한 문장(TTS)만 답하고 화면은 바뀌지 않는다. 정리는 백그라운드에서 한다.
3. 목적지에 도착해 기어를 P로 넣으면 '오늘의 나' 카드(소요 시간·거리·한마디 수·항목, 감정 흐름)가 뜬다.
4. 15초 요약을 음성과 자막으로 재생한다(건너뛰기 가능).

주행 중 앱은 먼저 말을 걸지 않는다. 카드와 요약은 도착과 기어 P 이후에만 나온다.

## 데이터 원칙

- 음성 원문은 저장하지 않는다. 텍스트만 차량 로컬(Room)에 둔다.
- 차 밖으로 나가는 데이터는 두 가지뿐이다. LLM 처리용 발화 텍스트, 날씨·주소 조회용 좌표. 식별자는 붙이지 않는다.
- LLM에는 발화 텍스트만 보낸다. 위치, 주행값, 음성은 보내지 않는다.
- 캘린더·메신저·SNS 등 외부 서비스와 연동하지 않는다.

## 개인정보 처리

- 처음 실행하면(주차 상태에서) 동의 화면이 뜬다. "동의"를 눌러야만 동의다. "동의 안 함"을 고르거나 답하지 않으면 AI 정리는 꺼지고, 기록은 말한 원문 그대로 남는다.
- 보내는 것: 말한 내용의 텍스트만 외부 LLM으로 보내 정리한다. 먼저 Anthropic(Claude `claude-haiku-5-5`)로 보내고, 실패하면 Google(Gemini `gemini-2.5-flash`)로 보낸다. 음성, 위치, 이름은 보내지 않는다.
- 위치: 좌표는 날씨·주소 조회에 쓰고(지금은 fixture 라 밖으로 나가지 않는다), 경로와 거리는 차 안에서 계산한다.
- Gleo AI: Gleo 에게 말해 남긴 메모는 Pleos 플랫폼(Gleo AI)이 처리한 뒤 앱으로 전달한다.
- 앱이 모으는 것: 발화 텍스트, 발화 시각·위치·장소 이름·주행 거리·속도, 주행 경로, 정리된 항목(유형·감정·키워드), 동의 상태와 시각. 동의 화면에 같은 목록을 보여준다.
- 저장: 기록은 이 차(Room, SQLCipher 암호화)에만 저장하고, 사용자가 지울 때까지 보관한다. DB 비밀번호는 Android Keystore 키로 감싸 둔다. 동의 상태와 시각은 같은 기기의 SharedPreferences에 둔다.
- 철회와 삭제: 설정(카드 또는 주행 화면의 "설정", 주차 중에만 열림)에서 "동의 철회"를 누르면 그 순간부터 외부 전송이 멈춘다. "전체 삭제"는 확인을 한 번 더 거쳐 모든 기록을 지운다.
- 문의·신고: 설정 화면의 "문의·신고"에 연락처를 보여준다. **TODO(제출 전, 사람 작업): 지금은 임시 주소 `pleory.team@example.com` 이다. 실제 연락처로 바꾼다(`ui/S3Views.kt` `CONTACT_EMAIL`).**
- 개인정보처리방침: (URL 미정 — 담당자가 제공하면 여기에 넣는다)

## 사용 SDK와 권한

| SDK (`ai.pleos.playground`) | 버전 | 용도 |
|---|---|---|
| Vehicle | 2.3.2 | 기어(P 감지), 주행 정보 |
| SpeechToText | 3.3.1 | 한마디 인식 (on-device, ko_KR) |
| TextToSpeech | 3.3.1 | "적어뒀어", 요약 음성 |
| NaviHelper | 3.0.5 | 목적지, 도착 이벤트 |
| FusedLocation | 1.1.3 | 위치·속도 (궤적, 도착 반경 판정) |

LLM SDK는 release에 포함하지 않는다(debug 스파이크 전용).

| 권한 | 용도 |
|---|---|
| `pleos.car.permission.CAR_DRIVE` | 기어 조회 |
| `pleos.car.permission.CAR_INFO` | 차량 정보 조회 |
| `pleos.car.permission.NAVI_ROUTE` | 목적지·도착 |
| `pleos.car.permission.STT_SERVICE` | 음성 인식 |
| `pleos.car.permission.TTS_SERVICE` | 음성 합성 |
| `pleos.car.permission.FUSED_LOCATION` | 위치 서비스 |
| `android.permission.RECORD_AUDIO` | 마이크 |
| `android.permission.ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | 위치 |
| `android.permission.INTERNET` | 구조화용 LLM 호출 |

Vehicle SDK가 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`을 자동으로 merge한다. 매니페스트에 `distractionOptimized`를 선언해 주행 중에도 실행된다.

## 테스트 환경

- Pleos Connect 에뮬레이터 v2.1.0 (AVD `Pleos_Connect_API_34`, 2560×1440)
- 앱 영역 약 1682×1210, 밀도 160
- Android API 34, minSdk 28

## 빌드와 실행

JDK 17 이상(Android Studio 번들 JBR 가능)이 필요하다. 루트의 `local.properties`(gitignore 대상)에 값을 넣는다.

```properties
pleos.clientId=<Pleos Playground Client ID>
pleos.clientSecret=<Pleos Playground Client Secret>
llm.claudeKey=<Anthropic API key>
llm.geminiKey=<Gemini API key>
```

`llm.claudeKey`가 없으면 환경변수 `PLEORY_CLAUDE_KEY`, `llm.geminiKey`가 없으면 `GEMINI_API_KEY`를 쓴다. `ANTHROPIC_API_KEY`는 읽지 않는다. Claude 키가 없으면 Gemini만 쓴다.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.pplab.pleory/ai.pleos.hello.MainActivity
adb logcat -s PleoryFlow:I PleoryPlatform:I
```

release APK는 git에 넣지 않는다(빌드 시 키가 들어가기 때문). 저장소의 Releases에 첨부하며, 받은 파일을 `apk/`에 두고 아래처럼 설치한다.

```bash
adb install -r apk/pleory-1.0.apk
```

release 서명은 심사용으로 debug 서명을 쓴다. 자체 키스토어로 서명하려면 `local.properties`에 `release.storeFile`, `release.storePassword`, `release.keyAlias`, `release.keyPassword`를 추가한 뒤 `./gradlew assembleRelease`를 실행한다.

## 데모 재현

데모 스크립트는 debug 빌드의 디버그 패널을 사용하므로 debug APK를 설치한 상태에서 실행한다.

```bash
tools/run-demo.sh [배속=30] [pcm|text]
```

- 앱 안의 시나리오를 무장하고, 같은 배속으로 `tools/demo-route.gpx`를 에뮬레이터에 재생한다.
- 시나리오가 첫 위치 fix를 받는 순간을 18:40으로 삼아 DemoClock이 켜진다. DemoClock은 앱 시계를 배속만큼 빠르게 흘려, 약 51분 퇴근 경로를 짧은 시간에 재생한다. 경로 재생과 앱 시계가 같은 배속이라 어긋나지 않는다.
- 18:57과 19:08에 한마디가 입력되고, 19:31에 기어 P, 마지막 위치의 300m 반경 판정으로 카드가 뜬다.
- `pcm`은 준비한 PCM 파일을 on-device STT에 넣고, `text`는 STT를 우회해 텍스트를 인식 결과로 넣는다. PCM 준비 방법은 스크립트 상단 주석을 본다.

영상 v2(이틀 이야기, `docs/pleory/video-script.md`)는 하루에 명령 하나로 전 장면을 재현한다. 텍스트 모드만 쓴다.

```bash
REC=1 tools/run-demo.sh v2 day1   # 앱 데이터 초기화 → 동의 → 퇴근길 18:40, 한마디 3개 → 카드·요약 → 지우기 → QR → 지난 기록·할 일만 → 시동 OFF (약 2분 20초)
REC=1 tools/run-demo.sh v2 day2   # 같은 DB 로 다음 날 출근길 08:10 → Gleo 호출 → 회사 도착 → 리마인드 → 했어 (약 2분, 다시 돌려도 된다)
```

- 산출물은 `apk/video/v2/day1|day2/` 에 `flow.log`(logcat), `rec_start.txt`, `raw.webm`(REC=1). `video/scripts/prepare.sh apk/video/v2/day1` 와 `node video/scripts/timings.mjs apk/video/v2` 가 그대로 읽는다. `SHOTS=<디렉터리>` 면 주요 장면 PNG 도 남긴다.
- 탭은 좌표 상수가 아니라 디버그 receiver(`DemoReceiver`)가 버튼을 접근성 이름으로 찾아 누르고, 화면 좌표를 `PleoryDemo tap <이름> x= y=` 로 남긴다.
- 시뮬레이션: 운전자 대사는 텍스트로 넣고(듣는 중은 대사 길이만큼 유지), 한마디 3은 정리를 건너뛰고 원문으로 저장하며, Gleo 호출은 테스트 ASR broadcast 로 넣는다(Gleo 가 우리 앱으로 안 오면 `ADD_DRIVE_NOTE` intent 를 직접 넣고 `gleo simulated` 를 남긴다).

데모 fixture (실서비스 연동 아님):

- 날씨: 항상 "비".
- 장소 라벨: 좌표를 가장 가까운 기준점(반경 1.5km)에 맞춘다.

| 기준점 | 라벨 |
|---|---|
| (37.5133, 127.1000) | 회사 |
| (37.5210, 127.0300), (37.5190, 127.0030), (37.5170, 126.9760) | 올림픽대로 |
| (37.5165, 126.9590) | 한강대교 |
| (37.5400, 126.8400) | 집 |

- 임시 경로: `tools/demo-route.gpx`는 합성 경로다(23km, 51분, 정체 구간 포함). 실제 도로를 따르지 않는다.

## LLM

구조화는 외부 LLM API를 쓴다. Claude(`claude-haiku-5-5`, 공식 Java SDK)가 1순위이고 4.5초 안에 실패하면 Gemini, 그래도 실패하면 말한 원문 그대로 남긴다(전체 8초 안). 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 전환한다.

## 알려진 한계

- on-device STT는 인식 오류가 날 수 있다.
- Odometer는 에뮬레이터에서 0이라 거리는 위치 궤적을 적분해 계산한다.
- 기록 수정, QR로 폰에서 열기, 나의 지도는 다음 단계다.
- 날씨와 장소 라벨은 위 fixture다.

## 개발자 메모

- APK에는 빌드 시점의 Claude·Gemini 키가 들어간다. 최종 제출 APK는 사용량 한도를 건 전용 키로 다시 빌드하고, 그 키는 심사 뒤 폐기한다.
- `local.properties`와 키스토어는 커밋하지 않는다.
