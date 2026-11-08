# Pleory (플레오리) — 기능명세 v4
1차 구현 범위(S1 퇴근길 end-to-end) 기준. 접수양식 최종본(2026.11.8)과 용어·숫자를 맞췄고, 기획서에 없는 기능은 넣지 않는다.

**v4는 Pleos 공식 개발자 문서 약 130쪽(기획·설계 가이드 10, 디자인 가이드 33, 개발·연동 가이드, SDK 5종 프로그래밍 가이드, Gleo 연동 6, 테스트·등록·심사, 릴리즈 노트)과 공식 샘플 앱 3종(GitHub pleos-playground)을 전부 읽고 반영한 판이다.** v3까지의 추정 중 틀린 것은 §13에 "정정"으로 남겼다. 문서에 없는 값은 "(가정)", 1일차에 에뮬레이터에서 직접 확인할 것은 "(1일차 확인)".

- 개발 목표: **2일 안에 S1이 에뮬레이터에서 끝까지 도는 것**(§9). 남는 날은 영상 재촬영과 P1 보강
- 산출물: 시연 영상 1~2분, GitHub(private, 심사 계정 Collaborator), APK, README
- 함께 볼 것: `prototype.html`(인터랙션), `card_mock.png`(카드 시안), `pleos_docs/`(문서 다이제스트 30개 — 원문 수치·식별자 그대로)
- 공식 샘플: github.com/pleos-playground/pleos-sample-{vehicle,navi,fusedlocation}-app — 초기화·퍼미션·콜백 코드는 여기서 복사

---

## 0. 한 줄 정의와 원칙

**운전 중 떠오르는 대로 말하면 AI가 정리하고, 차가 시간·장소·주행 맥락을 붙여 통근마다 한 편씩 쌓아주는 Pleos 드라이브 저널.** 앱 카테고리: **Lifestyle**(Pleos 앱 카테고리 8개 중. 설계 시점에 정해야 하고, 카테고리를 벗어나는 기능은 제출 거부 사유).

지키는 원칙 5개 (기획서 "안전과 데이터 원칙"과 동일)

1. **주행 중** 앱은 먼저 말을 걸지 않는다. 사용자 발화에 대한 응답만 한다. (기어 P 후 카드·요약, 최초 설정·동의 화면은 정차 중이므로 예외)
2. 주행 중 응답은 **"적어뒀어"** 한 문장(TTS)뿐. **화면은 바뀌지 않는다** — 버튼의 "듣는 중" 상태만 예외. 분류·정리는 백그라운드.
3. 카드·요약·편집은 **목적지 도착 + 기어 P** 이후에만. 중간 정차(주유·편의점)는 기록만 이어진다.
4. 음성 원문은 저장하지 않는다. 텍스트만 차량 로컬(Room, **암호화**). **차 밖으로 나가는 데이터는 둘뿐** — LLM 처리용 발화 텍스트, 날씨·주소 조회용 좌표. 식별자는 붙이지 않고, 감정·유형 라벨과의 결합은 차량 안에서만.
5. 캘린더·메신저·SNS 등 외부 서비스와 연동하지 않는다. 기록이 차 밖으로 나가는 경로는 사용자가 QR을 직접 여는 것뿐.

1차 범위 밖: 동승 모드(S3), 월간 지도, 폰 앱·블루링크 연동, 앰비언트·공조 제안, EV 충전 맥락.

---

## 1. 상태 흐름

```
OFF ──앱 실행 / 주행 시작──▶ SESSION_OPEN ──버튼 1탭──▶ LISTENING ──STT 결과──▶ SAVED ──▶ SESSION_OPEN
                               │                                           (TTS "적어뒀어", LLM 백그라운드)
                               │ 정차(기어 P) but 도착 아님 ──▶ SESSION_OPEN 유지 (기록만)
                               │
                               └─ 정차(기어 P) + 도착 판정 ──▶ CARD ──닫기 / 앱 종료──▶ CLOSED
                                                                 └─ 주행 재개(기어 D) ──▶ SESSION_OPEN (같은 세션)
```

| 상태 | 들어가는 조건 | 하는 일 | 화면 |
|---|---|---|---|
| SESSION_OPEN | 앱 실행 후 **주행 상태 진입**(실차: 시동 ON 후 D. 에뮬레이터: 버추얼 콘솔에 시동 신호가 없으므로 **앱 실행 + 기어 P→D 전환**) | Session 생성. NaviHelper 목적지, 시각, Odometer 시작값, FusedLocation 콜백 등록(TrackPoint 적재), 날씨 1회(2일 데모는 fixture). 구간 판단(§6) | S0 (버튼 1개) |
| LISTENING | 대형 버튼 1탭 | `SpeechToText.request()`. 결과는 `onUpdated(stt, completed)`로 중간·최종 구분, 종료는 `onEndedRecognition()`. 앱은 15초 상한 타이머(가정)에서 `stop()` | 버튼만 "듣는 중"(탭 후 0.25초 안에 표시 — 디자인 가이드) |
| SAVED | `completed=true` 텍스트 수신 | Utterance 저장(텍스트 + 맥락 스냅샷, `llmStatus=PENDING`) → TTS "적어뒀어"(저장 후 2초 안 — 디자인 가이드) → LLM 구조화 백그라운드 → Item 저장, `DONE`. 실패 시 원문 1개 Item(type=raw), `FAILED` | 변화 없음 |
| CARD | **정차 판정 &&** 도착 판정 | 세션 종료값(시간·거리·정체분) → 카드 → 15초 요약 TTS(건너뛰기) | S1 카드 |
| CLOSED | 카드 닫기 또는 앱 종료 | 저장 완료, `cardSeen` 기록 | — |
| 재시작 | 다음 앱 실행, 정차 상태 | `cardSeen=false` 세션 카드 먼저. `llmStatus=PENDING/FAILED` 재처리 | 카드 → S0 |

