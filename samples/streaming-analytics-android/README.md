# Streaming Analytics sample

Android sample for `com.amplitude:streaming-analytics-android`.

## What it shows

* **XML** — one Media3 `PlayerView` (VoD). PiP and background playback stay on this player.
* **Compose** — two VoD `PlayerView`s plus one audio-only player. PiP and background playback stay on video 1 only.
* Amplitude is created in a Metro `AppGraph`. Each screen is an Activity plus a ViewModel that owns `DemoPlayer`s and calls `trackPlayer`. Metro is pinned to `0.6.5` so it matches Kotlin `2.2.10` without requiring JDK 21.

## Run

1. Set `AMPLITUDE_API_KEY` in `local.properties`.
2. `./gradlew :samples:streaming-app:installDebug`
