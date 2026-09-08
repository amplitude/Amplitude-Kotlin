# Streaming Analytics sample

Android sample for `com.amplitude:streaming-analytics-android`.

## What it shows

* **XML** — one Media3 `PlayerView` (VoD). PiP and background playback stay on this player.
* **Compose** — two VoD `PlayerView`s plus one audio-only player. PiP and background playback stay on video 1 only.
* Amplitude is created in a Metro `AppGraph`. Each screen is an Activity plus a ViewModel that owns `DemoPlayer`s and calls `trackPlayer`. Metro is pinned to `0.6.5` so it matches Kotlin `2.2.10` without requiring JDK 21.
* **IMA ads** — both VoD items use Google IMA sample tags via `ImaAdsLoader`. Swap media to switch tags. Audio stays ad-free.

### Ads to exercise

* **Big Buck Bunny · skippable preroll** — skippable VAST preroll. Skip the IMA overlay to emit `[Amplitude] Ad Skipped` plus `[Amplitude] Ad Stopped` (`ad_completion_status=skipped`). Let it finish for `completed`.
* **Frame counter · VMAP pre/mid/post** — preroll, mid-roll, and post-roll. Mid-roll completion should be `completed`, not a skip.

Ad minutes are `ad_watch_duration` on `[Amplitude] Ad Stopped`. Pause during an ad should not accrue watch time.

## Run

1. Set `AMPLITUDE_API_KEY` in `local.properties`.
2. `./gradlew :samples:streaming-app:installDebug`