**정차 판정(기어 P)** — Vehicle SDK에는 기어 API가 없다(2.3.2, 19도메인 중 Gear 없음). Pleos 정책은 "속도·기어 같은 표준 데이터는 AAOS Car API". 두 경로를 1일차에 같이 테스트해 하나로 확정(1일차 확인):
- (a) `CarUxRestrictionsManager.registerListener` → `onUxRestrictionsChanged` → `isRequiresDistractionOptimization()==false` = 정차. **Pleos 주행 제한 개발 가이드가 명시한 공식 경로.** 버추얼 콘솔 기어 P/D 변경이 이 콜백으로 오는지 확인. ADB `adb shell cmd hmg_car_service --service drivingstate driving_regulation on/off`로도 토글 가능.
- (b) `CarPropertyManager.getProperty(VehiclePropertyIds.GEAR_SELECTION)` + 콜백 — 퍼미션 `android.car.permission.CAR_POWERTRAIN`이 서드파티에 열려 있는지 확인(FAQ는 속도 `PERF_VEHICLE_SPEED`를 이 경로로 안내).

**도착 판정** — `NaviHelper.onDestinationArrived(DestinationArrivedInfo)` **존재 확인**(NaviHelper 3.0.5 이벤트 리스너). 단 "이벤트 수신 여부는 내비 앱 구현·상태에 따름"이고 에뮬레이터에 내비 앱이 있는지(1일차 확인). 보조: `FusedLocation.addProximityAlert(ProximityAlertRequest(lat, lng, 300f, setOf(ENTER)))`로 목적지 반경 300m(가정) 진입. 둘 다 없을 때만 정차 유지 N초(가정, 데모 5초).

빠뜨리기 쉬운 전이
- LISTENING 중 정차/앱 종료/버튼 재탭 → `stop()`, 저장 없음, TTS 없음. 재탭은 취소.
- STT 결과 빈 문자열·`onError` → 저장 없음, TTS 없음.
- CARD 중 주행 재개 → 카드 닫고 S0, 같은 세션 이어감.
- CLOSED 후 같은 실행에서 재출발 → 새 세션.
- 앱이 백그라운드로 밀렸다 돌아옴(사용자가 다른 앱을 올림) → `onResume`에서 상태 복원(§12).

예외
- 오프라인: STT는 `Mode.ON_DEVICE`라 동작. LLM은 `PENDING`으로 두고 다음 실행 때 재시도. 카드는 원문 그대로.
- STT 오인식: 주행 중 정정하지 않는다. 카드에서 수정·삭제.
- 발화 0개 세션(S2): 카드는 뜨되 항목 0개, 요약 고정 "오늘은 말이 없었네. 그래도 여기까지 왔어."
- 오디오 포커스: Pleos 가이드가 **"포커스 변화 대응 로직 반드시 구현"**. `AudioFocusRequest`(usage ASSISTANT 또는 ASSISTANCE_NAVIGATION_GUIDANCE 중 TTS SDK 문서 미기재 → 1일차 확인) 요청 → `LOSS_TRANSIENT`(내비 안내·Gleo 호출)면 TTS `stop()`, 끝나면 "적어뒀어"는 생략(저장은 됨), 요약은 재개. 2일 범위에서 요청+LOSS 처리까지(P0), 덕킹 세부는 P1.

---

## 2. 기능 목록

P0 = 2일 안에 반드시(영상에 나옴), P1 = 2일 뒤 시간이 남으면, P2 = 2차 PT 전까지.

