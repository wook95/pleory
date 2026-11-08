# Pleory TRD — S1 end-to-end

- 기준: `Pleory_기능명세_v2.md`(이하 PRD). v4 반영분은 §8-6(LLM 계약 §4·요약 규칙은 v2와 같다). PRD와 어긋나면 PRD가 기준이고, PRD와 접수양식이 어긋나면 접수양식(기획서)이 기준이다.
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
| D6 | **(11/8 변경) 공모전 공급자는 Claude `claude-haiku-5-5` 1순위, 실패하면 Gemini `gemini-2.5-flash`(§8-8). 플랫폼 LLM(Gleo AI)이 서드파티에 열리면 `PleosLlmStructurer`로 전환한다** — (이전: Gemini 단독) 이 문장을 `Structurer` 인터페이스와 Gemini 구현의 KDoc, README에 그대로 남긴다. 상세:(REST, 텍스트만 전송, `responseSchema`로 JSON 강제, thinking 0, 앱 안 실측 약 1~1.3초). 목표 공급자는 Pleos LLM이다. 권한이 열리고 contextMap 처리(§7-1)가 확인되면 교체한다. `flash-lite`는 빈말에 없는 문장을 지어내서(규칙 4 위반) 쓰지 않는다. 503 등 일시 오류는 8초 안에서 1회 재시도. 구조화 LLM은 `Structurer` 인터페이스 + **실제 공급자 하나**. 후보는 Pleos LLM SDK SERVER(자격증명 필요) 또는 외부 LLM HTTP API(§7-1). `RuleStructurer`는 개발·테스트용이고, 이걸로 찍은 영상은 "기능 축소 데모"로 명시한다 | 접수양식의 핵심 주장이 "AI가 정리"다 |
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
8. **applicationId**: `ai.pleos.hello`는 플랫폼 소유자 네임스페이스처럼 보인다. → 2026-11-08 결정: `com.pplab.pleory`로 바꿨다(§8-7). Kotlin 패키지·namespace는 `ai.pleos.hello` 그대로.

---

## 8. 후속 과제 (P0 완료 후, 2026-11-08)

### 8-1. Pleos 플래닝·심사 가이드 감사에서 나온 것 — 반드시 바꿀 것

1. 앱 안에 개인정보 고지와 명시적 동의 화면(Gemini 전송, 좌표, 로컬 저장, 보유기간, 거부 시 원문만 저장)을 둔다. 동의 철회와 전체 삭제(F12)를 P0으로 올린다. 개인정보처리방침 URL을 준비한다.
2. `screenOrientation`과 가로·세로(분할) 레이아웃. 카드는 고정 폭 대신 비율로 배치한다.
3. TTS 오디오 포커스: `AudioAttributes`(USAGE_ASSISTANT) + `GAIN_TRANSIENT_MAY_DUCK`. 거부되면 "적어뒀어"를 생략하고, 포커스를 잃으면 요약을 멈춘다.
4. 기어 `check*Capabilities` 확인. 내비가 없는 차는 FusedLocation 근접 알림 폴백.
5. CarUxRestrictions를 카드·설정·QR 진입 조건에 연결한다.
6. 앱 카테고리: Lifestyle + DO + 주행 중 음성 입력이 허용되는지 주최 측에 확인한다.

바꾸면 좋은 것: CPU·RAM 실측 기록, 카드·세션 상태 복원(F13 앞당기기), 카드 오프라인 표시, 쓰지 않는 `CAR_INFO` 제거, S0 "퇴근길" 하드코딩 수정, 목적지 로그 마스킹.
원문 근거: 감사 에이전트가 수집한 가이드 텍스트(세션 scratchpad `guide/txt/`).

### 8-2. 코드 리뷰 LOW (데모 범위에서 수용)

- 취소 직후 다시 탭하면 이전 회차의 늦은 Ended/Error가 새 회차로 붙을 수 있다(`platform/Speech.kt`).
- `SdkHub.initLocation`이 FAILED 뒤 `release()` 없이 `initialize()`를 다시 부른다.
- final 없이 Ended만 오는 경로(무음)는 15초 타이머로 풀린다. 테스트 1건을 추가할 것.

### 8-3. Gleo AI 연동 (2026-11-08 조사)

