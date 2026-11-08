# Pleory (플레오리) — 기능명세 v2
1차 구현 범위(S1 퇴근길 end-to-end) 기준. 접수양식 최종본(2026.11.8)과 용어·숫자를 맞췄고, 기획서에 없는 기능은 넣지 않는다. v2는 Pleos/AAOS 개발자 관점의 독립 검토를 반영한 판이다(DO 선언, 도착 판정, 요약 로컬화, 데이터 모델 보강).

- 개발 목표: **2일 안에 S1이 에뮬레이터에서 끝까지 도는 것**(§9). 남는 날은 영상 재촬영과 P1 보강
- 산출물: 시연 영상 1~2분, GitHub(private, 심사 계정 Collaborator), APK, README
- 함께 볼 것: `prototype.html`(인터랙션), `card_mock.png`(카드 화면 시안)
- 표기: "(가정)" = 기획서에 없는 숫자로 구현 편의상 정한 값. "(확인)" = 1일차에 Pleos 문서에서 직접 확인해 적어 넣을 것

---

## 0. 한 줄 정의와 원칙

**운전 중 떠오르는 대로 말하면 AI가 정리하고, 차가 시간·장소·주행 맥락을 붙여 통근마다 한 편씩 쌓아주는 Pleos 드라이브 저널.**

지키는 원칙 5개 (기획서 "안전과 데이터 원칙"과 동일)

1. **주행 중** 앱은 먼저 말을 걸지 않는다. 사용자 발화에 대한 응답만 한다. (기어 P 후 카드·요약, 최초 설정 안내는 정차 중이므로 예외)
2. 주행 중 응답은 **"적어뒀어"** 한 문장(TTS)뿐. **화면은 바뀌지 않는다** — 버튼의 "듣는 중" 상태만 예외. 분류·정리는 백그라운드.
3. 카드·요약·편집은 **목적지 도착 + 기어 P** 이후에만. 중간 정차(주유·편의점)는 기록만 이어진다.
4. 음성 원문은 저장하지 않는다. 텍스트만 차량 로컬(Room). **차 밖으로 나가는 데이터는 둘뿐** — LLM 처리용 발화 텍스트, 날씨·주소 조회용 좌표. 식별자는 붙이지 않고, 감정·유형 라벨과의 결합은 차량 안에서만.
5. 캘린더·메신저·SNS 등 외부 서비스와 연동하지 않는다. 기록이 차 밖으로 나가는 경로는 사용자가 QR을 직접 여는 것뿐.

1차 범위 밖(만들지 않음): 동승 모드(S3), 월간 지도, 폰 앱·블루링크 연동, 앰비언트·공조 제안, EV 충전 맥락.

---

## 1. 상태 흐름

```
OFF ──시동 ON──▶ SESSION_OPEN ──버튼 1탭──▶ LISTENING ──STT 결과──▶ SAVED ──▶ SESSION_OPEN
                     │                                                 (TTS "적어뒀어", LLM 백그라운드)
                     │ 기어 P (중간 정차) ──▶ SESSION_OPEN 유지 (기록만)
                     │
                     └─ 기어 P + 도착 판정 ──▶ CARD ──시동 OFF / 닫기──▶ CLOSED
                                               └─ 기어 D (재출발) ──▶ SESSION_OPEN (같은 세션 이어감)
```

| 상태 | 들어가는 조건 | 하는 일 | 화면 |
|---|---|---|---|
| SESSION_OPEN | 시동 ON(앱 실행) | Session 생성. NaviHelper 목적지, 시각, Odometer 시작값, FusedLocation 스트림(TrackPoint 적재), 날씨 1회 조회. 구간 판단(§6) | S0 (버튼 1개) |
| LISTENING | 대형 버튼 1탭 | SpeechToText 시작. 종료 조건은 SDK 기본값(확인). 앱은 15초 상한 타이머만 둔다(가정) | 버튼만 "듣는 중" |
| SAVED | STT 텍스트 수신 | Utterance 저장(텍스트 + 맥락 스냅샷, `llmStatus=PENDING`) → TTS "적어뒀어" → LLM 구조화 백그라운드 → Item 저장, `llmStatus=DONE`. 실패 시 원문 1개를 Item(type=raw), `llmStatus=FAILED` | 변화 없음 |
| CARD | 기어 P **&&** 도착 판정: NaviHelper 도착 이벤트(확인) ‖ 현재 위치가 목적지 반경 300m(가정) ‖ 목적지 없을 때 P 유지 N초(가정, 데모 5초) | 세션 종료값 계산(시간·거리·정체분) → 카드 표시 → 15초 요약 TTS(건너뛰기 가능) | S1 카드 |
| CLOSED | 시동 OFF 또는 카드 닫기 | 저장 완료. `cardSeen` 기록 | — |
| 재시작 | 다음 시동 ON | `cardSeen=false` 세션이 있으면 **정차 상태에서** 먼저 표시. `llmStatus=PENDING/FAILED` 재처리 | 카드 → S0 |

