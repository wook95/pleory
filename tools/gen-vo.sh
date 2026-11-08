#!/usr/bin/env bash
# 영상 운전자 대사를 ElevenLabs(eleven_v3)로 만든다. 앱에는 쓰지 않는다(영상 전용).
# 키: local.properties 의 tts.elevenKey 또는 셸 ELEVEN_API_KEY. 값은 출력하지 않는다.
#   tools/gen-vo.sh voices            # 한국어 공유 보이스 후보 목록
#   tools/gen-vo.sh <voice_id>        # video/public/vo/driver-{1,2}.mp3 생성
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
# 단어는 DemoScenario.kt TEXTS 와 같게(대본 A안). 대괄호는 v3 오디오 태그.
LINES=(
  "[sighs] 오늘 팀장 때문에... 진짜 힘들었다."
  "내일 거래처 서류 다시 보내야 해. [pause] [soft laugh] 아, 노을 예쁘다."
)
for i in "${!LINES[@]}"; do
  n=$((i+1))
  body=$(python3 -c 'import json,sys;print(json.dumps({"text":sys.argv[1],"model_id":"eleven_v3","language_code":"ko","voice_settings":{"stability":0.5}}))' "${LINES[$i]}")
  curl -sf -X POST "$API/v1/text-to-speech/$VOICE?output_format=mp3_44100_128" \
    -H "xi-api-key: $KEY" -H 'Content-Type: application/json' -d "$body" -o "$OUT/driver-$n.mp3" \
    || { echo "driver-$n 실패"; exit 1; }
  echo "driver-$n.mp3 $(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/driver-$n.mp3")s"
done
