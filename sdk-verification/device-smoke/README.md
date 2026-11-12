# Unified device smoke

A standalone AGP 8.13 / Kotlin 2.2 app consumes only Unified's Maven coordinate. Its release build enables R8 and resource shrinking, using SDK consumer rules. It has no added coroutines, OkHttp, or app keep rules.

## Run

Install JDK 17, Android SDK platform 36, and Maestro 2.6.1. Start one API 35 emulator or connect one device. This device lane packages the checkout independently of the JVM verification modes.

Publish its candidates from the repo root using JDK 21:

```bash
verification_repo="$PWD/build/device-smoke-maven"
candidate="$(sed -n 's/^VERSION_NAME=//p' gradle.properties)-smoke-$(git rev-parse --short HEAD)-SNAPSHOT"
./gradlew :analytics-core:publishToMavenLocal :android:publishToMavenLocal :unified:publishToMavenLocal \
  -PVERSION_NAME="$candidate" -Dmaven.repo.local="$verification_repo"
```

Then select JDK 17 and run:

```bash
sdk-verification/device-smoke/run.sh \
  -PunifiedVersion="$candidate" -PanalyticsVersion="$candidate" \
  -Dmaven.repo.local="$verification_repo"
```

`unifiedVersion` defaults to the released version in `unified/gradle.properties`. `analyticsVersion` optionally constrains Analytics to a candidate. The runner records resolved versions for exact attribution assertions. Select a different Maestro executable with `MAESTRO`.

The runner builds the minified APK, starts local mock endpoints, installs and clears the app, drives Maestro, and waits up to 30 seconds for expected uploads. It cleans up the server, app, and adb port forwarding on exit. Evidence is saved under `build/smoke/`, including traffic, resolved versions, Maestro output, and logcat.

## Checks

- Every Analytics event has exact Unified and Analytics version attribution and the Analytics API key.
- Replay records and uploads with matching Analytics device/session IDs and API key.
- Customer events and the Experiment exposure carry the matching Replay ID.
- Experiment uses its separate deployment key and follows user changes and reset.
- Opt-out stops recording and suppresses a tracked event. Opt-in resumes recording.
- Reset rotates the device ID and clears the user ID in Analytics and Experiment.

The API 35 CI job runs this flow on every PR. Test the traffic validator independently with:

```bash
python3 -m unittest discover -s sdk-verification/device-smoke/tests -v
```
