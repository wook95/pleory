# Pleory TRD — S1 end-to-end

- 기준: `Pleory_기능명세_v2.md`(이하 PRD). PRD와 어긋나면 PRD가 기준이고, PRD와 접수양식이 어긋나면 접수양식(기획서)이 기준이다.
- 이 문서는 PRD를 **어떻게 만들지**만 다룬다. 기능·문구·숫자는 PRD를 참조한다.
- 근거: 2026-11-08 SDK 스파이크(`app/src/debug/.../spike/SpikeActivity.kt`, 결과는 PRD §7과 CLAUDE.md).
- v2(11/8): Codex 레인과 critic 레인의 검토를 반영했다. 반영하지 않은 지적과 사용자 결정이 필요한 항목은 §7.

---

## 1. 결정 요약

| # | 결정 | 이유 |
|---|---|---|
| D1 | UI는 **Android View**, Activity 1개 + 화면별 View | 레포와 proto-make 시안이 View 기반이다. 확정안을 그대로 승격할 수 있다 |
| D2 | 차량·음성·LLM·시계는 **인터페이스 뒤**에 둔다. Fake는 **JVM 테스트용 가짜 콜백만** 만든다(앱 내부 GPX 재생기, 3종 LLM 같은 확장은 하지 않음) | 상태 머신을 테스트로 고정하되 2일 범위를 넘지 않는다 |
| D3 | 상태 머신과 모든 SDK 이벤트는 **단일 직렬 실행 경로**(메인 루퍼 또는 단일 스레드 디스패처)로 모은다. SDK 콜백은 Binder 스레드에서 오므로 즉시 그 경로로 넘긴다 | LLM 콜백이 Binder 스레드에서 리스너를 직접 부른다(javap 확인). 경합을 한 곳에서 처리하기 위해서 |
| D4 | 저장소는 **Room + KSP2**. AGP 9 내장 Kotlin은 KSP를 공식 지원한다. T1에서 실제 Entity·DAO 생성과 쓰기·재조회까지 확인하고, 실패할 때만 `SQLiteOpenHelper`로 간다 | PRD §3 |
| D5 | **STT·TTS는 ON_DEVICE 고정.** SERVER/HYBRID STT는 음성이 서버로 나가서 PRD 원칙 4(차 밖으로 나가는 건 발화 텍스트와 좌표뿐)와 충돌한다 | 정확도가 부족하면 원칙을 바꿀지는 사용자가 결정한다(§7-2) |
| D6 | **(11/8 확정) 공모전 공급자는 Gemini `gemini-2.5-flash`. 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 `PleosLlmStructurer`로 전환한다** — 이 문장을 `Structurer` 인터페이스와 Gemini 구현의 KDoc, README에 그대로 남긴다. 상세:(REST, 텍스트만 전송, `responseSchema`로 JSON 강제, thinking 0, 앱 안 실측 약 1~1.3초). 목표 공급자는 Pleos LLM이다. 권한이 열리고 contextMap 처리(§7-1)가 확인되면 교체한다. `flash-lite`는 빈말에 없는 문장을 지어내서(규칙 4 위반) 쓰지 않는다. 503 등 일시 오류는 8초 안에서 1회 재시도. 구조화 LLM은 `Structurer` 인터페이스 + **실제 공급자 하나**. 후보는 Pleos LLM SDK SERVER(자격증명 필요) 또는 외부 LLM HTTP API(§7-1). `RuleStructurer`는 개발·테스트용이고, 이걸로 찍은 영상은 "기능 축소 데모"로 명시한다 | 접수양식의 핵심 주장이 "AI가 정리"다 |
| D7 | **DemoClock**: `Clock` 인터페이스. 데모 모드에서는 GPX 타임스탬프를 기준 시각으로 삼고 N배속으로 흐른다. `startedAt`, `Utterance.at`, `durationMin`, `congestionMin`, 구간 판단(§6-2 시각 규칙)은 전부 이 시계로 계산한다 | 1~2분 영상에서 카드가 "51분 · 23km · 정체 23분 · 18:57"을 보여야 한다 |
| D8 | 거리·속도는 **좌표와 시각으로 직접 계산**한다(`Location.speed`가 GPX 재생에서 채워지는지 모른다). 역순·중복·오래된 좌표·GPS 점프(속도 > 200km/h)는 버린다. Odometer는 0이 아닐 때만 쓴다 | 에뮬레이터 Odometer 0.0 |
| D9 | 목적지 상태는 **미확인 / 없음 / 있음(좌표)** 셋으로 구분한다. 도착 = 기어 P **AND** (`onDestinationArrived` OR 최신 유효 위치가 목적지 반경 300m OR 목적지 "없음"이 확정된 상태에서 P 5초). 목적지 변경이나 기어 D 복귀 때 도착 상태를 초기화한다 | 미확인을 "없음"으로 보면 P 5초 조건 때문에 잘못 도착 판정된다 |
| D10 | **SdkHub**(Application 소유)가 SDK별 상태(준비 중/준비됨/실패/해제)를 들고 init·release를 맡는다. Activity는 구독만 한다. TTS는 `initialize` 성공 뒤 리스너 등록, 로케일 `Locale.KOREA` | 스파이크의 Activity별 release를 옮기면 싱글턴 수명과 충돌한다 |
| D11 | **시동 = 앱 프로세스의 세션 시작과 종료**로 정의한다. Pleos 시동 API를 찾으면 `IgnitionSource`로 교체한다 | 상태 머신의 시동 ON/OFF 전이에 실제 입력이 필요하다 |
| D12 | 오프라인 처리: **네트워크 없음 = PENDING**(다음 시동 때 재시도), **응답 불량·타임아웃 = FAILED + RAW**. PRD §4-1을 이 기준으로 맞춘다 | PRD §1과 §4-1이 서로 달랐다 |
| D13 | 비밀값은 `local.properties` → `BuildConfig`. 제출 APK에 들어가는 키는 사용량 한도를 건 전용 키로 쓰고 제출 뒤 폐기한다 | APK는 디컴파일할 수 있다 |

