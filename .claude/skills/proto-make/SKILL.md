---
name: proto-make
description: Use when the user runs /proto-make in pleos-hello. 배경·문제 정의를 먼저 확정하고 plan.md 를 쓴 뒤, Kotlin 화면 시안을 여러 벌 발산해 에뮬레이터 스크린샷으로 비교하고 별표로 수렴한다. 확정안만 앱 본 코드로 승격한다.
---

# /proto-make (pleos-hello)

alpha-canvas-proto 의 `/proto-make` 를 이 레포용으로 줄인 판이다. 남긴 것은 **정의 먼저 게이트**와 **발산·별표 수렴** 두 가지.
Linear·팀 분기·AS-IS 인덱싱·카탈로그·push 는 없다. 산출물은 HTML 이 아니라 **에뮬레이터에서 돌아가는 Kotlin 화면**이다.

> **⭐⭐ 정의 먼저 (최우선 · 예외 없음)**
> 화면 코드를 한 줄이라도 쓰기 전에 ① **배경** ② **문제 정의** 를 써서 사용자 확인을 받는다(0단계).
> 둘 중 하나라도 없으면 4단계 이후로 넘어가지 않는다. 근거가 비면 **지어내지 말고 되묻는다**.

> **⭐ 발산 우선**
> 시안을 한 벌만 그리지 않는다. 시안 여러 벌 → 별표로 수렴(라운드 반복) → 확정안만 본 코드로 승격.
> 사용자가 "사소하니 발산 생략" 을 **명시**할 때만 건너뛰고, 그 사실을 plan.md 에 적는다.

## 사전 조건

- cwd 가 pleos-hello 레포 루트. 빌드·에뮬레이터 사실은 `CLAUDE.md` 가 정본이다 — 먼저 읽는다.
- `git status` 가 clean 이거나, 이 스킬이 만들 파일과 겹치지 않는다.
- 에뮬레이터 `Pleos_Connect_API_34` 가 떠 있다 (`adb shell getprop sys.boot_completed` == `1`). 꺼져 있으면 띄우고 부팅을 기다린다.

## 워크플로우

### 0. 정의 먼저 — 배경 · 문제 정의 (필수 게이트)

사용자 요청·대화·참고 자료를 근거로 초안을 **내가 먼저** 써서 보여 준다.

```markdown
## 배경
- 왜 지금 이 작업인가 — 계기와 출처
- 지금 차량(Pleos Connect)에서는 어떻게 되어 있나 (아는 범위 · 추정이면 「추정」)

## 문제 정의
- 누가: <운전자 / 동승자 / 정차 중 사용자 …>
- 어떤 상황에서: <주행 중·정차 중, 어느 화면에서>
- 무엇이 막히나: <지금 못 하는 것 · 드는 수고 · 잃는 것>
- 근거: <데이터·경험·인터뷰 · 없으면 「가설」>
```

**되묻는 경우** — 하나라도 걸리면 확정하지 말고 AskUserQuestion 으로 묻는다(질문 1~3개, 선택지에 내 추정안 포함).

- 문제가 아니라 **해결책을 문제로 적고 있다** ("문 상태 위젯이 없다" — 그래서 누가 무엇을 못 하나?)
- **대상 사용자**가 불분명하거나 섞여 있다. 차량 앱은 운전자와 동승자의 제약이 다르다
- **주행 중에 쓰는 화면인지**가 불분명하다 — 운전자 주의 분산 제약이 걸리므로 반드시 정한다
- 배경과 문제가 이어지지 않는다 / 차량 기본 앱에 이미 있는 기능일 수 있다

사용자가 확인하면(`y`) 그 문구를 그대로 plan.md 에 쓴다. 이후 바꾸면 plan.md 도 함께 고친다.

### 1. 범위 (In / Out Scope)

자유 서술 bullet 로 받는다. "(아직 없음)" 허용. Out of Scope 는 스코프 크리프 방지용이다.

### 2. User Stories & Acceptance Criteria

1개 이상 받는다.

```
US-1: <한 줄 제목>
  As <persona>, I want <goal>, so that <reason>
  AC:
    - Given ..., When ..., Then ...
```