| ID | 기능 | 트리거 | 처리 | 출력 | SDK / API (퍼미션) | 우선 |
|---|---|---|---|---|---|---|
| F0 | **주행 중 실행 허용(DO)** | 설치 | Pleos 정책: DO 미선언 앱은 Parking ✅ / Drive ⛔. AAOS 표준 `<meta-data android:name="distractionOptimized" android:value="true"/>`를 Activity에 선언(Pleos 문서에 선언 방법 자체는 없음 → 1일차 확인). `CarUxRestrictionsManager`로 주행 중 S1~S3 진입 차단, `ActiveRestrictions` 비트(NO_KEYBOARD 8, NO_VIDEO 16, LIMIT_CONTENT 32, NO_SETUP 64…) 반영 | — | AAOS car-lib | **P0, 1일차 첫 작업** |
| F1 | 세션 시작·맥락 수집 | 주행 상태 진입 | Session 생성. `Vehicle(ctx).initialize()` → `getOdometer()` 시작값. NaviHelper `initialize(onConnected, onFailed)` → `getDestinationInfo()`. FusedLocation `isSupported()` → `initialize()` → `registerFusedLocationCallback`. 날씨 fixture "비" | 없음 | Vehicle Odometer (`CAR_DRIVE`), NaviHelper (`NAVI_ROUTE`), FusedLocation (`FUSED_LOCATION`, dangerous → 런타임 요청) | P0 |
| F2 | 한마디 입력 | 버튼 1탭 | `SpeechToText(ctx, Mode.ON_DEVICE)` → `initialize` → `addListener` → `setLocale(Locale.KOREA)` → `request()` → `onUpdated(stt, completed)` → `stop()`. **ON_DEVICE는 앱 등록(Client ID) 불필요** | 버튼 상태만 | SpeechToText 3.3.1 (`STT_SERVICE`) | P0 |
| F3 | 즉답 | 최종 텍스트 수신 | 오디오 포커스 요청 → `TextToSpeech(ctx, Mode).speak("적어뒀어", isFinal=true)` → `onDone`에서 포커스 반납. 선행 묵음 150~200ms 권장(가이드) | TTS | TextToSpeech 3.3.1 (`TTS_SERVICE`) | P0 |
| F4 | 구조화 | F3 직후, 백그라운드 | §4-1 계약으로 외부 LLM 호출, Item 생성. 실패 시 raw. 키는 앱에 하드코딩 금지(보안 가이드) → 중계 서버 또는 공모전 APK에서는 BuildConfig + 제출 전 제거 | 없음 | 외부 LLM API | P0 |
| F5 | 맥락 부착 | F3 시점 | Utterance에 시각·좌표·장소 라벨(fixture 표)·Odometer·속도·정체 스냅샷. 속도: `VehicleSpeedListener`는 2.3.2에서 **제거됨**, `LocationData`에 speed 없음 → TrackPoint 좌표 적분(2일) / `PERF_VEHICLE_SPEED`(P1, FAQ 경로) | 카드 메타 줄 | FusedLocation, Vehicle | P0 |
| F6 | 구간 판단 | F1 | 내비 목적지명 "집"/"회사" → 그대로, 없으면 시각 규칙(§6). 집·회사 등록은 P1 | 카드 제목 | NaviHelper | P0 |
| F7 | 카드 | 정차 + 도착 | 통계 4, 감정 흐름 바(전 항목 순서), 항목 목록. 경로+핀은 마지막 | S1 | Room | P0 |
| F8 | 15초 요약 | 카드 표시 직후 | **로컬 규칙**(§4-2) → TTS → 건너뛰기. 자막은 정적 표시(애니메이션·자동 스크롤 금지 — 디자인 가이드) | 음성+자막 | TextToSpeech | P0 |
| F9 | 카드 편집·삭제 | 항목 탭 | 수정·유형 변경·삭제(원문 보존). **삭제는 P0** — 심사 가이드 "AI 생성 콘텐츠 앱은 부적절 콘텐츠 필터링 + 사용자 신고 기능 필수" | 즉시 반영 | Room | P0(삭제) / P1(편집) |
| F10 | QR로 폰에서 열기 | 카드 버튼 | 카드 텍스트 평문을 QR에 직접. 서버 없음. 한국 정책상 QR 로그인은 NO_SETUP(정차 중만) — 우리 설계와 일치 | QR 모달 | zxing | P1 |
| F11 | 나의 지도(주간) | 정차 중 | 최근 7일 세션 목록 + 흐름 바 | S2 | Room | P1 |
| F12 | 설정·동의 | 최초 실행(정차), 정차 중 | **동의 화면 1장**(마이크→발화 텍스트가 외부 LLM으로, 위치→좌표가 날씨·주소 조회로, 수집 목록) + 동의/거부, 철회 = 전체 삭제(보안·개인정보 가이드: 목적 고지·명시적 동의·앱 내 수집 목록·철회 시 즉시 삭제). 집·회사 등록, 버튼 위치, 문의·신고 연락처 | S3 | Room | **P0(동의 화면)** / P1(나머지) |
| F13 | 미열람 카드 재표시 | 다음 실행, 정차 | `cardSeen=false` 카드 먼저 | 카드 | — | P2 |
| F14 | **Gleo AI 음성 호출** | "플레오리, … 적어줘" | `assets/schema.json`의 `ADD_NOTE`(service) 수신 → F3~F5 → Callback(§11) | TTS "적어뒀어" | Gleo Interface | P1 — 에뮬레이터 포함 여부(1일차 확인) |

주행 중 화면 변화는 F2 버튼 상태뿐. 상태 텍스트에 발화 수를 올리지 않는다.

---

## 3. 데이터 모델 (Room, SQLCipher 암호화 — 보안 가이드 "기기 저장 민감정보는 SQLite 포함 암호화")

