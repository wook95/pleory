#!/bin/zsh
# 데모 실행. v1(T8)은 앱 안의 시나리오를 무장하고 같은 배속으로 경로를 재생한다.
# v2 는 영상 대본(docs/pleory/video-script.md "파이프라인 요구사항")의 하루치 장면을 처음부터 끝까지 한 번에 재현한다.
#
# v1 사용: tools/run-demo.sh [배속=30] [pcm|text]
#   pcm  : u1.pcm·u2.pcm 을 on-device STT 에 넣는다 (먼저 아래 "PCM 준비" 필요)
#   text : STT 를 우회해 텍스트를 인식 결과로 넣는다 (구조화 이후 체인 검증용)
# PCM 준비 (한 번): 16kHz·16bit·mono PCM 두 개를 앱 filesDir 에 둔다.
#   adb push u1.pcm /data/local/tmp/u1.pcm; adb push u2.pcm /data/local/tmp/u2.pcm
#   adb shell run-as com.pplab.pleory --user 10 cp /data/local/tmp/u1.pcm files/u1.pcm   (u2 도 동일)
#
# v2 사용 (텍스트 모드만):
#   tools/run-demo.sh v2 reset           앱 데이터 초기화(동의 화면이 다시 뜬다) + 권한 + 영상 시드 5건. day1 이 먼저 알아서 한다.
#   tools/run-demo.sh v2 day1 [배속=30]  초기화 → 동의 탭 → 퇴근길 18:40, 한마디 1~3, 기어 P → 카드·요약 → 지우기 → QR → 지난 기록·할 일만 → 시동 OFF
#   tools/run-demo.sh v2 day2 [배속=30]  같은 DB 로 다음 날 출근길 08:10(거꾸로 된 경로, 목적지 "회사") → Gleo 호출 → 기어 P → 리마인드 → 했어
#     day2 는 다시 돌려도 된다(앱이 2일차 세션을 지우고 1일차 할 일의 했어·알림 기록을 되돌린다).
#   환경 변수: REC=1 이면 `adb emu screenrecord` 로 <OUT>/raw.webm(video/scripts/prepare.sh 입력) 을 녹화한다.
#              OUT=<디렉터리> (기본 apk/video/v2/day1·day2): flow.log(logcat), rec_start.txt(기기 시각), raw.webm
#              SHOTS=<디렉터리> 이면 주요 장면 스크린샷(PNG)을 남긴다(녹화와 별개, 확인용).
#              GLEO=b 이면 Gleo 호출 A(실제 Gleo 에 테스트 ASR)를 건너뛰고 B(ADD_DRIVE_NOTE 직접)를 쓴다.
#   탭은 앱의 디버그 receiver 가 버튼을 접근성 이름으로 찾아 누르고 `PleoryDemo tap <이름> x= y=`(화면 좌표)를 남긴다.
#   좌표 상수가 없어서 창 크기(1682×1210 등)가 바뀌어도 같은 버튼을 누른다.
# 로그: adb logcat -s 'PleoryFlow:*' 'PleoryDemo:*'
set -eu
ADB="${ADB:-adb}"
HERE="${0:A:h}"
ROOT="${HERE:h}"
PKG=com.pplab.pleory
RECV="$PKG/ai.pleos.hello.debugpanel.DemoReceiver"

if [[ "${1:-}" != v2 ]]; then
  SPEED="${1:-30}"
  MODE="${2:-pcm}"
  # 이전 실행이 남긴 마지막 위치(에뮬레이터가 계속 반복 송출)를 출발점에서 먼 곳으로 치워 둔다.
  "$ADB" emu geo fix 126.9 37.4 >/dev/null
  "$ADB" shell am start -W --user 10 -f 0x18000000 -n $PKG/ai.pleos.hello.debugpanel.DebugPanelActivity \
    --es action demo --es mode "$MODE" --ef speed "$SPEED" >/dev/null
  ADB="$ADB" "$HERE/play-route.sh" "$SPEED"
  exit 0
fi

STEP="${2:?v2 reset|day1|day2}"
SPEED="${3:-30}"
OUT="${OUT:-$ROOT/apk/video/v2/$STEP}"
LOG="$OUT/flow.log"
T0=$SECONDS

if pgrep -f play-route.sh >/dev/null; then echo "다른 경로 재생이 돌고 있다(겹치면 거리가 두 배). 끝난 뒤 다시."; exit 1; fi

say() { print -r -- "[$(printf '%5.1f' $((SECONDS - T0)))s] $*"; }
demo() { "$ADB" shell am broadcast --user 10 -n "$RECV" "$@" >/dev/null; }
tap() { demo --es cmd tap --es target "$@"; }
# 탭 표지를 기다린다. 버튼을 못 찾았으면(`tap <이름> missing`) 바로 멈춘다.
wait_tap() {
  local start=$SECONDS
  until grep -q -E -- "tap $1 x=" "$LOG"; do
    grep -q -- "tap $1 missing" "$LOG" && { say "버튼 없음: $1"; exit 1; }
    (( SECONDS - start > 10 )) && { say "시간 초과: tap $1"; exit 1; }
    sleep 0.2
  done
  say "ok  $(grep -E -- "tap $1 x=" "$LOG" | tail -1 | sed -E 's/^.*\): //')"
}

