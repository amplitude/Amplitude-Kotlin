# Streaming Analytics sample

Android sample for `com.amplitude:streaming-analytics-android`.

## What it shows

* **XML** — one Media3 `PlayerView` (VoD). PiP and background playback stay on this player.
* **Compose** — two VoD `PlayerView`s plus one audio-only player. PiP and background playback stay on video 1 only.
* Amplitude is created in a Metro `AppGraph`. Each screen is an Activity plus a ViewModel that owns `DemoPlayer`s and calls `trackPlayer`. Catalog IDs, titles, and extra properties are stored on each `MediaItem`. Metro is pinned to `0.6.5` so it matches Kotlin `2.2.10` without requiring JDK 21.
* **IMA ads** — both VoD items use Google IMA sample tags via `ImaAdsLoader`. Swap media to switch tags. Audio stays ad-free.

### Ads to exercise

* **Big Buck Bunny · skippable preroll** — skippable VAST preroll. Skip the IMA overlay to emit `[Amplitude] Ad Skipped` plus `[Amplitude] Ad Stopped` (`ad_completion_status=skipped`). Let it finish for `ended`.
* **Frame counter · VMAP pre/mid/post** — preroll, mid-roll, and post-roll. Mid-roll completion should be `ended`, not a skip.

Ad minutes are `ad_play_time` on `[Amplitude] Ad Stopped`. Pause during an ad should not accrue play time.

## Run

1. Set `AMPLITUDE_API_KEY` in `local.properties`.
2. Install the sample:

   ```bash
   ./gradlew :samples:streaming-app:installDebug
   ```

The same APK supports Android mobile and Android TV. On TV it appears in the Apps row as
**Streaming Analytics Sample** and can be operated entirely with a D-pad remote.

## Android TV

### Emulator setup

1. In Android Studio, open **Device Manager** and create a virtual device from the **TV** category
   (for example, Android TV 1080p).
2. Select an Android TV Google APIs image and start the emulator.
3. Install the sample with the command above, then launch it from the TV home screen.

To launch it directly from a terminal:

```bash
adb shell am start \
  -n com.amplitude.android.streaming.sample/.LauncherActivity
```

### Remote-only test checklist

Use only the emulator remote or a physical D-pad remote.

#### Launcher

- Confirm the app is present in the TV launcher with its banner and title.
- Confirm **XML · one VoD** has initial focus.
- Move focus between both choices and confirm the focused action has a visible outline.

#### XML player

- Move focus between the player and Play, Pause, seek, Swap media, and Enter PiP controls.
- Play, pause, seek backward and forward, and swap media.
- Press Home during playback, reopen the app, and confirm background/resume behavior.
- Enter PiP if the device or emulator image supports TV PiP.

#### Compose player

- Reach every control with the D-pad; focused Compose buttons should scale and show an outline.
- Start both video players and the audio-only player.
- Pause, seek, and swap media independently on each player.
- Scroll through all three player cards using focus navigation.
- Press Home during playback, reopen the app, and confirm background/resume behavior.
- Enter PiP if the device or emulator image supports TV PiP.

#### Ads and analytics

- Let the Big Buck Bunny preroll finish, then repeat and skip it.
- Swap to the frame-counter media and exercise its pre-roll and mid-roll.
- Confirm ad watch duration excludes time spent paused.

The sample enables Amplitude debug logging. Inspect SDK activity with:

```bash
adb logcat -s Amplitude
```

In Amplitude, look up user `streaming-analytics-sample-user` and confirm the expected streaming and
ad events arrive. In particular, skipping the preroll should emit `[Amplitude] Ad Skipped` followed
by `[Amplitude] Ad Stopped` with `ad_completion_status=skipped`; completed ads should report
`ad_completion_status=completed`.