```kotlin
Session(id, profileId, startedAt, endedAt,
  segment: COMMUTE_IN | COMMUTE_OUT | OUTING,
  originLabel, destLabel, startLat, startLng, endLat, endLng,
  distanceKm, durationMin, weather, congestionMin, summaryText, cardSeen)
Utterance(id, sessionId, at, text,          // STT 최종 텍스트. 음성은 저장 안 함
  lat, lng, placeLabel, odometerKm, speedKmh, congested,
  llmStatus: PENDING | DONE | FAILED)
Item(id, utteranceId, order, text, editedText,
  type: THOUGHT | TODO | EMOTION | PERSON | RAW,
  emotion: DOWN | NEUTRAL | UP,              // 비감정 유형으로 바꾸면 NEUTRAL
  keywords, deleted)
TrackPoint(sessionId, at, lat, lng, speedKmh)   // 포그라운드 5초 / 백그라운드 30초 (가정)
Place(profileId, kind: HOME | WORK, lat, lng)
Consent(profileId, mic: Boolean, location: Boolean, at)   // F12
```

파생 규칙
- 거리: `Odometer.getTripCurrentDrivingDistance` 우선, 콘솔에서 0이면 TrackPoint 적분. 시간: `getTripCurrentDrivingTime` 또는 `endedAt - startedAt`.
- 속도·정체: TrackPoint 연속 두 점의 거리/시간. 20km/h 미만 누적 분 = `congestionMin`(가정). 카드 메타 "정체 {congestionMin}분 구간". 기획서 장표의 "출발 17분" = `at - startedAt`.
- 감정 색: DOWN `#E05A6A` / NEUTRAL `#E0A24A` / UP `#00AAD2`. 핀: 항목 1개면 그 색, 2개 이상이면 반반 분할.
- 운전자 프로필 ID(Pleos)로 전 테이블 파티션.

---

## 4. LLM 계약

### 4-1. 구조화 (F4) — 발화 텍스트만 전송, 빈 문자열이면 호출 안 함
```
system:
당신은 운전자가 운전 중에 떠오르는 대로 말한 한마디를 정리한다.
1. 여러 생각이 섞여 있으면 항목으로 나눈다. 한 항목은 한 문장. 최대 5개.
2. type은 thought / todo / emotion / person 중 하나. 3. emotion은 down / neutral / up.
4. 말투는 다듬되 뜻을 바꾸지 않는다. 없는 내용을 추가하지 않는다.
5. 정리할 수 없으면 원문 그대로 1개 항목(type=raw, emotion=neutral, keywords=[]).
JSON만 출력. 코드펜스·설명 금지.
user: "내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다"
assistant: {"items":[{"text":"내일 거래처 서류 다시 보내기","type":"todo","emotion":"neutral","keywords":["거래처","서류"]},{"text":"노을이 예뻤다","type":"emotion","emotion":"up","keywords":["노을"]}]}
```
실패(→ raw 1개, `FAILED`): 파싱 실패, 코드펜스/JSON 외 텍스트, 빈 배열, 6개 이상, 미지 type/emotion, 빈 text, 8초 타임아웃(가정, 요청 취소로 중복 저장 방지), 오프라인.

### 4-2. 15초 요약 (F8) — LLM 없음, 차 안 규칙
```
항목 0개 → "오늘은 말이 없었네. 그래도 여기까지 왔어."
감정 ≥2, 첫 down·끝 up → "힘들게 시작해서 {끝 up 항목 키워드}로 끝났어."
감정 ≥2 그 외 → "감정이 {down→가라앉았다 / neutral→그냥 / up→올라왔다}, …."
감정 1 → down "오늘은 좀 힘들었네." / 그 외 "오늘은 괜찮았네."     감정 0 → "생각이 좀 있었네."
+ 할 일 n → " 할 일 {하나/둘/셋} 있어."
```
40자 상한, 15초 타이머에서 `stop()`. P1에서 LLM 요약으로 바꿔도 **항목 텍스트만** 보낸다.

---

## 5. 화면 (4장) — 디자인 가이드 반영

| 화면 | 언제 | 구성 | 주행 중 |
|---|---|---|---|
| **S0 주행** | SESSION_OPEN | **portrait window(1 window)**. 하단 대형 버튼 1개("말하기"), 상단 고정 텍스트 "퇴근길 · 기록 중". 배경 단색(지도는 Driving view 담당) | ○ 1탭만 |
| **S1 카드** | CARD | `card_mock.png`는 landscape(2 window) 기준. **사용자가 윈도 바로 가로↔세로를 언제든 바꿀 수 있고 "모든 앱은 두 레이아웃에 맞게 설계"가 가이드 → 1열(portrait)·2열(landscape) 둘 다 P0.** 1열: 통계 → 흐름 바 → 요약 → 항목 → 경로 순 | × |
| **S2 나의 지도** | 정차 | 세션 목록 + 흐름 바 (미니맵 P2) | × |
| **S3 설정·동의** | 최초 실행(정차), 정차 | 동의 화면(F12), 집·회사, 버튼 위치, 전체 삭제(2단계), 문의·신고 | × |

