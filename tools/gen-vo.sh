#!/usr/bin/env bash
# 영상 운전자 대사를 ElevenLabs(eleven_v3)로 만든다. 앱에는 쓰지 않는다(영상 전용).
# 키: local.properties 의 tts.elevenKey 또는 셸 ELEVEN_API_KEY. 값은 출력하지 않는다.
#   tools/gen-vo.sh voices            # 한국어 공유 보이스 후보 목록
#   tools/gen-vo.sh <voice_id>        # video/public/vo/<이름>.mp3 생성(아래 LINES, 이미 있으면 건너뜀)
set -euo pipefail
cd "$(dirname "$0")/.."
KEY="${ELEVEN_API_KEY:-$(grep -E '^tts\.elevenKey=' local.properties 2>/dev/null | cut -d= -f2- || true)}"
[ -n "$KEY" ] || { echo "tts.elevenKey 가 없습니다 (local.properties)"; exit 1; }
API=https://api.elevenlabs.io

if [ "${1:-}" = voices ]; then
  curl -sf -H "xi-api-key: $KEY" "$API/v1/shared-voices?language=ko&page_size=30" |
    python3 -c 'import json,sys
for v in json.load(sys.stdin).get("voices",[]): print(v["voice_id"], "|", v.get("name"), "|", v.get("gender"), v.get("age"), "|", (v.get("description") or "")[:60])'
  echo "--- 내 보이스"
  curl -sf -H "xi-api-key: $KEY" "$API/v1/voices" |
    python3 -c 'import json,sys
for v in json.load(sys.stdin).get("voices",[]): print(v["voice_id"], "|", v.get("name"), "|", v.get("labels"))'
  exit 0
fi

VOICE="${1:?voice_id 필요 (tools/gen-vo.sh voices)}"
OUT=video/public/vo; mkdir -p "$OUT"
# 단어는 DemoScenario.kt(RAMBLE)·run-demo.sh 의 Gleo 문구와 같게(video-script.md v3 대사 표). 대괄호는 v3 오디오 태그.
# "이름|대사". v3 영상은 ramble-1(1일차 혼잣말)과 driver-4(2일차 Gleo)만 쓴다. driver-1~3 은 v2 영상용으로 남겨 둔다.
LINES=(
  "driver-1|[sighs] 오늘 팀장 때문에... 진짜 힘들었다."
  "driver-2|내일 거래처 서류 다시 보내야 해. [pause] [soft laugh] 아, 노을 예쁘다."
  "driver-3|음... [pause] 그 뭐냐..."
  "driver-4|플레오리 앱에서, [pause] 오늘 오후 세 시 회의 자료 챙기라고 적어줘."
  "ramble-1|[sighs] 오늘 팀장 때문에... 진짜 힘들었다. [pause] 아 근데, 노들섬 예쁘네. 이번 주말에 여기 데이트 한번 올까? [pause] 아 맞다, 내일 거래처 서류 다시 보내야 되고... 회의 자료도 만들어야 되네. [sighs] 날씨는 왜 이렇게 흐려... 기분까지 우울하다. 짬뽕이나 먹어야겠다. [pause] 요즘 주식은 어떻게 되고 있으려나..."
)
# 이미 있는 파일은 다시 만들지 않는다(크레딧 절약). 다시 만들려면 지우고 돌린다.
for entry in "${LINES[@]}"; do
  name="${entry%%|*}"; line="${entry#*|}"
  [ -s "$OUT/$name.mp3" ] && { echo "$name.mp3 있음, 건너뜀"; continue; }
  body=$(python3 -c 'import json,sys;print(json.dumps({"text":sys.argv[1],"model_id":"eleven_v3","language_code":"ko","voice_settings":{"stability":0.5}}))' "$line")
  curl -sf -X POST "$API/v1/text-to-speech/$VOICE?output_format=mp3_44100_128" \
    -H "xi-api-key: $KEY" -H 'Content-Type: application/json' -d "$body" -o "$OUT/$name.mp3" \
    || { echo "$name 실패"; exit 1; }
  echo "$name.mp3 $(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/$name.mp3")s"
done