---

## 2. 구조

```
ai.pleos.hello
├── PleoryApp                  Application. SdkHub, SessionController, DB 조립(수동 DI)
├── MainActivity               상태 → 화면 매핑만. 재생성되면 다시 구독
├── core/                      순수 Kotlin, JVM 테스트 대상
│   ├── SessionStateMachine    PRD §1. 이벤트 → 상태 + 부수효과 명령
│   ├── CardTrigger            D9
│   ├── SegmentRule            PRD §6 (Clock 사용)
│   ├── TrackMath              D8 거리·속도·정체(< 20km/h 누적, Clock 기준)
│   ├── SummaryComposer        PRD §4-2, 40자 상한
│   ├── StructureContract      PRD §4-1 응답 검증 → Item 또는 RAW
│   └── Clock                  System / Demo(D7)
├── data/                      Room 엔티티(PRD §3) + Session.state(OPEN/CARD/CLOSED) 추가
├── platform/                  GearSource, NaviSource, LocationSource, SpeechInput, SpeechOutput,
│                              Structurer, UxRestrictions, SdkHub
└── ui/                        S0DriveView, S1CardView(+RouteView). S2·S3은 P1
```

debug 소스셋에만 디버그 패널을 둔다(기어 P/D 가짜 입력, 텍스트 발화, 도착 강제, 오프라인 토글, DemoClock 배속). **자동 골은 이 패널로 검증하고, Virtual Console 실측은 사람 체크포인트(§5)에서 한다.**

### 2-1. 한마디 1건의 흐름과 경합 규칙

```
S0 탭 → SpeechOutput.stop() → SpeechInput.start(회차 r)
  ├ 재탭·기어 P·시동 OFF·15초 만료 → 회차 r 취소. 이후 도착하는 r의 결과는 버린다
  └ r의 유효한 isFinal 텍스트가 처음 도착 (두 번째부터 무시)
      ├ 빈 문자열·오류 → 저장 없음. 짧은 실패 이어콘 1회(§7-5 확정 전까지는 무음)
      └ Utterance 저장(PENDING, 맥락 스냅샷) → TTS "적어뒀어"
          → Structurer.structure(text) [요청 세대 g, 8초]
              ├ isDone 성공이 8초 안에 옴 → 트랜잭션: Item n개 저장 + DONE
              ├ 8초 초과 → 트랜잭션: RAW 1개 + FAILED. 세대 g는 만료되고, 늦게 온 응답은 버린다
              └ 네트워크 없음 → PENDING 유지 (D12)
```

- SDK에 요청별 취소 API가 없으므로 **세대 번호로만 막는다**(D3). `isDone=false` 조각 응답은 모았다가 `isDone`일 때 한 번에 처리한다.
- 재처리(다음 시동)에서는 해당 Utterance의 기존 Item을 지우고 새 Item을 넣는 작업을 **한 트랜잭션**으로 한다. 같은 Utterance를 두 번 재처리해도 Item 수가 늘지 않는다.
- LLM에 보내는 건 발화 텍스트뿐이다. `structure(text: String)` 시그니처로 고정한다.