수치(디자인 가이드): 본문 글자 **18px 이상**(12sp 권장의 1.5배, 실제 픽셀 최소 18px), 한 줄 80자·문단 3줄 이내, 대비 4.5:1, 중요 텍스트 5mm. 탭 후 응답 **0.25초**, 버튼 동작 2초, 앱 실행·콘텐츠 로드 10초. 동영상·애니메이션 이미지·자동 스크롤 금지. 레터박스 금지, 해상도 전환 시 상태 손실 금지. 시스템 폰트 Asta Sans(개성 강한 폰트 지양). 성능: 비내비 앱 주행 중 CPU 5% 미만, 정차 25% 이하, RAM 400MB 이하.

해상도·window px 수치는 텍스트 문서에 없음(Figma `Pleos_Connect_display_guide_215.fig`, 폼팩터 테스트 Display preset). 샘플 AVD는 2560×1440. 폼팩터: Connect-S(17/14.6/12.9인치, 앱 2개 1:1 또는 1개 2:0), Connect-W(2:1·1:2, 고급 차종), Connect-WF. **S1 아반떼는 Connect-S로 추정**(차종-폼팩터 매핑은 문서에 없음) → 폼팩터 테스트 콘솔(포트 8095/8096)로 1:1·2:0 둘 다 확인.

---

## 6. 구간 판단 (F6)
1. Place 있으면: 목적지 또는 도착 위치가 집 300m(가정) → 퇴근, 회사 → 출근, 그 외 외출.
2. 없으면: 목적지명 "집"/"회사" → 그대로. 아니면 출발 06~10시 출근, 16~21시 퇴근, 나머지 외출(가정). 도착 위치로 보정.
3. 집·회사 등록 안내는 최초 실행 정차 중 1회. 주행 중 질문 없음.

---

## 7. SDK · 퍼미션 · 의존성 (문서 확정)

Maven: `maven("https://nexus-playground.pleos.ai/repository/maven-releases/")`
```
ai.pleos.playground:Vehicle:2.3.2   ai.pleos.playground:NaviHelper:3.0.5   ai.pleos.playground:FusedLocation:1.1.3
ai.pleos.playground:SpeechToText:3.3.1   ai.pleos.playground:TextToSpeech:3.3.1
```
샘플 기준: minSdk 28(사이버 보안 요건), compileSdk 35, AGP 8.8.x, Kotlin 2.0, JDK 17, Compose. 모든 SDK 호출은 비동기 `onComplete/onFailed`, 콜백 완료 전 `release()` 금지.

| 기능 | API | 퍼미션 | 상태 |
|---|---|---|---|
| 정차/주행 | `CarUxRestrictionsManager`(공식 경로) / `CarPropertyManager GEAR_SELECTION` | — / `android.car.permission.CAR_POWERTRAIN`(서드파티 가능 여부 1일차 확인) | 콘솔 기어 P/R/N/D 지원 |
| 주행 거리·시간 | `Vehicle.getOdometer()` → `getTripCurrentDrivingDistance/Time`, `registerDistanceDriven` | `pleos.car.permission.CAR_DRIVE`(normal) | 확정. 콘솔 0~1,000,000km |
| 목적지·도착 | `NaviHelper.getDestinationInfo()`, `onDestinationArrived`, `onDestinationChanged`, `onCurrentLocationInfo` | `pleos.car.permission.NAVI_ROUTE` | 확정(API). 에뮬레이터 내비 앱 유무 1일차 확인. 내비 탑재 차량만 완전 호환 |
| 위치 | `FusedLocation.isSupported()` → `initialize()` → `registerFusedLocationCallback`, `addProximityAlert` | `pleos.car.permission.FUSED_LOCATION`(**dangerous, 런타임**) — 차량 개인정보 설정 차단 시 `showLocationAccessBlockedPopup()` | 확정. 에뮬레이터 주입 `adb emu geo fix <lng> <lat>`(경도 먼저). **GPX 재생은 문서에 없음** |
| 동승 | `Vehicle.getDoor()`, `getSeat()`(좌석 점유) | `CAR_DOORS`, `CAR_SEATS` | 확정. 콘솔 도어 4개·좌석 점유 Empty/Occupied |
| STT | `SpeechToText(ctx, Mode.ON_DEVICE)` | `pleos.car.permission.STT_SERVICE`(RECORD_AUDIO 언급 없음) | 설명회 38:28 사용 가능 안내. **My Project 상태표는 STT·TTS·FusedLocation "준비 중"** → ON_DEVICE로 시작, 안 되면 즉시 보고. 에뮬레이터 Extended Controls → Microphone |
| TTS | `TextToSpeech(ctx, Mode)` → `speak(text, isFinal)` → `EventListener{onReady,onStart,onDone,onStop,onError}` | `pleos.car.permission.TTS_SERVICE` | 동일 |
| LLM | 외부 API | `INTERNET` | 허용(37:56). 에뮬레이터 2.1.0에서 LLM SDK 제거됨 |
| Gleo | §11 | — | 에뮬레이터 포함 여부 미기재(1일차 확인) |

Vehicle SDK 퍼미션은 **마켓과 사전 협의·승인된 것만 선언 가능, 미승인 선언 시 심사 불통과** → 선언은 위 표의 것만. 모델별 가용성은 `checkXXXCapabilities`/`isSupported`로(모델명 분기 금지).

