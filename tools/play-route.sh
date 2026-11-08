#!/bin/zsh
# PLACEHOLDER (E1 임시): tools/demo-route.gpx 를 에뮬레이터에 재생한다.
# 사용: tools/play-route.sh <배속>   예) tools/play-route.sh 25   (디버그 패널 DemoClock 배속과 같은 값)
# GPX 타임스탬프 간격 ÷ 배속 만큼 쉬면서 `adb emu geo fix <lon> <lat>` 를 보낸다.
# 앱의 DemoClock 은 디버그 패널에서 켠 시점부터 18:40 기준으로 같은 배속으로 흐르므로, 켠 직후에 이 스크립트를 시작한다.
set -eu
SPEED="${1:-25}"
GPX="${2:-${0:A:h}/demo-route.gpx}"
ADB="${ADB:-adb}"

/opt/homebrew/bin/python3 -I - "$GPX" "$SPEED" "$ADB" <<'PY'
import re, subprocess, sys, time
from datetime import datetime
gpx, speed, adb = sys.argv[1], float(sys.argv[2]), sys.argv[3]
pts = [(float(m[0]), float(m[1]), datetime.fromisoformat(m[2]))
       for m in re.findall(r'<trkpt lat="([\d.]+)" lon="([\d.]+)"><time>([^<]+)</time>', open(gpx).read())]
start = time.monotonic()
for lat, lon, t in pts:
    due = (t - pts[0][2]).total_seconds() / speed
    time.sleep(max(0.0, due - (time.monotonic() - start)))
    subprocess.run([adb, "emu", "geo", "fix", str(lon), str(lat)], check=True, stdout=subprocess.DEVNULL)
    print(f"{t.time()} {lat:.5f},{lon:.5f}", flush=True)
PY