- **가능: Gleo Interface(App Schema + Intent).** `assets/schema.json`(invokeWord "플레오리", actionCalls 최대 3개, 예: `ADD_DRIVE_NOTE{note}`)과 action intent-filter를 두면 Gleo가 우리 기능을 호출한다. 파라미터는 extra `ACTION_PARAM`(JSON), 응답은 broadcast `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`, 7초 안에 회신. 에뮬레이터 Gleo(`ai.umos.ambientai`)가 사용자 앱의 schema.json을 스캔하는 것을 logcat으로 확인했다. 실제 호출에 Pleos 승인이 필요한지는 미확인. 문서: `/docs/pleos-only/app-integration-guide/guide/*`.
- **F14 휠 PTT·호출어 직접 수신은 불가** — Gleo가 음성 비서 역할을 독점한다. 대신 "PTT → Gleo → 플레오리에 적어줘 …" 간접 연동으로 바꾼다.
- **AmbientAi API(@PrivilegedApi)·Gleo LLM은 서드파티 비공개.** Gemini를 유지한다.
- **오디오 포커스(실측):** Pleos TTS SDK는 `USAGE_MEDIA`로 재생하고 포커스를 요청하지 않는다. 재생 전에 `setAudioAttributes(USAGE_ASSISTANT, CONTENT_TYPE_SPEECH)` + `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, LOSS_TRANSIENT면 stop, 끝나면 abandon(§8-1 3번).
- **위험: receiver는 권한 없이 exported다.** 문서화된 Gleo 호출 권한이 없어 다른 앱도 같은 action으로 메모를 넣을 수 있다. 완화: 입력 길이 상한(메모 500자, 파라미터 5,000자, items 최대 5개)과 `StructureContract` 검증, 세션이 없으면(시동 OFF) 실패 회신. 호출자 신원 검증은 Gleo 권한이 공개되면 추가한다.
- Pleos 문의: schema 승인 없이 에뮬레이터 호출 가능 여부와 절차, invokeWord 한국어 규칙, ACTION_PARAM 형식, TTS SDK 권장 usage, AmbientAi·LLM 개방 계획.

### 8-4. Gleo 우선 구조화 (2026-11-08 결정·실측)

- 사용자 결정: **Gleo 먼저, 정말 안 되면 Gemini.**
- **Gleo 음성 경로는 Gleo가 구조화한다.** 스키마에 `ADD_DRIVE_NOTES{original, items:array}`를 두면 Gleo(gpt-4o 기반 함수 호출)가 항목·유형·감정·키워드를 채워 보낸다. 받은 items는 `StructureContract`로 검증해 그대로 저장한다(Gemini 호출 없음). 계약 위반이나 `ADD_DRIVE_NOTE{note}`(원문만)로 오면 Gemini, 그것도 실패하면 RAW.
- **화면 버튼 경로는 Gemini를 유지한다.** 서드파티가 Gleo에 텍스트를 넘기는 공개 API가 없다. `ai.umos.intent.action.ASR` 테스트 broadcast는 개발용이라 제품에 쓰지 않는다. Pleos LLM SDK는 권한 없음(§7-1).
- 실측으로 확인한 Gleo 동작(문서와 다른 점 포함):
  - action 이름은 `ai.pleos.hello.ADD_DRIVE_NOTE`처럼 패키지(applicationId)가 붙어서 온다. 지금은 `com.pplab.pleory.ADD_DRIVE_NOTE`(§8-7).
  - 우리 TTS가 포커스를 잡으면 Gleo가 응답을 거부한다 → Gleo 경로에서는 "적어뒀어"를 말하지 않고 응답 mode=voice.
  - 에뮬레이터에서는 스키마 승인 없이 호출된다(blacklist 비어 있음). 라우팅은 비결정적이다 — "플레오리 앱에서 …"가 안정적.
  - 지연: 주입부터 수신까지 약 1.7~3.4초.
- 테스트 주입: `adb shell am broadcast --user 10 -a ai.umos.intent.action.ASR -p ai.umos.ambientai --es ASR "'플레오리 앱에서 항목별로 나눠서 적어줘, …'"`
- Pleos 문의: 실차 승인 필요 여부, 패키지 접두 action 이름, Gleo 경로 발화의 플랫폼 처리 범위(개인정보 고지 문구에 반영).

### 8-5. P1 리뷰 후 남은 것 (수용, 2026-11-08)

- TTS 발화별 포커스 추적은 "SDK가 발화마다 종료 이벤트를 하나 낸다"는 가정에 기댄다. 어긋나면 다음 발화의 onDone이 버려지고 포커스가 다음 say/stop까지 남을 수 있다 → 실측 로그로 이벤트 수를 확인하거나 begin()마다 상한으로 초기화.
- 전체 삭제 대기(`joinAll`) 중에 새로 시작된 DB 작업은 기다리지 않는다(범위 좁음).
- 기어 리스너 등록은 성공했는데 이벤트 없이 onFailed만 오는 차량은 대체 판정이 켜지지 않는다.
- Gleo 액션 두 개(원문/항목)를 하나로 합쳐 "항목별로" 없이도 items가 오는지 시험(§8-4 제안).
- 슬롭 정리 미반영: `GearSource.supported` 읽는 곳 없음, `DEBUG_ACTIVE` 매직넘버, 상수 동어반복 테스트.

### 8-6. v4 명세 반영 (2026-11-08)

기준: `Pleory_기능명세_v4.md`, 문서 다이제스트 `devpackv2/pleos_docs/`. 명세와 문서가 다르면 문서, 문서와 우리 에뮬레이터 실측이 다르면 실측을 따른다.

바꾼 것
- **Room 암호화(§3, 보안 가이드).** SQLCipher `net.zetetic:sqlcipher-android:4.17.0` + `androidx.sqlite:sqlite:2.6.2`, Room 에 `SupportOpenHelperFactory`. 4.18.0 이상은 `androidx.sqlite` 2.7.0을 끌어오고 2.7.0은 compileSdk 36을 요구해서 4.17.0에 둔다. DB 비밀번호는 무작위 32바이트(hex)이고 Android Keystore AES-GCM 키로 감싸 SharedPreferences `db_key`에 둔다(`data/DbKey.kt`). 감싼 값이 없거나 풀 수 없으면 기존 DB를 지우고 새로 만든다(첫 실행 때 평문 개발 DB 정리). 실측: `pleory.db` 첫 16바이트가 `SQLite format 3`이 아니고, db·wal 어디에도 발화 텍스트·`CREATE TABLE` 평문이 없다.
- **동의 화면(F12, §0-4).** 마이크(발화 텍스트 → Gemini)·위치(좌표 → 날씨·주소 조회, 지금은 fixture) 목적, Gleo 경로 메모를 Pleos 플랫폼이 처리한다는 고지, 앱 내 수집 목록, 암호화 저장을 넣고 고지 버전을 2로 올렸다(기존 동의자도 다시 묻는다). 동의 모델은 명세의 `Consent(mic, location, at)` 대신 **마이크·위치를 함께 묻는 명시적 동의 한 번**(상태+시각+버전)을 유지한다. 위치 좌표가 아직 밖으로 나가지 않아 둘을 나눌 실익이 없다. 날씨·주소 조회를 실제로 붙일 때 나눈다.
- **Gleo `SHOW_TODAY_CARD`(§11).** schema 세 번째 액션(component=activity, 파라미터 없음). 실측(Gleo 4.2.0.15): activity 액션은 `startActivity`(action `ai.pleos.hello.SHOW_TODAY_CARD`, FLAG_ACTIVITY_NEW_TASK)로 온다. MainActivity(singleTop)가 패키지 접두·맨 이름 action을 받고, 화면 게이트가 열려(주차) 있으면 카드를 연다 — 도착 카드 상태거나 지금 세션에 한마디가 있으면 지금 세션, 빈 세션이면 마지막으로 끝난 세션. 주행 중이면 화면을 바꾸지 않고 `fail` "The card is available only while parked." Gleo가 각각 "오늘 카드를 화면에 보여드릴게요" / "주차 중일 때만 … 보여줄 수 있어요"로 답하는 것을 확인했다.
- **Gleo 회신을 `setPackage("ai.umos.ambientai")`로 한정.** `APP_ACTION_RESPONSE`는 Gleo가 user 10에서 동적으로 등록한 receiver가 받는다(`dumpsys activity broadcasts`). ADD_DRIVE_NOTE·SHOW_TODAY_CARD 모두 한정 후에도 Gleo가 응답을 받는 것을 확인했다.
- **문의·신고(F9·F12, 심사 가이드).** 설정에 연락처를 보여준다. 주소는 임시값 `pleory.team@example.com` — 제출 전 교체(README에 사람 작업으로 표시).
- **요약 오디오 포커스(§1 예외).** 요약 중 LOSS_TRANSIENT면 멈추고 포커스 요청을 쥔 채 기다렸다가 GAIN에서 처음부터 다시 읽는다(SDK에 이어 읽기 없음). 재개 때 15초 상한도 다시 잰다. 손실이 15초를 넘으면 요약을 끝낸다. "적어뒀어"는 기존대로 일시 손실이면 생략. 실측: Gleo 호출로 `focus paused -2` → 약 5~14초 뒤 `regained, resume` → onDone. 이때 SDK가 `stop()`마다 onStop을 내는 것도 확인했다(§8-5 첫 항목의 가정과 맞다).
- **TrackPoint 적재 간격(§3·§12).** 위치 콜백 주기는 SDK가 정하므로(문서 미기재) 앱이 거른다: 화면이 보이면 5초, 백그라운드면 30초(`TrackSampler`). 데모 GPX는 30초 간격이라 카드 값(51분·23km)은 그대로다.

이미 맞았던 것
- 본문 글자 18px 이상: 가장 작은 글자가 20sp(밀도 160이라 20px).
- STT LISTENING 15초 상한에서 `stop()`, 다시 탭하면 취소.
- `distractionOptimized`, 철회 시 외부 전송 즉시 중단, 2단계 전체 삭제.

하지 않은 것
- TTS 선행 묵음 150~200ms: TextToSpeech 3.3.1에 묵음·SSML API가 없다(`speak(text, isFinal)`뿐). 지연 재생으로 흉내 내는 건 stop·포커스 경합을 늘려서 넣지 않았다.
- 철회 = 전체 삭제(F12 문구): 지금은 철회가 외부 전송만 멈추고 삭제는 설정의 "전체 삭제"(2단계 확인)로 따로 한다. 철회 한 번에 기록이 사라지는 게 맞는지 기획 확인이 필요하다.

따르지 않은 것과 이유
- **기어 API.** v4는 "Vehicle SDK 2.3.2에 기어 API 없음 → AAOS Car API"라고 쓰지만 우리 에뮬레이터에서는 `vehicle.getGear().registerGearSelection`·`getCurrentGearSelectionState`가 동작한다(TRD D4·§8-1 4번). 그대로 둔다. 기획 쪽에 이 차이를 알린다.
- **Gleo `ADD_NOTE{content}` + `mode: "silent"` + 앱 TTS "적어뒀어".** 실측 검증된 `ADD_DRIVE_NOTE`/`ADD_DRIVE_NOTES`와 `mode=voice`를 유지한다. 우리 TTS가 포커스를 잡으면 Gleo가 응답을 거부했고(§8-4), Gleo는 action 이름에 패키지를 붙여 보낸다.
- ~~패키지명 `com.pplab.pleory`~~ → 사용자 결정으로 바꿨다(§8-7).
- compileSdk(명세 스파이크 35), LLM 제공자(Gemini)는 바꾸지 않았다.

### 8-7. applicationId 변경 (2026-11-08, 사용자 결정)

- `applicationId`를 `com.pplab.pleory`로 바꿨다. Kotlin 패키지와 `namespace`는 `ai.pleos.hello` 그대로다(소스 이동 없음, 강제되는 것도 없었다). adb 컴포넌트는 `com.pplab.pleory/ai.pleos.hello.MainActivity`.
- Gleo: schema `packageName`을 바꾸고, 매니페스트 접두 action은 `${applicationId}.<FN>` 자리표시자로 쓴다(빌드 결과 `com.pplab.pleory.ADD_DRIVE_NOTE` 확인). 수신 판정은 `context.packageName` 접두 또는 맨 이름만 받는다(다른 접두는 거절).
- 실측: Gleo는 설치(`PACKAGE_ADDED`) 때 새 패키지 schema를 바로 읽었고 재부팅이 필요 없었다. 옛 패키지 삭제(`PACKAGE_REMOVED`)도 바로 반영됐다. `com.pplab.pleory.ADD_DRIVE_NOTE`(저장·Gemini 구조화·success 회신)와 `com.pplab.pleory.SHOW_TODAY_CARD`(카드·success 회신) 모두 동작. 두 패키지가 같이 깔린 동안은 invokeWord가 겹치므로 옛 앱을 먼저 지웠다.
- STT SERVER `registerApp`: 같은 Client ID로 새 패키지에서도 연결되고 토큰이 발급된다. 다만 인식 스트림은 gRPC `PERMISSION_DENIED: Unsupported endpoint or you do not have permission to use that endpoint`. 옛 applicationId로 다시 빌드해 같은 버튼을 눌러도 같은 오류여서, 패키지 변경 탓이 아니라 프로젝트 자격증명에 STT 서버 권한이 없는 것이다(LLM 400과 같은 계열, §7-1). 앱은 STT ON_DEVICE 고정(D5)이라 데모 경로에는 영향이 없다. Playground 쪽 권한 확인이 필요하면 사람 작업.
- 새 패키지는 데이터가 비어 시작한다(옛 패키지 DB·동의는 옮기지 않음). 권한은 `pm grant --user 10 com.pplab.pleory …`로 다시 줬다.

### 8-8. 구조화 LLM: Claude 1순위 + Gemini 대체 (2026-11-08, 사용자 결정)

- 이유: Gemini 무료 키가 계속 HTTP 429(할당량 소진)를 내서 데모 발화가 RAW로 남았다.
- 체인(`core/FallbackStructurer`): Claude `claude-haiku-5-5` 4.5초 → 실패하면(HTTP 오류·타임아웃·`stop_reason` refusal/max_tokens·형식·계약 위반) Gemini(남은 약 3.4초) → RAW. 전체 상한은 그대로 컨트롤러 8초이고, 취소되면 진행 중인 단계도 취소돼 늦은 응답이 두 번 저장되지 않는다. 동의 게이트(`ConsentGatedStructurer`)는 체인 전체를 감싼다. Gleo `ADD_DRIVE_NOTES` 경로는 LLM을 쓰지 않는다(그대로).
- 구현: 공식 Java SDK `com.anthropic:anthropic-java:3.3.0`(Android minSdk 28에서 그대로 동작, raw HTTP 불필요). 시스템 프롬프트는 Gemini와 같은 PRD §4-1 문구(`StructureContract.SYSTEM_PROMPT`), 사용자 메시지는 발화 텍스트뿐. `output_config.format` JSON 스키마({items:[{text,type,emotion,keywords}]}, additionalProperties false), `effort: low`, 생각 끔(Haiku 5.5는 effort high 이하에서 허용), `max_tokens` 1024, temperature 없음, SDK 재시도 0. 받은 JSON도 `StructureContract.parse`로 다시 검증한다. Haiku 5.5는 서버 쪽 거절 대체가 없어서 Gemini가 대체다.
- 키: `local.properties` `llm.claudeKey` 또는 셸 `PLEORY_CLAUDE_KEY` → `BuildConfig.CLAUDE_API_KEY`. `ANTHROPIC_API_KEY`는 읽지 않는다(셸에 있으면 사용자의 Claude Code 과금 키가 APK에 들어간다). 비어 있으면 Claude 단계를 건너뛰고 Gemini가 8초를 다 쓴다.
- 고지: 발화 텍스트 수신처가 Anthropic(1순위)·Google(대체)로 바뀌어 동의 문구를 고치고 고지 버전을 3으로 올렸다(기존 동의자도 다시 묻는다).
- 로그(`PleoryFlow`): `llm ok: claude`, `llm claude failed: claude http 429`처럼 단계와 분류만 남긴다. 본문·발화·키는 남기지 않는다.
- 실측(에뮬레이터, 2026-11-08): Gleo 메모 첫 호출 3.1초(클라이언트 생성 포함), 이후 데모 발화 1.2~1.7초, 모두 `llm ok: claude`. 데모 카드: 한마디 2개 → 항목 3개(감정↓, 할 일, 감정↑), 요약 "힘들게 시작해서 노을로 끝났어. 할 일 하나 있어."
- 미리 데우기(2026-11-08): 새 프로세스 첫 한마디가 클라이언트 생성·첫 TLS 연결로 Claude 4.5초를 다 써 RAW로 남는 일이 있었다. 앱 시작(동의가 있을 때)과 동의 직후에 IO 스레드에서 클라이언트를 한 번 만들고 `models.retrieve("claude-haiku-5-5")`(과금 없음, 발화 없음)로 연결을 연다. 한 번만 돌고, 데우는 중에 구조화가 오면 같은 클라이언트 초기화를 기다린다. 로그 `claude warm ok <ms>` / `claude warm failed <분류>`. 실측: force-stop 뒤 콜드 스타트 2회 모두 warm 약 1.1초, 데모 두 한마디 모두 `llm ok: claude`(첫 한마디 2.0~2.5초, 둘째 1.0~1.3초).

### 8-9. v2 범위: 지난 기록, 도착 리마인드, QR (2026-11-08)

기능명세 v4 의 F11·F10 과 신규 F15 를 영상에 넣기 위해 추가한다. 근거는 `.omc/ultragoal-brief-v2.md`.

**S2 지난 기록 (F11)**
- 진입은 정차 중 S0·S1 에서만 한다. 주행 중에는 `ScreenGate` 가 막는다.
- 조회는 오늘 0시 기준 6일 전 0시 이후에 시작한 세션을 최신순으로 가져온다. 행은 날짜, 구간, 시간, 거리, 감정 흐름 바, 항목 수.
- 행 탭은 해당 세션의 S1 카드를 연다. 요약 TTS 는 자동 재생하지 않는다.
- "할 일만" 필터는 미완료 할 일이 하나라도 있는 세션만 남긴다. 결과가 없으면 빈 상태를 보인다.
- 1열·2열 모두 같은 내용이다.

**도착 리마인드 (F15, 신규)**
- 트리거: 목적지가 확정된 세션이 도착하고 기어가 P 가 된 시점. 카드를 띄우는 기존 도착 판정과 같은 순간에 한 번 평가한다.
- 대상: 현재 세션이 아닌 이전 세션의 항목 중 `type = todo`, `done = false`, `remindedAt = null` 인 것. 오래된 순으로 정렬한다.
- 발화: TTS 한 줄에 개수와 첫 항목을 넣는다. 하나면 "어제 퇴근길에 할 일 하나 말했어. 거래처 서류 다시 보내기.", 여럿이면 "할 일 N개 말했어. 첫째는 …." 카드 상단에도 같은 내용을 띄운다.
- 알린 항목은 `remindedAt` 에 시각을 기록해 다시 알리지 않는다. 카드의 "했어"를 누르면 `done = true`.
- 주행 중에는 말하지 않는다. 기어가 P 가 아니면 평가하지 않는다.
- 데이터: `Item` 에 `done`(기본 false), `remindedAt`(null 허용)을 추가하고 Room 버전을 올려 마이그레이션한다. 기존 행은 `done = 0`, `remindedAt = NULL` 로 채운다.
- 테스트: 대상 선정, 한 번만 알림, 주행 중 억제, "했어" 완료, 마이그레이션.
- 태연 확인 필요: F15 는 기획서에 없는 신규 기능이다. 도착 시 먼저 말을 거는 동작이 "앱이 먼저 말을 걸지 않는다"(US-03)와 닿는 부분이므로 허용 범위를 확인한다.

**QR 로 폰에 가져가기 (F10)**
- 라이브러리는 zxing core(`com.google.zxing:core`)만 쓴다. 화면 렌더는 비트 매트릭스를 직접 그린다.
- 페이로드는 카드 평문이다. 제목, 통계 한 줄, 요약, 항목 목록 순으로 줄바꿈으로 잇는다. 서버와 URL 은 없다.
- 길이 상한은 오류 정정 수준 L 에서 QR 한 장에 안전하게 들어가는 바이트 수(UTF-8 약 1,000바이트)로 잡고, 구현 때 실측해 확정한다.
- 축약 규칙: 제목, 통계, 요약은 유지하고 항목을 뒤에서부터 빼며, 뺀 것이 있으면 마지막 줄에 "외 N개"를 붙인다. 문자 중간에서 자르지 않는다.
- 정차 중에만 열고, 카드(S1)와 S2 에서 들어간다.
- 테스트: 페이로드 생성, 길이 상한, zxing 으로 인코드 후 디코드한 결과가 원문과 같은지.