에뮬레이터: AVD `Pleos Connect v2.1.0`, API 34, **Cold boot 필수**(quick boot면 VHAL 불가), RAM 4GB. Virtual Console: `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/`. 콘솔에 **시동·속도·GPS·내비 없음**.

---

## 8. 에뮬레이터 시연 절차 (S1, 영상 대본 겸용)

| 순서 | 조작 | 앱 | 화면 |
|---|---|---|---|
| 1 | 앱 실행(정차 상태). 최초면 동의 화면 → 동의. 내비 목적지 "집" 설정(내비 앱 있을 때) | — | S3 → S0 |
| 2 | Virtual Console 기어 **P → D**. `geo fix` 스크립트로 회사→집 좌표 재생 시작 | 세션 시작, 구간=퇴근 | S0 "퇴근길 · 기록 중". **주행 중에도 앱이 떠 있어야 함(F0)** |
| 3 | (18:57 지점, 저속 구간) 버튼 1탭 → "오늘 팀장 때문에 진짜 힘들었다" | STT → 저장 → TTS → LLM | 버튼 "듣는 중" → 원래대로. 그 외 변화 없음 |
| 4 | (19:08 지점) 버튼 1탭 → "내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다" | 항목 2개 | 동일 |
| 5 | 목적지 좌표 도착(ProximityAlert 또는 `onDestinationArrived`), 기어 **D → P** | 종료값 → 카드 | S1: 51분 · 23km · 2 한마디 · 3 항목, ↓→↑ |
| 6 | — | 요약 TTS "힘들게 시작해서 노을로 끝났어. 할 일 하나 있어." | 자막 + 건너뛰기 |
| 7 | (P1) 항목 탭 → 유형 변경 후 되돌림 / 삭제 | 원문 보존 | 즉시 반영 |
| 8 | (P1) "폰에서 열기" | QR | 폰으로 스캔 |
| 9 | (P1) "이번 주" | 목록 | S2 |
| 10 | 카드 닫기 | 저장 완료 | — |

영상 1~2분, 2·3·4·5·6은 한 테이크. 좌표 재생 스크립트(회사→집, 올림픽대로·한강대교 경유, 저속 구간 포함, 5초 간격 `adb emu geo fix`)와 좌표→라벨 fixture 표는 태연 준비.

---

## 9. 개발 순서 — 2일

**1일차 첫 3시간(문서 근거)** — 여기서 설계의 분기가 다 결정된다.

| 시간 | 할 일 | 판단 |
|---|---|---|
| 0:00–0:40 | AVD(Pleos Connect v2.1.0 / API 34 / **Cold boot** / 4GB) 구동. `adb forward tcp:8088 tcp:8088` → Virtual Console에서 기어 P↔D 바뀌는지 확인. Extended Controls → Microphone ON. GitHub private repo 생성, 첫 커밋 | 환경 OK |
| 0:40–1:30 | **정차/주행 스파이크(최대 리스크)**: 빈 프로젝트(minSdk 28, compileSdk 35) + `android.car`로 (a) `CarUxRestrictionsManager.registerListener` (b) `CarPropertyManager GEAR_SELECTION` 두 경로 동시 시도. 콘솔 기어 변경 때 logcat에 콜백이 오는 쪽으로 확정. 둘 다 안 오면 `adb shell cmd hmg_car_service --service drivingstate driving_regulation on/off`로 (a)만이라도 | F0·F7·§8의 "정차 판정" 확정 |
| 1:30–2:15 | 의존성 5개 + 퍼미션(`CAR_DRIVE`, `NAVI_ROUTE`, `STT_SERVICE`, `TTS_SERVICE`, `FUSED_LOCATION` 런타임 — 샘플 MainActivity.kt 복사) + DO meta-data. `Vehicle.initialize()` → `getOdometer().getCurrentDistanceDriven` 1회로 서비스 연결 확인 | SDK 연결 OK |
| 2:15–3:00 | **STT/TTS 스파이크**: `SpeechToText(ctx, Mode.ON_DEVICE)` → `setLocale(Locale.KOREA)` → `request()` → `onUpdated(…, completed=true)` 로그. `TextToSpeech(ctx, Mode.ON_DEVICE).speak("적어뒀어")`. 안 되면 **즉시 태연에게 보고**(My Project 상태표 "준비 중" 때문일 수 있음) → 대체: Android 표준 `SpeechRecognizer`/`TextToSpeech`로 파이프라인 먼저, SDK는 병행 | F2·F3 경로 확정 |
| 병행 | `NaviHelper.initialize(onConnected, onFailed)` — `onFailed`면 도착 판정은 ProximityAlert/N초로 확정. `adb emu geo fix` 3점 주입으로 FusedLocation 콜백 확인 | 도착 판정 확정 |

