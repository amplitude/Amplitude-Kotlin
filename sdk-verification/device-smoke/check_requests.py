#!/usr/bin/env python3
"""Validate captured traffic, optionally waiting for asynchronous uploads to finish."""
import argparse
import json
import time
from pathlib import Path

API_KEY = "unified-smoke-api-key"
DEPLOYMENT_KEY = "unified-smoke-deployment-key"
REPLAY_ID = "[Amplitude] Session Replay ID"


def validate(requests, unified_version, analytics_version):
    events = [e for r in requests if r["path"] == "/2/httpapi" for e in r["body"]["events"]]
    replays = [r for r in requests if r["path"] == "/sessions/v2/track"]
    fetches = [r for r in requests if r["path"] == "/sdk/v2/vardata"]
    smoke = [e for e in events if e["event_type"] == "smoke_event"]
    exposures = [e for e in events if e["event_type"] == "$exposure"]
    library = f"amplitude-android-unified/{unified_version}-amplitude-analytics-android/{analytics_version}"
    failures = []

    def check(condition, message):
        if not condition:
            failures.append(message)

    check(events and all(e.get("library") == library for e in events), "exact library attribution on every event")
    check(any(e["event_type"] == "session_start" for e in events), "session_start uploaded")
    check(not any(e["event_type"] == "smoke_event_opted_out" for e in events), "opted-out event suppressed")
    check(all(r["body"].get("api_key") == API_KEY for r in requests if r["path"] == "/2/httpapi"), "Analytics API key")
    check(len(smoke) == 2, "two smoke events uploaded")
    if len(smoke) == 2:
        before, after = smoke
        check(before["device_id"] != after["device_id"], "reset rotates device ID")
        check(after.get("user_id") is None, "reset clears user ID")
        check(any(r.get("experiment_user", {}).get("device_id") == after["device_id"] and
                  not r["experiment_user"].get("user_id") for r in fetches), "Experiment follows reset identity")
        for event in smoke:
            check(event.get("event_properties", {}).get(REPLAY_ID) == f"{event['device_id']}/{event['session_id']}",
                  "smoke event enriched by Replay")
    check(len(exposures) == 1, "exactly one exposure uploaded")
    if exposures:
        exposure = exposures[0]
        props = exposure.get("event_properties", {})
        check(props.get("flag_key") == "checkout" and props.get("variant") == "treatment", "checkout=treatment exposure")
        check(exposure.get("user_id") == "smoke-user", "exposure user identity")
        check(props.get(REPLAY_ID) == f"{exposure['device_id']}/{exposure['session_id']}", "exposure enriched by Replay")
    check(fetches and all(r["headers"].get("authorization") == f"Api-Key {DEPLOYMENT_KEY}" for r in fetches),
          "Experiment deployment key")
    check(any(r.get("experiment_user", {}).get("user_id") == "smoke-user" for r in fetches), "Experiment follows setUserId")
    check(bool(replays), "Replay uploaded")
    check(all(r["headers"].get("authorization") == f"Bearer {API_KEY}" for r in replays), "Replay API key")
    analytics_ids = {(e["device_id"], str(e["session_id"])) for e in events}
    check(all((r["query"].get("device_id"), r["query"].get("session_id")) in analytics_ids for r in replays),
          "Replay upload matches Analytics identity and session")
    return failures


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    parser.add_argument("--versions", type=Path, required=True)
    parser.add_argument("--wait-seconds", type=int, default=0)
    args = parser.parse_args()
    unified, analytics = args.versions.read_text().splitlines()
    deadline = time.monotonic() + args.wait_seconds
    while True:
        try:
            requests = [json.loads(line) for line in args.log.read_text().splitlines()]
            failures = validate(requests, unified, analytics)
        except (ValueError, KeyError, TypeError) as error:
            failures = [f"Malformed request log: {error}"]
        if not failures or time.monotonic() >= deadline:
            break
        time.sleep(0.2)
    for failure in failures:
        print(f"FAIL {failure}")
    if not failures:
        print("PASS Analytics, Experiment, and Replay wire contracts")
    raise SystemExit(bool(failures))


if __name__ == "__main__":
    main()