빠뜨리기 쉬운 전이

- LISTENING 중 시동 OFF / 기어 P / 버튼 재탭 → STT 취소, 저장 없음, TTS 없음. 재탭은 "취소"로 동작.
- STT 결과가 빈 문자열·오류 → 저장 없음, TTS 없음(원칙 1·2).
- CARD 중 기어 D(다시 출발) → 카드 닫고 S0, 같은 세션을 이어간다(`endedAt` 재계산).
- CLOSED 후 같은 시동에서 재출발 → 새 세션.
- "목적지 없을 때 P 유지 N초"는 중간 정차와 구분이 안 되는 한계가 있다(원칙 3과 충돌 가능). 데모는 목적지 있는 S1만 찍는다.

예외

- 오프라인(지하주차장): STT 결과는 로컬 저장, LLM은 `PENDING`으로 두고 다음 시동 때 재시도. 카드는 원문 그대로 보여준다.
- STT 오인식: 주행 중 정정하지 않는다. 카드에서 수정·삭제.
- 발화가 없는 세션(S2): 카드는 뜨되 항목 0개. 요약 문구는 고정 "오늘은 말이 없었네. 그래도 여기까지 왔어."
- 내비 음성 안내와 TTS 겹침(오디오 포커스 양보): P2. 에뮬레이터에는 내비 음성이 없어 2일 범위에서 다루지 않는다.

---

## 2. 기능 목록

P0 = 2일 안에 반드시(영상에 나옴), P1 = 2일 뒤 시간이 남으면(없어도 영상은 성립), P2 = 2차 PT 전까지.

| ID | 기능 | 트리거 | 처리 | 출력 | SDK / API | 우선 |
|---|---|---|---|---|---|---|
| F0 | **주행 중 실행 허용(DO)** | 앱 설치 | MainActivity에 `distractionOptimized=true` meta-data 선언(**레포에 이미 선언됨**, 기어 D 실측은 Virtual Console로 확인 필요). `CarUxRestrictionsManager` 리스너로 주행 중 S1~S3 진입 차단. **미선언 시 Pleos 정책상 정차에서만 실행되어 S0 자체가 뜨지 않는다** | — | AAOS car-lib | **P0, 1일차 오전** |
| F1 | 세션 시작·맥락 수집 | 시동 ON | Session 생성, 목적지·시각·Odometer·위치 수집. 날씨는 2일 데모에서 고정값 "비"(fixture) | 없음(조용히) | Vehicle, NaviHelper, FusedLocation | P0 |
| F2 | 한마디 입력 | 화면 대형 버튼 1탭 | STT 시작·종료 | 버튼 상태 변화만 | SpeechToText SDK | P0 |
| F3 | 즉답 | STT 텍스트 수신 | 저장 직후 한 문장 | TTS "적어뒀어" | TextToSpeech SDK | P0 |
| F4 | 구조화 | F3 직후, 백그라운드 | §4-1 계약으로 LLM 호출, Item 생성. 실패 시 raw | 없음(카드에서 보임) | 외부 LLM API | P0 |
| F5 | 맥락 부착 | F3 시점 | Utterance에 시각·좌표·장소 라벨·Odometer·속도·정체 여부 스냅샷. 장소 라벨은 2일 데모에서 좌표→라벨 fixture 표(§7) | 카드 메타 줄 "18:57 · 올림픽대로 · 정체 23분 구간" | FusedLocation, Vehicle | P0 |
| F6 | 구간 판단 | F1 | 2일 범위: 내비 목적지명이 "집"이면 퇴근, 아니면 시각 규칙(§6-2). 집·회사 등록은 P1 | 카드 제목 "퇴근길 51분" | NaviHelper | P0 |
| F7 | 카드 | 기어 P + 도착 판정 | 우측 통계 4(시간·거리·한마디·항목), 감정 흐름 바(**전 항목 순서대로**, 유형 무관), 항목 목록. 좌측 경로+핀은 마지막에(없어도 영상 성립) | S1 카드 | Vehicle(기어), Room | P0 |
| F8 | 15초 요약 | 카드 표시 직후 | **2일 범위는 로컬 규칙**(§4-2)으로 문장 조립 → TTS → 건너뛰기. LLM 요약은 P1 | 음성 + 자막 | TextToSpeech | P0 |
| F9 | 카드 편집 | 항목 탭 | 텍스트 수정, 유형 변경, 삭제. 원문 보존 | 즉시 반영 | Room | P1 |
| F10 | QR로 폰에서 열기 | 카드 "폰에서 열기" | 카드 텍스트(제목·메타·항목)를 **줄바꿈 평문**으로 QR에 직접. 서버 없음. 1KB 초과 시 항목 텍스트만 | QR 모달 | zxing 등 | P1 |
| F11 | 나의 지도(주간) | 정차 중, 카드의 "이번 주" | 최근 7일 세션 목록 + 감정 흐름(경로 미니맵은 P2) | S2 | Room | P1 |
| F12 | 설정 | 정차 중 | 집·회사 등록, 버튼 위치(좌/우), 전체 삭제 | S3 | Room | P1 |
| F13 | 미열람 카드 재표시 | 다음 시동 ON, 정차 중 | `cardSeen=false` 세션 카드 먼저 | 카드 | — | P2 |
| F14 | 휠 음성 버튼 연동 | 플랫폼 허용 시 | F2와 동일 | — | 확인 중 | P2 |