| | 이후 | 완료 기준 |
|---|---|---|
| 1일차 오후 | 디버그 텍스트 입력 → Room(암호화) → TTS → LLM 구조화 + raw 폴백 → Item. 동의 화면 1장 | 텍스트 넣으면 "적어뒀어", Item 2개 저장 |
| 1일차 밤 | 버튼 → STT 연결, 맥락 스냅샷(시각·좌표·라벨 fixture·Odometer), 구간 판단 §6-2, 오디오 포커스 요청+LOSS 처리 | 실제 말하면 저장 |
| 2일차 오전 | 정차+도착 → 종료값 → 카드(1열·2열), 삭제 | 시연 1~5 |
| 2일차 오후 | 로컬 요약 + TTS + 건너뛰기 → 시연 2~6 한 테이크 **영상 1차본** | 영상 완성 |
| 2일차 밤 | README(실행 방법·테스트 환경·fixture 명시·APK 위치), APK, Collaborator 초대·접근 확인 | 링크 3개 |
| 남는 날 | 경로+핀, F9 편집, F10 QR, F11, F12 나머지, F14 Gleo → 영상 재촬영 | — |

뺀 것(2일): 날씨 API·역지오코딩(fixture), LLM 요약, 집·회사 등록, 항목 편집, 덕킹 세부, 미열람 카드, Gleo.

---

## 10. 정해야 할 것 (태연 ↔ 욱창)
1. LLM 공급자와 키 — 보안 가이드상 하드코딩 금지. 공모전 APK는 BuildConfig(제출 전 키 제거·로테이션), 마켓용은 중계 서버.
2. 좌표 재생 스크립트와 fixture 표(좌표→"올림픽대로"/"한강대교"/"집") — 태연, 1일차 오전까지.
3. 정차 판정 (a)/(b) 중 확정 — 1일차 0:40–1:30 결과로.
4. Gleo — 에뮬레이터 포함 여부. 포함되면 §11을 2일차 밤 이후로.
5. 2차 PT용 출시 준비: 개인정보처리방침 URL, 위치정보 이용약관 URL(위치정보 사업 신고 대상 여부 검토), 아이콘 512×512, 스크린샷 2~8장(2560×1380 등), 영문 태그, IARC.

---

## 11. Gleo AI 연동 — App Integration Guide 기준 (P1)

흐름: 사용자 발화 → Gleo AI가 App Schema의 function call 생성 → Gleo Action(Intent)으로 앱 호출 → 앱이 Callback(Broadcast) → Gleo가 최종 답변. **스키마는 APK의 `assets/schema.json`** — Gleo AI 앱이 부팅 시 AssetManager로 전 앱을 스캔(1회)하고 설치·삭제 때 갱신. 승인 후 자동 활성.

```json
{ "packageName": "com.pplab.pleory",
  "appDescription": "A drive journal that keeps what the driver says while driving, organized with time and place context.",
  "invokeWord": "플레오리", "version": "1.0.0",
  "actionCalls": [
    { "type": "function", "function": { "name": "ADD_NOTE",
      "description": "Save what the driver just said as a journal note for the current drive.",
      "parameters": { "type": "object", "properties": { "content": { "type": "string", "description": "What the driver said, as spoken." } }, "required": ["content"] },
      "component": "service" } },
    { "type": "function", "function": { "name": "SHOW_TODAY_CARD",
      "description": "Open the summary card of the current or most recent drive.",
      "parameters": { "type": "object", "properties": {}, "required": [] },
      "component": "activity" } } ] }
```
규칙: actionCalls ≤3, name 대문자_밑줄, description 영어·예시 금지, parameter type 단일(string/number/boolean/array), `invokeWord`는 예시에만 있음.

수신: Intent `action` = function name, extra `"ACTION_PARAM"` = 파라미터 JSON 문자열.
```xml
<receiver android:name=".GleoReceiver" android:exported="true">
  <intent-filter><action android:name="ADD_NOTE"/><category android:name="android.intent.category.DEFAULT"/></intent-filter>
</receiver>
```
```kotlin
val content = JSONObject(intent.getStringExtra("ACTION_PARAM") ?: "{}").optString("content")
if (content.isBlank()) { callback("ADD_NOTE","asking","What should I note down?"); return }
repo.saveUtterance(content, vehicleContext.snapshot())           // F3~F5
callback("ADD_NOTE","success","Noted.", data = """[{"length":${content.length}}]""", mode = "silent")
tts.speak("적어뒀어")
```
Callback: `Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")` + extras `packageName`, `response`(JSON `{functionName,status,message,data,mode}`) → **`setPackage(Gleo 패키지)` 지정 후** `sendBroadcast` (보안 가이드: 민감정보를 수신자 제한 없는 암시적 브로드캐스트로 보내지 않음 → `data`에 발화 원문 넣지 않음). `status` success/asking/fail, `message` 영어 한 문장, `mode: "silent"`로 Gleo TTS 대신 앱이 말함. **Gleo는 7초 후 Action 취소** → Callback은 저장 직후 즉시, LLM은 그 뒤. `SHOW_TODAY_CARD`는 주행 중이면 `fail` "The card is available only while parked."

주의: Gleo가 `content`를 발화 그대로 넘긴다는 보장 없음(LLM 파라미터 추출) → "개떡같이 말해도" 데모는 F2 경로로.

---