받은 뒤 내가 검토해 제안한다 (반영 루프 무제한):
1. **분리** — US 하나에 사용자 목적이 둘 이상이면 분리 후보
2. **누락 시나리오** — 오류·비어 있음·권한 거부·SDK 조회 실패(`onFailed`)·차량 상태 변화(기어 P↔D) 등
3. **범위 정합** — US 의 Goal 이 Out of Scope 와 충돌하면 경고
4. **SDK 가능성** — 필요한 차량 데이터가 Vehicle SDK 에 있는지 문서(`/docs/pleos-only/connect-sdk-pleos/`)로 확인. 없으면 "가짜 데이터로만 프로토" 라고 표시

### 3. 슬러그 · 폴더

- 슬러그: `<YYYY-MM-DD>-<영문 kebab-case>`. Kotlin 패키지명은 날짜를 떼고 하이픈을 지운 형태(`doorstatus`).
- 기록 폴더 `prototypes/<slug>/` — 이미 있으면 `-v2`.
- 코드 위치는 debug 전용: `app/src/debug/java/ai/pleos/hello/proto/<pkg>/`. 시안이 release 빌드에 섞이지 않는다.

### 4. plan.md 작성

`prototypes/<slug>/plan.md`:

1. 배경 (0단계 확정본 그대로)
2. 문제 정의 (0단계 확정본 그대로)
3. 범위 In / Out (1단계)
4. User Stories & AC (2단계 + 검토 반영)
5. 사용할 차량 데이터 — SDK API·필요 권한 / 가짜 데이터 여부
6. 핵심 사용자 가치
7. 인터랙션 흐름
8. 디자인 결정 사항
9. 미정 / 논의 필요

보여 주고 "이대로 진행할까요? (y/edit)". `edit` 은 무제한.

### 5. 발산 — 시안 여러 벌 (⭐ 필수)

**5-0. 하네스 (처음 한 번만)** — `app/src/debug/java/ai/pleos/hello/proto/ProtoActivity.kt` 가 없으면 만든다.

```kotlin
package ai.pleos.hello.proto

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView

/** 시안 레지스트리. 키 = "<pkg>/r<N>/<letter>" */
object Protos {
    val all: MutableMap<String, (Activity) -> View> = linkedMapOf()
}

class ProtoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProtoIndex.register()
        val id = intent.getStringExtra("id")
        val build = Protos.all[id]
        setContentView(build?.invoke(this) ?: TextView(this).apply {
            textSize = 24f
            text = "없는 시안: $id\n\n" + Protos.all.keys.joinToString("\n")
        })
    }
}
```

`app/src/debug/java/ai/pleos/hello/proto/ProtoIndex.kt` 에 시안 등록을 모은다 (`object ProtoIndex { fun register() { ... } }`).
`app/src/debug/AndroidManifest.xml` 에 `ProtoActivity` 를 `android:exported="true"` 로 등록한다(adb 로 띄우기 위해 · debug 매니페스트라 release 에는 없다).

**5-1. 발산 대상** — 화면 전체가 아니라 "결정이 필요한 부분" 단위(정보 배치·상태 표현·조작 방식 등). 대상이 여럿이면 각각 따로 돌린다.

**5-2. Round 1** — 구조적으로 서로 다른 컨셉 **4~6안**. 색만 바꾼 변형은 한 안으로 친다.
- 파일 `proto/<pkg>/R1.kt` 에 안마다 `fun r1a(a: Activity): View` 처럼 함수 하나. 앱과 같은 방식으로 **기본 View 코드**(의존성 추가 없음)로 그린다. Compose 가 필요해 보이면 의존성 추가 전에 묻는다.
- 차량 데이터는 plan.md 5번을 따른다 — 실데이터면 `Vehicle` 을 쓰고 `onFailed` 화면도 보이게, 가짜면 상수로.
- 화면은 2560×1440 IVI 전체가 아니라 오른쪽 앱 영역 **약 1682×1210** (x 853–2535, y 90–1300 · 실측)에 뜬다. 왼쪽 차량 패널과 하단 공조 바는 시스템 몫이다. 주행 중 화면이면 큰 글자·적은 정보·큰 터치 영역으로.