주행 중 화면 변화는 F2의 버튼 상태(기본/듣는 중)뿐이다. **상태 텍스트에 발화 수를 올리지 않는다**(화면 변화에 해당). 나머지 화면 전환은 모두 기어 P 이후.

---

## 3. 데이터 모델 (Room)

```kotlin
Session(
  id, profileId,               // Pleos 운전자 프로필 ID — 모든 조회의 파티션 키
  startedAt, endedAt,
  segment: COMMUTE_IN | COMMUTE_OUT | OUTING,   // 출근 / 퇴근 / 외출
  originLabel: String?, destLabel: String?,     // "회사", "집" 또는 내비 목적지명
  startLat, startLng, endLat, endLng,
  distanceKm: Double, durationMin: Int,
  weather: String?,            // 시작 시 1회 ("비"). 2일 데모는 fixture
  congestionMin: Int,          // 속도 < 20km/h 누적 분 (가정)
  summaryText: String?,        // F8 결과
  cardSeen: Boolean
)
Utterance(
  id, sessionId, at,
  text: String,                // STT 결과 원문. 음성 파일은 저장하지 않음
  lat, lng, placeLabel: String?,   // 장소 라벨 ("올림픽대로", "한강대교")
  odometerKm: Double, speedKmh: Int, congested: Boolean,
  llmStatus: PENDING | DONE | FAILED   // 시동 OFF로 끊긴 호출을 다음 시동 때 재처리
)
Item(
  id, utteranceId, order,
  text: String,                // 정리된 문장
  editedText: String?,         // 사용자가 고친 문장. 원문(text)은 유지
  type: THOUGHT | TODO | EMOTION | PERSON | RAW,
  emotion: DOWN | NEUTRAL | UP,   // 유형을 비감정으로 바꾸면 NEUTRAL로
  keywords: List<String>,
  deleted: Boolean
)
TrackPoint(sessionId, at, lat, lng, speedKmh)   // 경로·거리·정체 계산용. 5초 간격(가정)
Place(profileId, kind: HOME | WORK, lat, lng)   // F12. 2일 범위에선 비어 있어도 됨
```

파생 규칙