### 2-2. 도착 직후 처리

기어 P + 도착 → 카드를 먼저 띄운다. 진행 중인 구조화가 있으면 그 요청이 끝나거나 8초 폴백이 확정될 때까지 기다린 뒤 요약을 만들어 재생한다(최대 8초. 그동안 카드에는 원문이 보인다). 대기 중이나 재생 중에 기어 D로 바뀌면 요약을 취소하거나 즉시 멈추고 S0으로 돌아간다.

### 2-3. 음성 중재

앱 안에서 STT와 TTS가 동시에 돌지 않게 한다. STT 시작 전에 TTS를 멈추고, 인식 중에는 TTS를 내지 않는다. 내비 음성과 겹치는 문제는 P2다(PRD §1).

### 2-4. 상태 머신 테스트 목록 (T3 완료 기준)

PRD §1의 "빠뜨리기 쉬운 전이"를 모두 테스트로 만든다.
- LISTENING 중 시동 OFF·기어 P·재탭 → 취소하고 저장하지 않음.
- 취소 직후에 final이 들어와도 저장·TTS가 없음.
- 빈 STT → 저장하지 않음.
- final이 두 번 오면 1건만 저장.
- CARD 중 기어 D → S0 + 같은 세션.
- CLOSED 후 재출발 → 새 세션.
- 중간 정차(P, 도착 아님) → SESSION_OPEN 유지.
- 목적지 "미확인" 상태의 P 5초 → 카드 없음.
- LLM 8초 경계 처리, 늦은 성공 폐기, 재처리 중복 없음.

미열람 카드 재표시(F13)는 PRD §9에서 제외됐으므로 테스트하지 않는다.

---

## 3. 화면

- 앱 영역 **1682×1210dp 가로**, NoActionBar. 배치와 타이포는 PRD §5(Pleos 디자인 가이드 수치 반영).
- 시안은 proto-make `prototypes/2026-11-08-pleory-card/`에서 정한다. **T8은 시안 확정 없이도 기능 기준(숫자·항목·흐름 바가 맞음)으로 통과**시키고, 시각 다듬기는 T8b에서 한다.
- 디자인 기준 우선순위:
  1. Pleos 디자인 가이드(https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout 외 하위 페이지)와 운전자 주의분산 가이드(https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline)
  2. PRD §3·§5
  3. ui-ux-pro-max로 다듬기

  Apple HIG는 원칙만 참고한다.
- 주행 중 화면 변화는 S0 버튼의 두 상태뿐이다. S0에는 데이터 바인딩을 두지 않는다.
- 감정은 색과 함께 ↓·→·↑ 기호로도 표시한다(색각 접근성, PRD §3에 추가할 것).

---

## 4. 빌드·매니페스트

