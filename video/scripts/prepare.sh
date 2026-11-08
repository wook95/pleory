#!/usr/bin/env bash
# 원본 에뮬레이터 녹화를 Remotion 용 mp4 로 바꾸고, logcat PleoryFlow 시각으로 src/timings.json 을 만든다.
# 사용: scripts/prepare.sh [take 디렉토리=../apk/video/take2]  (v2: scripts/prepare.sh ../apk/video/v2 -> footage-d1.mp4, footage-d2.mp4)
#   입력: <take>/raw.webm (2560x1440), <take>/flow.log (logcat -v time, PleoryFlow), <take>/rec_start.txt (HH:MM:SS)
#   출력: public/footage.mp4 (앱 영역 1682x1210 에서 가장자리 테두리 4px 를 뺀 1674x1202, 30fps, H.264/AAC), src/timings.json
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
TAKE="$(cd "${1:-$HERE/../apk/video/take2}" && pwd)"

# raw.webm -> mp4. 에뮬레이터 녹화 소리는 소리가 날 때만 패킷이 있고 사이가 비어 있다. aresample async 로 빈 곳을 무음으로
# 채워 이어 붙인다(안 그러면 원음을 잘라 쓸 때 소리가 앞당겨지거나 빠진다). 에뮬레이터 녹화의 앱 TTS 는 운전자 대사보다 20dB 남짓 작다. 최대치를 -4dBFS 로 맞춘다.
transcode() {
  local raw="$1" out="$2"
  local peak gain
  peak="$(ffmpeg -hide_banner -nostats -i "$raw" -vn -af volumedetect -f null - 2>&1 \
    | sed -n 's/.*max_volume: \(-*[0-9.]*\) dB.*/\1/p')"
  gain="$(awk -v p="${peak:-0}" 'BEGIN { g = -4 - p; if (g < 0) g = 0; printf "%.1f", g }')"
  echo "transcode $raw -> $out (audio +${gain}dB)"
  ffmpeg -hide_banner -loglevel error -nostats -y -i "$raw" \
    -vf "crop=1674:1202:857:94,fps=30" \
    -c:v libx264 -preset medium -crf 16 -pix_fmt yuv420p \
    -af "aresample=async=1:first_pts=0,volume=${gain}dB" -c:a aac -b:a 192k -ar 48000 -movflags +faststart \
    "$out"
}

if [[ -f "$TAKE/day1/raw.webm" && -f "$TAKE/day2/raw.webm" ]]; then
  # v2 (tools/run-demo.sh v2 의 apk/video/v2): 하루에 한 테이크
  transcode "$TAKE/day1/raw.webm" "$HERE/public/footage-d1.mp4"
  transcode "$TAKE/day2/raw.webm" "$HERE/public/footage-d2.mp4"
elif [[ -f "$TAKE/raw.webm" ]]; then
  transcode "$TAKE/raw.webm" "$HERE/public/footage.mp4"
else
  echo "raw.webm 없음: footage 변환 건너뜀 (컴포지션은 자리표시 화면으로 렌더)"
fi

node "$HERE/scripts/timings.mjs" "$TAKE"
node "$HERE/scripts/scan-assets.mjs"