# 로그에 정규식이 [n]번째로 나올 때까지 기다린다. wait_log <정규식> [n=1] [초=60]
wait_log() {
  local re="$1" n="${2:-1}" limit="${3:-60}" start=$SECONDS
  while (( $(grep -c -E -- "$re" "$LOG" 2>/dev/null || true) < n )); do
    (( SECONDS - start > limit )) && { say "시간 초과: $re (${n}번째)"; exit 1; }
    sleep 0.2
  done
  say "ok  $(grep -E -- "$re" "$LOG" | sed -n "${n}p" | sed -E 's/^.*\): //')"
}

reset_app() {
  "$ADB" shell am force-stop --user 10 $PKG
  "$ADB" shell pm clear --user 10 $PKG >/dev/null
  # pm grant 는 예외를 내도 부여된다(CLAUDE.md "권한")
  for p in pleos.car.permission.FUSED_LOCATION android.permission.ACCESS_FINE_LOCATION android.permission.ACCESS_COARSE_LOCATION android.permission.RECORD_AUDIO; do
    "$ADB" shell pm grant --user 10 $PKG $p >/dev/null 2>&1 || true
  done
  "$ADB" shell am start -W --user 10 -n $PKG/ai.pleos.hello.debugpanel.DebugPanelActivity --es action seed-history --es set film >/dev/null
}

start_log() {
  mkdir -p "$OUT"
  "$ADB" logcat -c
  "$ADB" logcat -v time 'PleoryFlow:I' 'PleoryDemo:I' '*:S' > "$LOG" &
  LOGCAT_PID=$!
}
# zsh 는 함수 안에서 건 EXIT trap 을 함수가 끝날 때 돌리므로 맨 위에서 건다
LOGCAT_PID= ROUTE_PID=
trap '[[ -n "$LOGCAT_PID" ]] && kill $LOGCAT_PID 2>/dev/null; [[ -n "$ROUTE_PID" ]] && kill $ROUTE_PID 2>/dev/null; true' EXIT

start_rec() {
  "$ADB" shell date +%H:%M:%S.%N | cut -c1-12 > "$OUT/rec_start.txt"
  if [[ "${REC:-}" == 1 ]]; then
    rm -f "$OUT/raw.webm"
    "$ADB" emu screenrecord start --time-limit 180 "$OUT/raw.webm" >/dev/null
    say "녹화 시작 $OUT/raw.webm"
  fi
}

stop_rec() { [[ "${REC:-}" == 1 ]] && "$ADB" emu screenrecord stop >/dev/null && say "녹화 멈춤"; true; }

# 대사 mp3 길이 + 0.1초(없으면 대본 기본값)
holds() {
  local d=(3.6 8.4 2.2) out=()
  for n in 1 2 3; do
    local f="$ROOT/video/public/vo/driver-$n.mp3" h=${d[$n]}
    [[ -s "$f" ]] && h=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$f" | awk '{printf "%.1f", $1 + 0.1}')
    out+=$h
  done
  print -r -- ${(j:,:)out}
}

shot() { [[ -n "${SHOTS:-}" ]] && { mkdir -p "$SHOTS"; "$ADB" exec-out screencap -p > "$SHOTS/$1.png"; }; true; }

play() { ADB="$ADB" "$HERE/play-route.sh" "$SPEED" "$1" > "$OUT/route.log" & ROUTE_PID=$!; }