`app/build.gradle.kts`:
- `useLibrary("android.car")` 추가. `CarUxRestrictionsManager`를 쓰려면 필요하다.
- `implementation` 목록: Vehicle 2.3.2, SpeechToText 3.3.1, TextToSpeech 3.3.1, NaviHelper 3.0.5, **FusedLocation 1.1.3**, (선택) LLM 2.1.3.1, Room + KSP2(버전 고정).

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />           <!-- 런타임, 정차 중 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />   <!-- 런타임, coarse와 함께 요청 -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="pleos.car.permission.FUSED_LOCATION" />          <!-- dangerous: 런타임 요청(위치 권한과 함께) -->
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />
<uses-permission android:name="pleos.car.permission.CAR_INFO" />
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE" />
<uses-permission android:name="pleos.car.permission.STT_SERVICE" />
<uses-permission android:name="pleos.car.permission.TTS_SERVICE" />
<uses-permission android:name="pleos.car.permission.LLM_SERVICE" />          <!-- Pleos LLM을 쓸 때만 -->
```

FusedLocation 퍼미션명은 문서에서 확인해 추가한다. MainActivity에는 `distractionOptimized=true`(선언돼 있음)와 NoActionBar를 둔다. 문 위치 예제와 `CAR_DOORS`는 지운다.

---

## 5. 태스크

`종류`: **자동** = 에이전트가 명령과 테스트만으로 완료를 판정한다. **사람** = 사용자의 조작이나 판단이 필요하다(골 실행을 멈추고 알린다). **외부** = 다른 사람의 산출물이 필요하다.

### P0 — 데모 경로 (2일)

| ID | 종류 | 태스크 | 완료 기준 | 선행 |
|---|---|---|---|---|
| T1 | 자동 | 골격: 패키지, PleoryApp, SdkHub, §4 빌드·매니페스트, Room+KSP2 실검증(D4) | `assembleDebug` 성공. 계측 또는 Robolectric 없이 가능한 범위에서 Entity·DAO 생성 확인 + 앱 실행 시 DB 쓰기·재조회 로그. `CarUxRestrictionsManager` 획득 로그 | — |
| **H1** | **사람** | **플랫폼 차단 조건 확인**: Virtual Console 기어 D에서 앱이 떠 있고 버튼이 눌린다. 호스트 마이크로 한국어 한 문장이 on-device STT로 인식된다 | 사용자가 확인(스크린샷 + 인식 텍스트 로그). 실패하면 바로 태연에게 알린다(PRD §9) | T1 |
| T2 | 자동 | `core/` 순수 로직: Clock, SegmentRule, TrackMath(D8), SummaryComposer, StructureContract | `testDebugUnitTest` 통과. PRD §4-2 모든 분기, §4-1 실패 조건, D8 이상치 제거 포함 | T1 |
| T3 | 자동 | SessionStateMachine + CardTrigger + 직렬 실행 경로(D3) | §2-4 테스트 전부 통과 | T2 |
| T4 | 자동 | data/ + 한마디 파이프라인(§2-1), 세대·회차 가드, 재처리 트랜잭션 | 테스트: 같은 문장 → Item 2개. 8초 초과 → RAW+FAILED. 늦은 응답 무시. 오프라인 → PENDING. 재처리 2회 → Item 수 그대로 | T3 |
| T5 | 자동 | platform/ Pleos 구현(기어·내비·위치·STT·TTS·UxRestrictions) + 디버그 패널 | logcat: 기어 콜백, 위치 스트림(정차 중 권한 승인 후), TTS onDone, STT final(PCM 주입) | T1 |
| **E1** | **외부** | GPX 경로(회사→집, 올림픽대로·한강대교, 정체 구간), 좌표→라벨 fixture 표 | 태연이 전달(PRD §10-4). 없으면 T6이 임시 경로를 만들고 표시한다 | — |
| T6 | 자동 | S0 화면 + 실제 STT 버튼 + 음성 중재(§2-3) + DemoClock 배속 + 위치 재생 스크립트(`adb emu geo fix`를 같은 배속으로) | 디버그 패널 기어 D에서 탭 → 듣는 중 → 저장 → "적어뒀어"(PCM 주입). 스크린샷 2장 | T4, T5 |
| T7 | 자동 | `GeminiStructurer` 연결(D6, `spike/GeminiSpikeActivity.kt` 요청 형식 재사용). `Structurer`와 구현 KDoc에 "공모전은 Gemini. 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 PleosLlmStructurer로 전환" 주석 | 실제 호출로 "내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다" → Item 2개. 실패·타임아웃 경로 1회씩 확인 | T4, §7-1 |
| T8 | 자동 | 도착 판정(D9) + 세션 종료값 + S1 카드(기능 기준) + 요약 대기·재생·건너뛰기(§2-2, PRD §4-2) | 디버그 패널로 PRD §8 1~6단계 재현. 카드 숫자 **51분 · 23km · 2 · 3, 흐름 ↓→↑**(DemoClock + GPX), 요약 문장 일치 | T6, T7 |
| **H2** | **사람** | 실기 리허설: Virtual Console 조작 + 실제 음성으로 §8 1~6단계 | 사용자 확인. 실패 지점을 기록한다 | T8 |
| **H3** | **사람** | **녹화**: 호스트 화면 녹화(시스템 오디오 + 마이크 포함. `adb screenrecord`는 소리가 안 담긴다). 앱 영역과 Virtual Console을 함께 잡는다. 영상 콘티는 §7-6 | 1~2분 영상 파일 | H2 |
| T9 | 자동 | 제출 패키지: README(실행·테스트 환경·fixture·DemoClock·자격증명 안내), release APK `apk/` | README·APK·추적 파일에 실명·연락처가 없다(`git grep`, APK 내용 grep). 커밋 작성자 정보는 사용자 결정(11/8: 그대로 진행)에 따라 H4에서 처리한다 | T8 |
| **H4** | **사람** | GitHub private 저장소 생성(익명 계정·이력 정리, §7-4), 심사 계정 Collaborator 초대 | 링크 3개 | T9 |

### P1 — 마감(11/15)까지, 접수양식에 적힌 순서

| ID | 종류 | 태스크 | 완료 기준 |
|---|---|---|---|
| T8b | 사람→자동 | 시안 별표 확정 → 카드 시각 승격 | 확정안과 스크린샷 일치 |
| T10 | 자동 | F9 항목 수정·삭제 + 전체 삭제(2단계 확인) | 삭제 후 카드·DB 반영 스크린샷 |
| T11 | 자동 | F10 QR(평문, 1KB 초과 시 항목만) | 폰으로 스캔해 텍스트 확인(사람) |
| T12 | 자동 | F11 나의 지도(시드 fixture로 과거 세션 채움) | S2 스크린샷 |
| T13 | 자동 | 첫 실행 흐름(정차 중 권한 → 버튼 위치), 빈·로딩 상태 문구 | 각 상태 스크린샷 |
| T14 | 사람 | 영상 재촬영 | 최종 영상 |

병렬 가능: T2·T5, E1은 처음부터, T7은 §7-1이 정해지는 즉시.

---

## 6. 리스크

1. **on-device STT 정확도**: 합성 음성으로는 "거래처 서류"가 "까지 스타 스타일"로 인식됐다. H1에서 실제 음성으로 판정한다. 부족하면 §7-2.
2. **도착 이벤트**: 에뮬레이터 경로 재생에서 `onDestinationArrived`가 오는지 미확인. D9 반경 판정으로 대체할 수 있지만, 목적지 좌표를 얻어야 한다(경로 설정 필요).
3. **GPX 재생과 Location.speed**: D8로 직접 계산해서 회피한다.
4. **분할 화면**: 세로 레이아웃은 P1 이후.
5. **KSP**: D4. 실검증 전까지는 미확인.

---

## 7. 결정이 필요한 것 (사용자·태연)

1. **LLM 공급자**: 11/8 실측 결과 Pleos LLM SDK SERVER는 ① 자격증명 등록은 되지만 `generateContent`가 HTTP 400을 낸다 — **프로젝트 토큰에 LLM 엔드포인트 권한이 없음(확인됨, 게이트웨이 코드 e:cloud:gateway:00401)**, Playground에 권한 요청 필요 ② 서비스가 요청에 좌표 필드(contextMap)를 붙인다. 지금은 하드코딩 템플릿 값으로 보이지만(추정) 앱에서 끌 수 없어 원칙 4를 보장하지 못한다. 그래서 **외부 LLM API를 기본으로 권장**한다(키는 D13). Pleos LLM을 쓰려면 원칙 4 문구를 바꿔야 한다. → **확정(11/8): 공모전은 Gemini. 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 전환.** 근거: 공식 신청 문서상 클라이언트 ID 용도는 TTS·STT뿐이고, 설명회 SDK 목록에도 LLM이 없다. 400 원인은 권한 부족으로 확인됨(위 ①). 남은 일: 출력 말투가 "~합니다"체로 나온다(PRD 예시는 "보내기", "예뻤다"). 프롬프트에 말투 규칙 한 줄을 추가할지 T7에서 정한다.
2. **STT 원칙**: on-device 정확도가 부족할 때 SERVER STT를 허용할지. 허용하면 PRD 원칙 4를 "음성은 인식을 위해 플랫폼 음성 서버로 전송, 저장하지 않음"으로 고친다.
3. **1차 범위**: 접수양식은 '나의 지도'·QR·수정 및 삭제를 1차 범위로 적었다. 위 P1 순서(T10~T12)로 마감 전에 넣는 안을 기본으로 둔다.
4. **실명 노출**(11/8 결정: 커밋 이메일은 그대로 진행, 제출 시 H4에서 다시 판단): 기존 커밋의 작성자 이메일이 접수양식에 적은 개발자 연락처와 같다. 제출용 저장소는 이력 없이 새로 만들거나 작성자를 다시 쓰고, 작업 전에 `git config user.email`을 익명 주소로 바꾼다.
5. **STT 실패 신호**: 주행 중 인식 실패 시 무음(현 PRD)으로 둘지, 짧은 이어콘이나 "다시 말해줘"를 낼지. 무음이면 운전자가 화면을 확인하게 된다.
6. **영상 콘티**: 주행 중 화면이 바뀌지 않는 장면을 어떻게 보여 줄지(Virtual Console 동시 노출, 시계·자막 오버레이). 기어 P와 카드가 뜨는 장면을 한 컷에 담는다.
7. **profileId 출처**: Pleos 프로필 API 확인 전까지 고정 ID를 쓰고 README에 적는다.
8. **applicationId** `ai.pleos.hello`는 플랫폼 소유자 네임스페이스처럼 보인다. 제출 전에 바꿀지.
