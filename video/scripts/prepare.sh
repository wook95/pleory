#!/usr/bin/env bash
# 원본 에뮬레이터 녹화를 Remotion 용 mp4 로 바꾸고, logcat PleoryFlow 시각으로 src/timings.json 을 만든다.
# 사용: scripts/prepare.sh [take 디렉토리=../apk/video/take2]
#   입력: <take>/raw.webm (2560x1440), <take>/flow.log (logcat -v time, PleoryFlow), <take>/rec_start.txt (HH:MM:SS)
#   출력: public/footage.mp4 (앱 영역 1682x1210 에서 가장자리 테두리 4px 를 뺀 1674x1202, 30fps, H.264/AAC), src/timings.json
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
TAKE="$(cd "${1:-$HERE/../apk/video/take2}" && pwd)"

if [[ -f "$TAKE/raw.webm" ]]; then
  # 에뮬레이터 녹화의 앱 TTS 는 운전자 대사보다 20dB 남짓 작다. 최대치를 -4dBFS 로 맞춘다.
  PEAK="$(ffmpeg -hide_banner -nostats -i "$TAKE/raw.webm" -vn -af volumedetect -f null - 2>&1 \
    | sed -n 's/.*max_volume: \(-*[0-9.]*\) dB.*/\1/p')"
  GAIN="$(awk -v p="${PEAK:-0}" 'BEGIN { g = -4 - p; if (g < 0) g = 0; printf "%.1f", g }')"
  echo "transcode $TAKE/raw.webm -> public/footage.mp4 (audio +${GAIN}dB)"
  ffmpeg -hide_banner -loglevel error -nostats -y -i "$TAKE/raw.webm" \
    -vf "crop=1674:1202:857:94,fps=30" \
    -c:v libx264 -preset medium -crf 16 -pix_fmt yuv420p \
    -af "volume=${GAIN}dB" -c:a aac -b:a 192k -ar 48000 -movflags +faststart \
    "$HERE/public/footage.mp4"
else
  echo "raw.webm 없음: footage 변환 건너뜀 (컴포지션은 자리표시 화면으로 렌더)"
fi

node "$HERE/scripts/timings.mjs" "$TAKE"
node "$HERE/scripts/scan-assets.mjs"