- 카드 메타 줄: `Utterance.at`, `placeLabel`, `congested`면 "정체 {Session.congestionMin}분 구간". 기획서 장표의 "출발 17분"은 `at - startedAt`.
- 속도·거리: 버추얼 콘솔에는 속도 조작이 없다(설명회는 기어·도어·배터리만). **FusedLocation 재생 경로의 `Location.speed`와 좌표 적분**으로 계산하고, Odometer 차이가 0이면 적분값을 쓴다.
- 감정 색(카드·지도 공통): DOWN `#E05A6A` / NEUTRAL `#E0A24A` / UP `#00AAD2`. 배경 `#0B1220`, 강조 `#00AAD2`, 텍스트 `#E8ECF2`.
- 핀 색: 한마디에서 항목이 1개면 그 색, 2개 이상이면 반반(대각 분할). 말풍선 메타에 "한마디 1 → 항목 N".

---

## 4. LLM 계약 (외부 API, Gleo AI LLM SDK 제공 시 교체)

### 4-1. 구조화 (F4) — 호출당 발화 1건

보내는 것: **발화 텍스트만.** 위치·시각·이름·감정 등 어떤 맥락도 보내지 않는다. 빈 문자열이면 호출하지 않는다.

```
system:
당신은 운전자가 운전 중에 떠오르는 대로 말한 한마디를 정리한다.
규칙:
1. 여러 생각이 섞여 있으면 항목으로 나눈다. 한 항목은 한 문장. 최대 5개.
2. 각 항목의 type은 thought(생각) / todo(할 일) / emotion(감정) / person(사람) 중 하나.
3. emotion은 down / neutral / up 중 하나. 할 일은 보통 neutral.
4. 말투는 다듬되 뜻을 바꾸지 않는다. 없는 내용을 추가하지 않는다.
5. 정리할 수 없으면 원문을 그대로 1개 항목(type=raw, emotion=neutral, keywords=[])으로 돌려준다.
JSON만 출력한다. 코드펜스·설명 금지.

user: "내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다"

assistant:
{"items":[
 {"text":"내일 거래처 서류 다시 보내기","type":"todo","emotion":"neutral","keywords":["거래처","서류"]},
 {"text":"노을이 예뻤다","type":"emotion","emotion":"up","keywords":["노을"]}
]}
```

실패 처리(전부 `Item(text=원문, type=RAW, emotion=NEUTRAL)` 1개 + `llmStatus=FAILED`): JSON 파싱 실패, 코드펜스나 JSON 외 텍스트 포함, `items` 빈 배열, 항목 6개 이상, 미지의 type/emotion, 빈 text, 타임아웃 8초(가정 — 타임아웃 시 요청을 **취소**해 늦게 온 응답이 중복 저장되지 않게), 오프라인.

### 4-2. 15초 요약 (F8) — 세션당 1회, 카드 표시 시

**2일 범위: LLM을 부르지 않는다.** 차 안에서 규칙으로 문장을 조립한다(프로토타입 `playSummary` 로직을 Kotlin으로 옮기면 됨).

```
항목 0개            → "오늘은 말이 없었네. 그래도 여기까지 왔어."
감정 2개 이상, 첫 감정 down·마지막 up → "힘들게 시작해서 {마지막 up 항목의 키워드}로 끝났어."
감정 2개 이상, 그 외 → "감정이 {down→가라앉았다 / neutral→그냥 / up→올라왔다}, … ."
감정 1개            → down "오늘은 좀 힘들었네." / 그 외 "오늘은 괜찮았네."
감정 0개            → "생각이 좀 있었네."
+ 할 일 n개          → " 할 일 {하나/둘/셋} 있어."
```

40자 상한, TTS 15초 타이머에서 stop. 건너뛰기 버튼은 재생 중 항상 표시.

P1에서 LLM 요약으로 바꾸더라도 **보내는 것은 항목 텍스트 목록뿐** — 감정·유형 라벨, 구간, 시간, 좌표는 보내지 않는다(원칙 4).

---

## 5. 화면 (4장)

해상도 (11/8 에뮬레이터 실측으로 확정)

