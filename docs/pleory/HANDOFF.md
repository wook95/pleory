# Pleory 인수인계 (2026-11-09, v0.3)

이번 묶음(v0.3)은 태연 피드백(카드가 교통·날씨까지 복잡하다, "적어뒀어" 반복 대신 긴 혼잣말을 정리하는 모습)을 반영했다. 저장소는 https://github.com/wook95/pleory (비공개), 기본 브랜치 `main`, 작업 브랜치 `pleory/p0`. 두 브랜치는 같은 커밋을 가리킨다.

## 한눈에

| 항목 | 상태 | 위치 |
|---|---|---|
| 유저 스토리 13개(US-13 묶음 상세 추가) | 완료 | `docs/pleory/user-stories.md` |
| 카드 v3: 감정 / 일 / 아이디어 대표 문장 → 눌러서 상세 | 완료 | `ui/S1CardView.kt`, `core/CardModel.kt` |
| 대표 문장 LLM(Claude → Gemini → 규칙 문장) | 완료 | `core/CardDigest.kt`, `core/Digester.kt`, Room v2→v3 |
| 긴 혼잣말 정리(최대 8개 항목, STT 상한 45초) | 완료 | `core/StructureContract.kt` |
| 요약 TTS·S2 행·QR 을 묶음 기준으로 | 완료 | `core/SummaryComposer.kt`, `core/History.kt`, `core/QrPayload.kt` |
| 데모 파이프라인 v3 | 완료 | `tools/run-demo.sh v3 reset|day1|day2` |
| 시연 영상 v3 (1:55, 컷 28, US 13개 전부) | 완료 | Release v0.3, `video/`, 컷 표 `docs/pleory/video-script.md` |
| 코드 리뷰 2회 | APPROVE | 아래 "검증" |
| Release v0.3 (영상 + 내부용 debug APK) | 완료 | https://github.com/wook95/pleory/releases/tag/v0.3 |
| 제출용 release APK | 안 함 (전용 키·팀명 필요) | 아래 "제출 절차" |

## 기능 요약 (v0.3 에서 바뀐 것)

- 카드(S1) 첫 화면은 묶음 줄 셋(감정·일·아이디어)과 요약, 접힌 "이번 주행"뿐이다. 묶음 줄을 누르면 넓은 창은 오른쪽 판, 좁은 창은 한 단으로 상세(일은 체크 상자, 지우기)가 열린다. 날씨·정체는 "이번 주행"을 펼쳐야 보인다.
- 대표 문장: 카드가 뜰 때 세션의 남은 항목 전체로 LLM 이 묶음마다 한 줄을 만들고 세션에 저장한다. 오기 전·실패하면 규칙 문장(첫 항목 + "외 N개", 감정은 방향이 바뀌면 "첫 → 마지막"). 항목을 지우면 다시 만든다.
- 혼잣말 하나를 주제마다 나눈다(프롬프트 최대 8, 파서 10). LLM 예산: Claude 6.5초, Gemini 5초, 전체 12초(디버그 빌드만 `debug.pleory.llm_budget_ms` 로 늘림).
- 차량 서비스가 끊기면 주행 제한이 있는 것으로 본다(닫힌 쪽 실패). 다시 붙으면 다시 연결한다.

## 검증

- 단위 테스트 274개 통과, `compileReleaseKotlin` 통과, `video/` `tsc --noEmit` 통과.
- 코드 리뷰(opus) 2회: HIGH 1(지운 항목 글이 대표 문장에 남음)·MEDIUM·LOW 모두 고친 뒤 APPROVE.
- 데모 v3 녹화 로그: D1 `llm ok: claude`(항목 8), `digest ok: claude` 2회(카드, 지운 뒤), `item deleted`, `qr open bytes=655 items=7`, `history todoOnly`; D2 `gleo ADD_DRIVE_NOTE accepted`(실제 Gleo 경유), `remind speak n=2`, `remind done`.
- 영상: 스틸 확인, QR 정지 프레임을 zxing-cpp 로 디코드해 `video/src/qrText.json` 에 그대로 사용, 통합 음량 −21.9 LUFS(혼잣말 구간 −20~−23dB).

## 영상 다시 만들기

에뮬레이터는 창이 있는 상태로 띄운다(`-no-window` 에 `adb emu screenrecord` 를 쓰면 앱 TTS 가 나올 때 멈춘다). 호스트 부하가 높으면 녹화 중 "hanging thread" 로 에뮬레이터가 꺼지니, 꺼지면 다시 띄워 다시 찍는다.

```bash
REC=1 REC_SIZE=1920x1080 REC_FPS=20 LLM_BUDGET_MS=20000 tools/run-demo.sh v3 day1   # → apk/video/v3/day1/
REC=1 REC_SIZE=1920x1080 REC_FPS=20 LLM_BUDGET_MS=20000 tools/run-demo.sh v3 day2
cd video && npm install && bash scripts/prepare.sh ../apk/video/v3 && npm run render   # → apk/video/pleory-film.mp4
```