case "$STEP" in
  reset)
    start_log
    reset_app
    wait_log 'history seeded set=film' 1 30
    ;;

  day1)
    "$ADB" emu geo fix 126.9 37.4 >/dev/null
    start_log
    reset_app
    wait_log 'history seeded set=film' 1 30
    start_rec
    "$ADB" shell am start -W --user 10 -n $PKG/ai.pleos.hello.MainActivity >/dev/null
    sleep 2.5 # S3 동의 화면을 2초 이상 보인다(컷 2)
    shot d1-01-consent
    tap consent
    wait_tap consent
    wait_log 'consent GRANTED' 1 10
    wait_log 'claude warm ok' 1 20
    HOLDS=$(holds)
    demo --es cmd arm --ei day 1 --ef speed "$SPEED" --es holds "$HOLDS"
    wait_log 'demo armed day=1' 1 10
    play "$HERE/demo-route.gpx"
    wait_log 'demo start day=1' 1 20
    sleep 1; shot d1-02-s0
    wait_log 'inject utterance 1' 1 60
    wait_log 'llm ok: claude' 1 60
    wait_log 'committed DONE items=1' 1 30
    wait_log 'inject utterance 2' 1 60
    sleep 1.5; shot d1-03-listening
    wait_log 'llm ok: claude' 2 60
    wait_log 'committed DONE items=2' 1 30
    wait_log 'inject utterance 3' 1 60
    wait_log 'committed FAILED items=1' 1 30
    wait_log 'fake gear P' 1 60
    wait_log 'session end' 1 30
    wait_log 'output showCard' 1 30
    wait_log 'remind candidates n=' 1 30
    wait_log 'summary speak:' 1 30
    shot d1-04-card
    wait_log 'event SummaryDone' 1 30
    sleep 1.5
    tap delete --es text "'음 그 뭐냐'" # adb shell 이 한 번 더 쪼개므로 기기 쪽 따옴표를 넣는다
    wait_tap delete
    sleep 0.5; shot d1-05-delete-ask
    sleep 1
    tap delete-confirm --es text "'음 그 뭐냐'"
    wait_tap delete-confirm
    wait_log 'item deleted id=' 1 10
    sleep 0.5; shot d1-06-deleted
    sleep 2
    tap qr
    wait_tap qr
    wait_log 'qr open bytes=' 1 10
    sleep 0.5; shot d1-07-qr
    sleep 3
    tap qr-close
    wait_tap qr-close
    wait_log 'qr close' 1 10
    sleep 1
    tap history
    wait_tap history
    wait_log 'history rows=[0-9]+ todoOnly=false' 1 10
    sleep 0.5; shot d1-08-history
    sleep 2
    tap todo-only
    wait_tap todo-only
    wait_log 'history rows=[0-9]+ todoOnly=true' 1 10
    sleep 0.5; shot d1-09-todo-only
    sleep 5
    stop_rec
    demo --es cmd ignition-off
    wait_log 'event IgnitionOff' 2 10 # 1번째는 시나리오가 새 세션을 열 때
    wait $ROUTE_PID 2>/dev/null || true
    ROUTE_PID=
    ;;

  day2)
    "$ADB" emu geo fix 126.9 37.4 >/dev/null
    start_log
    start_rec
    "$ADB" shell am start -W --user 10 -n $PKG/ai.pleos.hello.MainActivity >/dev/null
    sleep 1
    demo --es cmd arm --ei day 2 --ef speed "$SPEED"
    wait_log 'demo armed day=2' 1 15
    play "$HERE/demo-route-day2.gpx"
    wait_log 'demo start day=2' 1 20
    sleep 1; shot d2-01-s0
    wait_log 'cue gleo' 1 90
    # "플레오리, …" 만으로는 Gleo 가 기본 리마인더 에이전트로 보낸다(2026-11-08 실측 3/3). "플레오리 앱에서" 를 붙이면 우리 액션으로 온다.
    UTT='플레오리 앱에서, 오늘 오후 세 시 회의 자료 챙기라고 적어줘'
    got=0
    if [[ "${GLEO:-a}" != b ]]; then
      for try in 1 2 3; do
        "$ADB" shell log -t PleoryDemo "gleo inject A try=$try"
        "$ADB" shell am broadcast --user 10 -a ai.umos.intent.action.ASR -p ai.umos.ambientai --es ASR "'$UTT'" >/dev/null
        for _ in {1..40}; do
          grep -q -E 'gleo ADD_DRIVE_NOTES? accepted' "$LOG" && { got=1; break; }
          sleep 0.2
        done
        (( got )) && break
        say "Gleo 가 우리 앱으로 오지 않음(try=$try)"
      done
    fi
    if (( ! got )); then
      "$ADB" shell log -t PleoryDemo "gleo simulated"
      "$ADB" shell am broadcast --user 10 -a $PKG.ADD_DRIVE_NOTE -p $PKG \
        --es ACTION_PARAM "'{\"note\":\"오늘 오후 세 시 회의 자료 챙기기\"}'" >/dev/null
    fi
    wait_log 'gleo ADD_DRIVE_NOTES? accepted' 1 10
    wait_log 'gleo response sent fn=.* status=success' 1 10
    shot d2-02-gleo
    wait_log 'committed (DONE|FAILED)' 1 30
    wait_log 'fake gear P' 1 90
    wait_log 'output showCard' 1 30
    wait_log 'remind candidates n=' 1 30
    wait_log 'remind speak n=' 1 30
    sleep 1; shot d2-03-card-remind
    wait_log 'summary speak:' 1 30
    wait_log 'event SummaryDone' 1 40
    sleep 1.5
    tap remind-done
    wait_tap remind-done
    wait_log 'remind done id=' 1 10
    sleep 0.5; shot d2-04-done
    sleep 2
    stop_rec
    wait $ROUTE_PID 2>/dev/null || true
    ROUTE_PID=
    ;;

  *) echo "v2 reset|day1|day2"; exit 1 ;;
esac
say "끝 ($STEP, $((SECONDS - T0))초). 로그 $LOG"
