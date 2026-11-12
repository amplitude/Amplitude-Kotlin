#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
maestro="${MAESTRO:-$HOME/.maestro/bin/maestro}"
out=build/smoke
port=8089
mkdir -p "$out"
./gradlew :app:assembleRelease :app:writeSmokeVersions "$@"
: > "$out/requests.jsonl"
python3 mock_server.py --port "$port" --log "$out/requests.jsonl" > "$out/mock_server.log" 2>&1 &
server=$!
cleanup() {
  "$adb" logcat -d > "$out/logcat.txt" 2>&1 || true
  "$adb" shell am force-stop com.amplitude.unified.smoke || true
  "$adb" reverse --remove "tcp:$port" || true
  kill "$server" 2>/dev/null || true
  wait "$server" 2>/dev/null || true
}
trap cleanup EXIT
python3 - "$server" <<'PY'
import os, sys, time, urllib.request
for attempt in range(50):
    os.kill(int(sys.argv[1]), 0)
    try:
        urllib.request.urlopen("http://127.0.0.1:8089/health", timeout=0.2).close()
        break
    except OSError:
        time.sleep(0.1)
else:
    raise SystemExit("Mock server failed to become ready")
PY
"$adb" reverse "tcp:$port" "tcp:$port"
"$adb" logcat -c
"$adb" install -r app/build/outputs/apk/release/app-release.apk
"$maestro" test .maestro/smoke.yaml --debug-output "$out/maestro"
python3 check_requests.py "$out/requests.jsonl" --versions "$out/versions.txt" --wait-seconds 30