- 디스플레이는 2560×1440이지만 **앱이 쓰는 영역은 오른쪽 Application window 약 1682×1210**(x 853–2535, y 90–1300)이다. 왼쪽 드라이빙뷰·상단 상태바·하단 GNB(공조)는 시스템 몫. 밀도 160이라 **1dp = 1px**, 설계는 1682×1210dp 가로 기준.
- 테마는 `NoActionBar` 계열. 기본 `Theme.DeviceDefault`는 상단에 약 75dp 타이틀 바를 넣는다.
- Pleos 레이아웃 가이드: Application window에는 앱이 1개 또는 **2개(분할)** 뜬다. 그래서 가로·세로 두 레이아웃에 모두 맞게 설계해야 한다. 2일 데모는 단독 가로(1682×1210)만 찍는다. 분할 폭은 P1에서 실측(확인).
- 카드 시안 `card_mock.png`(3840×1440, 2.67:1)는 비율이 다르다(앱 영역 1.39:1). 구성 요소는 그대로 두고 다음처럼 다시 배치한다.
  - 가로(기본): 좌 경로+핀 **약 40%(≈670dp)**, 우 패널 **약 60%(≈1010dp)**. 시안 왼쪽 위에 있던 제목 블록("오늘의 나 · 날짜 / 퇴근길 51분 / 메타 줄")은 우측 패널 맨 위로 옮긴다. 경로 영역이 좁아지기 때문이다. 그 아래로 통계 4 → 감정 흐름 바 → 항목 목록(스크롤) → 하단 고정 버튼 줄(15초 요약 듣기 / 수정 / 폰에서 열기)을 둔다.
  - 세로(분할 대비): 위 경로 약 35%, 아래 우측 패널 내용을 같은 순서로.
  - 글자는 Pleos 디자인 가이드 타이포 스케일을 따른다: Headline 60/56/48, Title 40/36/32, Body 30/28/26, Label 24/20(sp). 카드 적용값은 제목 48, 통계 40, 항목 28, 메타 26·20. 대비 4.5:1 이상, 문단 3줄 이하. 바깥 여백 40dp. 터치 타깃은 가이드에 수치가 없어 76dp 하한·80dp 사용(가정 — Design Resources Figma에서 확인). 시안의 16sp는 3840폭 이미지 기준이라 실제 크기와 다르다.
- S0 대형 버튼(지름 ≥120dp)은 앱 영역 하단 좌/우 모서리에서 32dp 안쪽.

| 화면 | 언제 | 구성 | 주행 중 |
|---|---|---|---|
| **S0 주행** | 시동 ON ~ 기어 P | 좌/우 하단 대형 버튼 1개("말하기", 지름 ≥ 120dp(가정)). 상단에 고정 상태 텍스트("퇴근길 · 기록 중"). 배경은 단색 — 내비는 별도 앱이라 지도를 그리지 않는다 | ○ 버튼 1탭만 |
| **S1 카드** | 기어 P + 도착 | `card_mock.png` 기준. 좌: 경로 + 핀(발화 지점, §3 핀 색 규칙) + 말풍선(원문·시각·장소). 우: 통계 4(주행 시간·거리·한마디·항목), 감정 흐름 바, 요약 자막 + 건너뛰기(재생 중만), 항목 목록(색 점·정리 문장·유형 태그·메타 줄·원문). 하단 "15초 요약 듣기 / 수정 / 폰에서 열기(QR)". 상단 우측 "이번 주 →"와 "설정" 링크 | × |
| **S2 나의 지도** | 정차 중 | 최근 7일 세션 목록(날짜·구간·시간·항목 수·감정 흐름 바) + 선택 세션 미니 경로(P2) | × |
| **S3 설정** | 정차 중 | 집·회사 등록(현재 위치로), 버튼 위치 좌/우, 전체 삭제(2단계 확인). 그 외 항목 없음 | × |

디자인 톤: 어두운 배경(야간 모드 기준), 글자 최소 16sp, 한글 단어 중간 줄바꿈 금지(`word-break: keep-all`), 폰트 Noto Sans KR.

---

## 6. 구간 판단 규칙 (F6)

1. Place에 집·회사가 있으면: 목적지(NaviHelper) 또는 도착 위치가 집 반경 300m(가정) → 퇴근(COMMUTE_OUT), 회사 → 출근(COMMUTE_IN), 그 외 → 외출.
2. 없으면: 내비 목적지명이 "집"/"회사"면 그대로, 아니면 출발 시각 06~10시 → 출근, 16~21시 → 퇴근, 나머지 → 외출(가정). 도착 위치로 사후 보정.
3. 집·회사 등록 안내는 **최초 실행 시 정차 중 1회**. 주행 중 확인 질문은 하지 않는다.