**5-3. 렌더 · 캡처**

```bash
./gradlew installDebug
adb shell am start -W -S -n com.pplab.pleory/ai.pleos.hello.proto.ProtoActivity --es id <pkg>/r1/a
sleep 1
adb exec-out screencap -p > prototypes/<slug>/rounds/r1/a.png
```

`-W` 없이 고정 sleep 만 두면 실행이 3초 넘게 걸릴 때 그리기 전 빈 화면이 찍힌다(실측 TotalTime 3069ms).

캡처한 이미지를 Read 로 직접 확인한다 — "없는 시안" 화면이나 크래시면 그 안은 다시 만든다(`adb logcat -s AndroidRuntime:E`).

**5-4. 보여 주기** — 안마다 스크린샷 + 한 줄 컨셉을 보여 주고 별표를 받는다.

**5-5. 수렴 루프 — 라운드는 사용자가 요청할 때만 늘어난다 (⛔ 필수)**
- 별표를 받으면 `prototypes/<slug>/rounds/r<N>/round.md` 에 기록하고 **사유 초안을 쓴 뒤 멈춘다**. 확정 / 라운드 더 / 수정 중 무엇을 할지 묻는다.
- **내가 먼저 다음 라운드를 만들지 않는다.** "라운드 더" 를 들으면 그때 별표 안을 축으로 다시 발산하고, 묻지 않고 끝까지 만든다. R1 에서 바로 확정해도 된다.
- **결정 기록 (필수)** — `round.md`:
  - `advanceReason`: 왜 이 방향인가
  - 살아남은 안마다 `verdict` / `why`. 탈락 사유는 받지 않는다
  - 사유마다 출처 `source: user | claude-draft`
- 확정되면 `prototypes/<slug>/decision.md` 에 확정안 id·사유·라운드 요약.

> **생략**: 사용자가 발산 생략을 명시하면 5단계를 건너뛰고 plan.md 에 `발산 생략: <사유>` 를 남긴다.

### 6. 확정안 승격

- 확정안 코드를 `app/src/main/java/ai/pleos/hello/` 의 실제 화면(새 Activity 또는 기존 화면 교체)으로 옮긴다. 필요한 권한은 `app/src/main/AndroidManifest.xml` 에 선언.
- 탈락 안 코드(`proto/<pkg>/`)와 `ProtoIndex` 등록은 지운다. 스크린샷·round.md 는 `prototypes/<slug>/` 에 히스토리로 남는다.
- `./gradlew assembleDebug` 성공 + 에뮬레이터에서 승격된 화면을 띄워 캡처, 확정 스크린샷과 같은지 확인.

### 7. 확인 루프

- `confirm` → 끝. 커밋은 사용자가 요청할 때만 한다.
- `수정: <자유 서술>` → 코드 또는 plan.md 패치 후 다시 빌드·캡처해 보여 준다. 매 수정마다 confirm 을 다시 묻지 않는다.
- `폐기` → `prototypes/<slug>/` 와 `proto/<pkg>/` 를 지우고 종료.
- `중단` → 그대로 두고 종료 (재개 가능).

## 산출물

- `prototypes/<slug>/plan.md`, `rounds/r<N>/*.png` + `round.md`, `decision.md`
- 승격된 화면 코드 (`app/src/main/...`)

## 안 함

- 배경·문제 정의 없이 화면 코드 착수
- 사용자 요청 없이 다음 라운드 생성
- 커밋·push (요청 시에만)
- release 소스셋에 시안 코드 두기
- 의존성 추가 (Compose 등) — 먼저 묻는다

## 에러 처리

- 빌드 실패 → 오류를 고치고 다시 빌드. 같은 오류 2회면 멈추고 보고한다.
- 에뮬레이터 없음 / `adb devices` 비어 있음 → `emulator -avd Pleos_Connect_API_34` 로 띄우고 부팅 대기. 실패하면 보고.
- SDK 조회 실패 → 그 화면을 실패 상태로 보여 주는 것도 시안의 일부다. 원인은 `onFailed` 예외 메시지로 보고.
