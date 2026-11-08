# Pleory 인수인계 (2026-11-09)

이번 묶음(v0.2)에서 한 일과 남은 일. 저장소는 https://github.com/wook95/pleory (비공개), 기본 브랜치 `main`, 작업 브랜치 `pleory/p0`. 두 브랜치는 같은 커밋을 가리킨다.

## 한눈에

| 항목 | 상태 | 위치 |
|---|---|---|
| 유저 스토리 12개 | 완료 | `docs/pleory/user-stories.md` |
| 지난 기록 S2 (F11) | 완료 | `ui/S2HistoryView.kt`, `core/History.kt` |
| 도착 리마인드 (F15, 신규) | 완료, 태연 확인 대기 | `core/ArrivalReminder.kt`, `SessionController` |
| 폰에서 열기 QR (F10) | 완료 | `core/QrPayload.kt`, `ui/QrView.kt` |
| 데모 파이프라인 v2 | 완료 | `tools/run-demo.sh v2 reset|day1|day2` |
| 시연 영상 v2 (1:51, US 12개 전부) | 완료 | Release v0.2, `video/` (Remotion) |
| 코드 리뷰·정리 | 완료, 막는 문제 없음 | 아래 "검증" |
| README 제출 요건 4가지 | 완료 | `README.md` |
| Release v0.2 (영상 + 내부용 debug APK) | 완료 | https://github.com/wook95/pleory/releases/tag/v0.2 |
| 태연에게 진행 메일 | 보냄 (11/9 00:14, uclee95@gmail.com) | |
| 제출용 release APK | 안 함 (전용 키·팀명 필요) | 아래 "제출 절차" |

## 기능 요약

- S2 지난 기록: 정차 중 S0·S1 의 "지난 기록"으로 들어간다. 최근 7일(오늘 0시 기준 6일 전 0시부터) 끝난 주행, 할 일만 보기, 행을 누르면 그날 카드(요약 자동 재생 없음). 주행으로 바뀌면 닫힌다.
- F15 도착 리마인드: 목적지 도착으로 열린 카드에서만(P 5초 대체 판정 제외), 화면 게이트가 열려 있을 때, 이전 세션 7일 안의 끝내지·지우지·알리지 않은 할 일을 최근 것부터 골라 요약 앞에 한 줄로 말한다. 한 번 말하면 `remindedAt`. 카드 맨 위 띠의 "했어"가 `Item.done`. Room v1→v2 마이그레이션 포함. 카드가 가려져 있으면 돌아올 때 한 번 말한다.
- F10 QR: 카드 평문(제목, 통계, 문장, 요약, 항목)을 서버 없이 QR 로. UTF-8 980바이트 상한, 오류 정정 M, 넘으면 항목을 뒤에서 빼고 "외 N개".
- 데모 v2: 에뮬레이터에서 이틀 장면을 그대로 재현한다. 조작은 디버그 broadcast(DemoReceiver, DUMP 권한)로 버튼을 접근성 이름으로 찾아 누른다. Gleo 는 실제 Gleo 를 거친다(입력만 ASR 테스트 broadcast, 문구는 "플레오리 앱에서, …").

## 검증

- 단위 테스트 247개 통과, `compileReleaseKotlin` 통과, `video/` `tsc --noEmit` 통과.
- 코드 리뷰(opus): 치명·높음 0. 중간 5건(요약이 S2 위에서 늦게 말함, DemoReceiver 노출, 리마인드 정렬·트리거 문서 불일치, 게이트 판정) 모두 수정.
- 에뮬레이터: S2 넓은·좁은 창, QR 스크린샷을 zxing-cpp 로 디코드해 카드 글과 일치, 데모 day1/day2 로그(`llm ok: claude` 2회, `item deleted`, `qr open/close`, `history todoOnly`, `gleo ADD_DRIVE_NOTE accepted`, `remind speak n=1`, `remind done`).
- 영상: 스틸 30장 확인(`apk/video/v2/stills/`), 구간별 음량 측정(대사 −18~−21, 앱 TTS −16~−18, Gleo 대답 −18.7 LUFS).

## 영상 다시 만들기

```bash
REC=1 tools/run-demo.sh v2 day1     # 녹화 + 로그 → apk/video/v2/day1/
REC=1 tools/run-demo.sh v2 day2
cd video && npm install && bash scripts/prepare.sh ../apk/video/v2 && npm run render   # → apk/video/pleory-film.mp4
```

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

- xoxo-112(태연) 에게 Collaborator 초대를 보냈다. 수락 전이면 저장소가 안 열린다.
- Release v0.2 의 debug APK 에는 개인 Claude·Gemini 키가 들어 있다. 저장소가 비공개라 두었지만 외부로 넘기지 않는다.
- 한마디 요약 문구 "생각이 좀 있었네. 할 일 하나 있어."(할 일 하나뿐인 2일차)는 기존 요약 규칙 그대로다. 어색하면 `core/SummaryComposer` 를 손본다.
- 이번 묶음의 커밋: `795992e`(영상 1차) ~ `ad68a0d`(README). `git log 7bfe3a0..main`.