---

## 7. SDK / API 매핑, 퍼미션, 미확정 항목

| 기능 | Pleos SDK · API | 상태 |
|---|---|---|
| 주행 중 실행 | AAOS `distractionOptimized` meta-data + `CarUxRestrictionsManager` | Pleos 주행 제한 정책: DO 미선언 앱은 정차에서만 실행 |
| 기어 P 감지 | Vehicle SDK 주행 정보(기어) | **확인됨(11/8)**: `vehicle.getGear().registerGearSelection(GearSelectionListener)` → `onSelected(VehicleGear?)`, 현재값 `getCurrentGearSelectionState`. 값 `GEAR_PARK/GEAR_DRIVE/...`. 퍼미션 `pleos.car.permission.CAR_DRIVE`. 에뮬레이터 초기값 GEAR_PARK. 기어 변경은 Virtual Console로만(adb VHAL 주입은 user 빌드라 막힘). 버추얼 콘솔 기어 변경 지원(설명회 34:47). 모델별로 제공 범위가 달라 capabilities 확인 API로 체크. **CarPropertyManager 직접 접근은 서드파티 퍼미션상 불가 — 폴백 아님** |
| 주행 거리 | Vehicle SDK Odometer | **확인됨(11/8)**: `getOdometer().getCurrentDistanceDriven` → 에뮬레이터에서 **0.0**. `getTripCurrentDrivingDistance`는 `ERROR_NOT_SUPPORTED_PROPERTY`. → 2일 데모는 **TrackPoint 적분이 기본값**. 실차는 `check*Capabilities`로 분기 |
| 목적지·도착 | NaviHelper | **확인됨(11/8)**: `NaviHelper` 3.0.5, 퍼미션 `pleos.car.permission.NAVI_ROUTE`. 조회는 `getDestinationInfo()` → `onDestinationInfo(DestinationInfo)` 콜백(경로 없으면 빈 값). **도착 이벤트 `onDestinationArrived(DestinationArrivedInfo)`가 API에 있다.** 에뮬레이터에서 실제로 오는지는 경로 재생으로 확인(확인). 폴백: FusedLocation proximity alert(반경 300m) |
| 위치·속도 | FusedLocation | 확정(38:28). 에뮬레이터는 GPX 경로 재생 |
| 음성 입출력 | SpeechToText SDK, TextToSpeech SDK | **확인됨(11/8)**: 3.3.1. 퍼미션 `pleos.car.permission.STT_SERVICE`, `TTS_SERVICE`(**자동 merge 안 됨, 직접 선언**) + `RECORD_AUDIO`. 모드 `ON_DEVICE / SERVER / HYBRID`. **SERVER·HYBRID는 Pleos Playground My Project의 Client ID/Secret으로 `registerApp()` 필요**(없으면 TTS는 on-device로 폴백, STT는 오류). 11/8 STT `registerApp` 연결 성공. `sendAudio`는 ON_DEVICE 전용이라 SERVER 정확도는 실제 마이크로만 잴 수 있다. 로케일은 `Locale.KOREA`(ko_KR) — `Locale.KOREAN`(ko)은 `unsupported locale`. TTS: `initialize(listener)` **성공 후에** `addEventListener`(앞서 부르면 크래시). on-device TTS "적어뒀어" 재생 확인(화자 seoyoon). STT: on-device 한국어 인식 동작 확인. 합성 음성 입력 기준 정확도 낮음("거래처 서류"→"까지 스타 스타일") → 실제 음성과 SERVER 모드 정확도 비교 필요(확인). 종료는 SDK EPD(`onEndedRecognition`) 또는 `stop()` |
| 구조화 | 외부 LLM API **또는 Pleos LLM SDK** | 외부 API·퍼블릭 네트워크 허용(37:56). **11/8 발견: `ai.pleos.playground:LLM:2.1.3.1`이 이미 저장소에 있다**(퍼미션 `pleos.car.permission.LLM_SERVICE`, `generateContent(prompt, ResultListener)`). ON_DEVICE는 에뮬레이터에서 비활성("on-device LLM is disabled"). 11/8 Client ID/Secret으로 `registerApp` 성공(토큰 발급). 하지만 `generateContent`는 짧은 문장도 **HTTP 400** — 게이트웨이 응답 `e:cloud:gateway:00401 "Unsupported endpoint or you do not have permission"`. **원인(확인됨): 프로젝트 클라이언트 토큰에 LLM 엔드포인트(`/bsm/v1/runbsm`) 권한이 없다** → Playground에서 LLM 사용 신청·권한 요청 필요(공식 SDK 목록과 사용 신청 문서에는 LLM이 없다). 또 caas 서비스가 요청에 `metadata.contextMap`(위치·목적지 좌표, deviceId 등)을 붙인다. 지금 값은 서비스에 하드코딩된 템플릿 상수로 보이고 실제 차량 위치는 아니다(추정). 앱에서 끌 수 없고 서비스 버전에 따라 실제 값이 들어갈 수 있다. 원칙 4(LLM에는 발화 텍스트만)와 충돌하므로, 원칙을 지키려면 **외부 LLM API가 기본**이다(§10-1) |
| 날씨·장소 라벨 | 날씨 API, 역지오코딩 | 2일 데모는 **fixture**(날씨 "비", 좌표→"올림픽대로"/"한강대교" 표). Android Geocoder는 GMS 없는 이미지에서 동작하지 않을 수 있음(확인). README에 fixture임을 명시 |
| 입력 트리거 | 화면 대형 버튼 1탭 | 기본 경로로 확정 |
| 휠 음성 버튼 | — | 확인 중. 허용되면 F14 |
| 카드 자동 표시 | — | 백그라운드 앱이 내비 위로 뜨는 것은 Android 10+ 제한으로 서드파티에선 사실상 불가. **2일 데모는 앱이 포그라운드인 상태로 찍는다.** 분할 화면 등 Pleos 레이아웃 정책은 §10 |

