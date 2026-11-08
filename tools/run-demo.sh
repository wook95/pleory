#!/bin/zsh
# PRD §8 1~6단계 데모 실행(T8). 앱 안의 시나리오를 무장하고, 같은 배속으로 경로를 재생한다.
# 사용: tools/run-demo.sh [배속=30] [pcm|text]
#   pcm  : u1.pcm·u2.pcm 을 on-device STT 에 넣는다 (먼저 아래 "PCM 준비" 필요)
#   text : STT 를 우회해 텍스트를 인식 결과로 넣는다 (Gemini 이후 체인 검증용)
#
# PCM 준비 (한 번): 16kHz·16bit·mono PCM 두 개를 앱 filesDir 에 둔다.
#   adb push u1.pcm /data/local/tmp/u1.pcm; adb push u2.pcm /data/local/tmp/u2.pcm
#   adb shell run-as ai.pleos.hello --user 10 cp /data/local/tmp/u1.pcm files/u1.pcm   (u2 도 동일)
#
# 동작: 시나리오가 첫 위치 fix 를 받는 순간을 18:40 로 삼아 DemoClock(배속)을 켜고 새 세션을 연다.
#       그러므로 경로 재생(play-route.sh)과 앱 시계가 어긋나지 않는다. 이후 18:57·19:08 에 한마디를 넣고
#       19:31 에 가짜 기어 P, 마지막 fix 의 300m 반경 판정으로 카드가 뜬다.
# 로그: adb logcat -s PleoryFlow:I PleoryDemo:I
set -eu
SPEED="${1:-30}"
MODE="${2:-pcm}"
ADB="${ADB:-adb}"
HERE="${0:A:h}"

# 이전 실행이 남긴 마지막 위치(에뮬레이터가 계속 반복 송출)를 출발점에서 먼 곳으로 치워 둔다.
"$ADB" emu geo fix 126.9 37.4 >/dev/null
"$ADB" shell am start -W --user 10 -n ai.pleos.hello/.debugpanel.DebugPanelActivity \
  --es action demo --es mode "$MODE" --ef speed "$SPEED" >/dev/null
ADB="$ADB" "$HERE/play-route.sh" "$SPEED"