- LLM 결과(항목·대표 문장)는 매번 조금씩 다르다. 다시 찍으면 `video-script.md` 의 "v3 녹화에서 확인한 것"과 QR 글(`qrText.json`, 디코드 다시)을 맞춘다. 컷 시각은 로그에서 자동으로 잡힌다.
- 운전자 목소리: `tools/gen-vo.sh SAz9YHcvj6GT2YYXdXww` (ElevenLabs River, 키는 `local.properties` 의 `tts.elevenKey`). 무료 플랜이라 엔딩에 "Voice: ElevenLabs" 표기가 필요하다.
- BGM: Pixabay "Dearest (Calm Piano)" by noemazeek (Pixabay 라이선스, 표기 불필요, YouTube Content ID 등록곡). 효과음은 ElevenLabs 생성.
- 음원·녹화·폰트는 커밋하지 않는다(`apk/`, `video/public/*` gitignore).

## 제출 절차 (마감 2026-11-15 일)

공식 안내: https://appdevchallenge.auto-hmg.io/guide/submission

1. 기획서(접수양식 DOCX)를 appdevchallenge@hyundai-partners.com 으로 보낸다. 제목 `[차량용 앱 개발 공모전] 팀명_대표자명`, 팀원 전원 CC, 각자 동의서 서명.
2. 비공개 저장소이므로 "공지된 심사용 GitHub 계정"을 Collaborator 로 초대하고, 마감 전에 그 계정에서 열리는지 확인한다. 계정 아이디는 공지·FAQ·규정 어디에도 없었다. 운영국에 문의한다. 초대 명령: `gh api -X PUT repos/wook95/pleory/collaborators/<아이디> -f permission=pull` (개인 저장소라 실제로는 쓰기 권한이 붙는다는 공식 경고가 있다).
3. 제출용 APK: 저장소 `apk/팀명_Pleory_release.apk`, 실행 가능한 release 버전.
   - 공모전 전용 Claude 키를 새로 발급하고 지출 한도를 건다(심사 뒤 폐기). `local.properties` 의 `llm.claudeKey` 에 넣는다.
   - release 서명 키스토어를 만든다(키스토어와 비밀번호는 커밋하지 않는다).
   - `./gradlew assembleRelease` 후 파일을 `apk/` 에 두고, `.gitignore` 에서 그 파일만 예외로 연다(`!apk/*_release.apk`). 너무 크면 Release 에 올리고 그 링크를 제출한다.
4. README 의 APK 위치 문구를 실제 파일명으로 바꾼다.

## 사람이 정할 것

- 태연: F15 도착 리마인드 채택 여부, 19:08 "한강대교" 라벨용 좌표→장소 fixture 표.
- 문의 연락처(지금 `pleory.team@example.com` 임시)와 개인정보처리방침 URL.
- 팀명(APK 파일명·메일 제목).
- 실제 목소리·실차 확인(H1), 실제 목소리 영상(선택, 2차 PT 전).
- Pleos 문의: STT SERVER·LLM 권한, 실차 Gleo 스키마 승인.

## 알아둘 점

- 실차 한계(리뷰 MEDIUM, 제출 비차단): 내비 도착 뒤 경로가 지워지고 후진(R)·중립(N)을 거쳐 P 에 놓으면 도착 래치가 지워져 리마인드를 건너뛴다(`CardTrigger.kt:28`). 고치려면 래치를 D 에서만 지우고 "도착 → None → R → P" 테스트를 더한다. TRD D9 행과 §8-9 문구도 그때 맞춘다.

- xoxo-112(태연) 에게 Collaborator 초대를 보냈다. 수락 전이면 저장소가 안 열린다.
- Release v0.2 의 debug APK 에는 개인 Claude·Gemini 키가 들어 있다. 저장소가 비공개라 두었지만 외부로 넘기지 않는다.
- 한마디 요약 문구 "생각이 좀 있었네. 할 일 하나 있어."(감정 없이 할 일 하나뿐인 2일차)는 요약 규칙 그대로다. 어색하면 `core/SummaryComposer` 를 손본다.
- 대표 문장 LLM 이 프롬프트 규칙을 가끔 어긴다(v3 녹화: 지운 뒤 감정 문장이 마지막 감정 "우울"을 빠뜨림, 첫 감정 문장 36자로 32자 초과). 파서는 길이 48자·빈 문장만 거른다.
- Release v0.3 의 debug APK 에도 개인 키가 들어 있다. 외부로 넘기지 않는다.
- 설계 리뷰(v0.3, CLEAR) 뒤 남은 LOW 세 가지:
  - 대표 문장을 다시 부르지 않는 판단(`digestSignatures`)이 메모리에만 있어, 앱을 다시 켠 뒤 카드를 다시 열면 저장된 LLM 문장을 지우고 다시 부른다(`SessionController.kt` scheduleDigest). 항목 해시를 대표 문장과 함께 저장하면 된다.
  - `Car.createCar` 가 예외를 던지면 UX 제한이 열린 채로 남는다(`SdkHub.kt`). 화면 게이트가 기어 P 를 따로 요구해 주행 중 노출은 없다.
  - 차량 서비스 끊김 → 다시 붙음(`markDisconnected` → `attach`) 경로에 단위 테스트가 없다.
- v0.2 묶음의 커밋: `795992e`(영상 1차) ~ `ad68a0d`(README). v0.3 묶음: `10e9f94`(명세) 부터 `git log 10e9f94~1..main`.