매니페스트·런타임 퍼미션 체크리스트(11/8 확인): `INTERNET`, `RECORD_AUDIO`(런타임 요청 필요. 에뮬레이터 앱은 user 10), `ACCESS_FINE_LOCATION`, `pleos.car.permission.CAR_DRIVE`(기어), `CAR_INFO`(주행거리, 확인), `NAVI_ROUTE`, `STT_SERVICE`, `TTS_SERVICE`, (`LLM_SERVICE`). `pleos.car.*`는 선언만으로 설치 시 부여된다. 런타임 퍼미션 요청은 **최초 실행 정차 중**에만.

개발 환경: Android API 28+(레포: minSdk 28 / target 34), Gradle 8.0+(레포: Gradle 9.8.2·AGP 9.4.1, 문제없음), Kotlin. 에뮬레이터 설정 — `document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator`. 주행 제한 정책·주의분산 가이드·API 호환성 페이지는 `document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/` 아래.

---

## 8. 에뮬레이터 시연 절차 (S1, 영상 대본 겸용)

| 순서 | 버추얼 콘솔 조작 | 앱이 하는 일 | 화면에서 보이는 것 |
|---|---|---|---|
| 1 | 시동 ON, 내비 목적지 "집", GPX 경로 재생 시작, 기어 D | 세션 생성, 구간 = 퇴근 | S0. "퇴근길 · 기록 중". **기어 D에서도 앱이 떠 있어야 함(F0)** |
| 2 | (18:57 지점) 재생 속도 12km/h 구간 | 정체 플래그 ON | 변화 없음 |
| 3 | 버튼 1탭 → 마이크에 "오늘 팀장 때문에 진짜 힘들었다" | STT → 저장 → TTS "적어뒀어" → LLM 백그라운드 | 버튼 "듣는 중" → 원래대로. **그 외 화면 변화 없음** |
| 4 | (19:08 지점) 버튼 1탭 → "내일 거래처 서류 다시 보내야 해, 아 노을 예쁘다" | 위와 동일. 항목 2개로 분리 | 동일 |
| 5 | 목적지 도착(이벤트 또는 반경), 기어 P | 세션 종료값 계산 → 카드 | S1 카드: 51분 · 23km · 2 한마디 · 3 항목, 흐름 ↓→↑ |
| 6 | — | 요약 "힘들게 시작해서 노을로 끝났어. 할 일 하나 있어." TTS | 자막 + 건너뛰기 |
| 7 | (P1) 항목 2 탭 → 유형 변경 후 되돌림 | Item 수정, 원문 유지 | 즉시 반영 |
| 8 | (P1) "폰에서 열기" | QR(평문) | 실제 폰으로 스캔해 보여주면 좋음 |
| 9 | (P1) "이번 주" | 주간 목록 | S2 |
| 10 | 시동 OFF | 저장 완료 | — |