## 12. 앱 구조·화면 모델 — 기획 가이드 기준

| 가이드 | Pleory |
|---|---|
| 화면 = Driving view(운전자 쪽 고정, 앱이 가리거나 종료 불가) + 앱 영역(최대 2앱, window 1~2) | 지도·주행 정보는 Driving view. S0에 지도 없음. 카드가 내비를 가리는 일은 구조상 없음 |
| portrait = window 1(핵심), landscape = window 2(확장). manifest `screenOrientation`(미지정=portrait). **사용자가 윈도 바·앱 윈도 버튼으로 언제든 전환. 모든 앱은 두 레이아웃 설계** | S0 portrait. S1은 1열·2열 둘 다 P0 |
| 새 앱은 현재 표시 앱 방향을 따름 | 카드가 portrait로 열릴 수 있음 → 1열 필수 |
| 사라진 앱은 백그라운드에 남을 수 있음, 다시 foreground 가능 | `onResume` 상태 복원. 해상도 전환 시 상태 손실 금지(심사 항목) |
| 백그라운드: 화면 갱신·애니메이션·센서 중단, 타이머·폴링·네트워크 최소화. 비내비 앱 주행 중 CPU 5% 미만, RAM 400MB 이하 | TrackPoint 5초/30초, LLM 큐, 애니메이션 없음 |
| 프로세스 재생성 대비 상태 저장 | Room 즉시 쓰기, `llmStatus` |
| 한국은 지역 정책 "그 외 지역": 주행 중 키보드·검색 허용, ⛔ 디지털키·도어·시트·차량설정·동영상·QR 로그인(NO_SETUP)·매뉴얼 | QR·설정은 정차 중만 — 현 설계와 일치 |

---

## 13. v3 → v4 정정 (문서로 확인된 것)

| v3 | 정정 | 근거 |
|---|---|---|
| "기어 P 감지 = Vehicle SDK 주행 정보" | Vehicle SDK 2.3.2에 **기어 API 없음**. 표준 데이터(속도·기어)는 **AAOS Car API** — Pleos SDK API 정책 | pleos-connect-sdk-api, vehicle.listener, 샘플 README |
| "CarPropertyManager 직접 접근 불가 — 폴백 아님" | 단정 틀림. FAQ·API 호환성 문서가 `CarPropertyManager`·`VehiclePropertyIds`로 안내. 퍼미션 범위만 1일차 확인 | vehicle-app-faq, api-compatibility |
| "시동 ON = 세션 시작"(에뮬레이터) | 버추얼 콘솔에 **시동 신호 없음**. 세션 시작 = 앱 실행 + 기어 D | virtual-console-guide |
| "GPX 경로 재생" | 문서·샘플에 없음. `adb emu geo fix <lng> <lat>`(경도 먼저) | fusedlocation 샘플 README |
| "FusedLocation Location.speed" | `LocationData`에 speed 없음(lat, lng, altitude, heading, confidence, timestamp). `VehicleSpeedListener`는 2.3.2 제거 | FusedLocation model, Vehicle release note |
| "RECORD_AUDIO, ACCESS_FINE_LOCATION" | `STT_SERVICE`, `TTS_SERVICE`, `FUSED_LOCATION`(dangerous), `CAR_DRIVE`, `NAVI_ROUTE` | 각 SDK declaring-permissions |
| "STT 종료 조건 SDK 기본값" | `onUpdated(stt, completed)`, `onEndedRecognition()`. HYBRID 기본은 서버 등록 필요 → **ON_DEVICE** | SpeechToText intro, result-listeners |
| "도착 이벤트(확인)" | `onDestinationArrived(DestinationArrivedInfo)` 존재. 반경은 `addProximityAlert` | NaviHelper event-listeners |
| "QR 서버 없음, Room 평문" | Room **암호화**, 키 하드코딩 금지, 브로드캐스트 `setPackage` | app-security-guide |
| "동의 = 런타임 퍼미션" | 목적 고지·명시적 동의·앱 내 수집 목록·철회 시 삭제 → 동의 화면 P0 | security-privacy, review-privacy |
| "글자 최소 16sp" | 실제 픽셀 최소 **18px** | typography |
| "응답 지연 상한 측정 후 설정" | **0.25초 / 2초 / 10초** | design intro, review-functionality |
| "오디오 포커스 P2" | **반드시 구현** → 요청+LOSS 처리 P0 | performance, audio-focus |
| "1920×720 기본값" | 수치는 Figma·폼팩터 preset에만. 샘플 AVD 2560×1440 | layout, window, form-factor |
| "스키마 콘솔 업로드?" | `assets/schema.json`, intent-filter에 DEFAULT 카테고리 | deliver-schema |
| 카테고리 미기재 | **Lifestyle** 명기 | app-category |

## 14. 문서 다이제스트 (`pleos_docs/`)
A(기획·디자인·심사) 22개, B(개발·SDK·Gleo·에뮬레이터) 9개. 각 파일 맨 위에 URL. 코드를 쓸 때는 여기서 식별자를 복사하고, 다이제스트에 없는 메서드 시그니처는 GitHub 샘플 3종에서.