영상은 1~2분. 1·3·4·5·6이 핵심이라 한 테이크로. 7~9는 있으면 붙인다. GPX 경로 파일(회사→집, 올림픽대로·한강대교 경유, 정체 구간 포함)은 태연이 준비.

---

## 9. 개발 순서 — 2일

목표는 "S1이 에뮬레이터에서 끝까지 돈다"뿐이다. **SDK가 막혀도 파이프라인은 먼저 뚫는다**: 1일차는 디버그용 텍스트 입력으로 end-to-end를 만들고, SDK 스파이크 3개를 병행한다.

| | 할 일 | 완료 기준 |
|---|---|---|
| **1일차 오전** | GitHub private repo 생성(매일 커밋). 프로젝트(API 28+, Kotlin), Pleos SDK 의존성, 에뮬레이터·버추얼 콘솔 구동. **F0 DO 선언.** S0 화면(버튼 1개 + 고정 상태 텍스트). 퍼미션 체크리스트(§7) | **기어 D에서 S0가 보인다.** 버튼이 눌린다 |
| **1일차 오후** | 디버그 텍스트 입력 → Room(Utterance+맥락 스냅샷) → TTS "적어뒀어" → LLM 구조화(§4-1) + raw 폴백 → Item 저장. 병행 스파이크 ①: Vehicle SDK로 기어 P/D 콜백이 오는가 | 텍스트 넣으면 "적어뒀어"가 나오고 두 생각 섞인 문장이 Item 2개로 저장된다 |
| **1일차 밤** | 스파이크 ②: STT SDK가 호스트 마이크로 한국어를 인식하는가 → 버튼에 연결. 스파이크 ③: NaviHelper가 에뮬레이터에서 목적지·도착을 주는가 → 안 주면 반경/N초 판정으로 확정. 구간 판단 §6-2 | 실제 말하면 저장된다. 도착 판정 방식이 정해진다 |
| **2일차 오전** | 기어 P + 도착 판정 → 세션 종료값 → 카드 S1(통계 4, 흐름 바, 항목 목록 먼저. 경로+핀은 마지막) | 시연 절차 1~5가 돈다 |
| **2일차 오후** | 로컬 요약(§4-2) + TTS + 건너뛰기. 시연 절차 1~6을 한 테이크로 **영상 녹화**(보험용 1차본) | 영상 1~2분 완성 |
| **2일차 밤** | README(앱 이름·한 줄 소개·실행 방법·테스트 환경·fixture 안내·APK 위치), APK 빌드, 심사 계정 Collaborator 초대, 접근 확인 | 링크 3개를 태연에게 전달 |
| 남는 날 | 경로+핀 다듬기, F9 편집, F10 QR, F11 주간 목록, F12 설정 → 영상 재촬영 | — |

1일차 정오까지 F0(기어 D에서 앱 표시)이나 스파이크 ①이 안 되면 태연에게 바로 알린다 — 문서·Q&A로 풀어야 하는 문제다. 영상은 반드시 실제 STT로 찍는다.

2일 범위에서 뺀 것: 날씨 API·역지오코딩(fixture), LLM 요약(로컬 규칙), 집·회사 등록 화면, 항목 편집, 오디오 포커스, 미열람 카드 재표시.

---

## 10. 정해야 할 것 (태연 ↔ 욱창)

1. LLM 공급자와 키 관리(앱에 키를 넣지 않고 중계할지, 1차는 BuildConfig로 둘지).
2. 날씨·역지오코딩 API 선택 — P1 시점에.
3. S0가 내비와 어떻게 공존하는가 — Pleos 레이아웃 정책(분할 화면 여부)을 문서에서 확인해 S0 배치 결정(확인). 2일 데모는 앱 포그라운드 전제.
4. GPX 경로 파일과 fixture 표(좌표→라벨) — 태연이 1일차 오전까지.
