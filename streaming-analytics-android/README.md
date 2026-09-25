# Streaming Analytics for Android

Media3 / ExoPlayer instrumentation for Amplitude. Add the module, call `trackPlayer`, and Amplitude emits stream events from playback.

This API is `@AmplitudePreview`: it may change in a minor release. Opt in with `@OptIn(AmplitudePreview::class)` (or the equivalent compiler flag).

## Setup

Use the same version as `com.amplitude:analytics-android`.

```kotlin
dependencies {
    implementation("com.amplitude:analytics-android:<version>")
    implementation("com.amplitude:streaming-analytics-android:<version>")
}
```

`streaming-analytics-android` depends on `analytics-android` and `androidx.media3:media3-common`. ExoPlayer and the IMA extension come along as runtime dependencies; you still construct and own the `Player`.

Requires `com.amplitude.android.Amplitude` (the Android SDK), not the core-only client.

`StreamingAnalyticsPlugin` is installed automatically when this artifact is on the classpath. You do not add it yourself. `trackPlayer` logs an error if the plugin is missing.

## Track a player

Call `trackPlayer` once per `Player` you want instrumented. Tracking the same player again is a no-op.

```kotlin
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.amplitude.android.Amplitude
import com.amplitude.android.trackPlayer
import com.amplitude.android.untrackPlayer
import com.amplitude.core.AmplitudePreview

@OptIn(AmplitudePreview::class)
amplitude.trackPlayer(exoPlayer)
```

Set content identity on the player's `MediaItem`. Streaming events report `mediaId` as `content_id`, which is the primary key for joining playback to a catalog. A blank media ID is omitted; the SDK never substitutes or captures the playback URI.

Also mapped:

- `MediaMetadata.title` (or `displayTitle`) to `title`
- JSON-safe primitives from `MediaMetadata.extras` (`String`, numbers, booleans) onto the event

```kotlin
val mediaItem = MediaItem.Builder()
    .setUri(playbackUri)
    .setMediaId("ep-42")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle("Episode 42")
            .setExtras(
                bundleOf(
                    "show_id" to "show-7",
                    "season" to 2,
                ),
            )
            .build(),
    )
    .build()
exoPlayer.setMediaItem(mediaItem)
amplitude.trackPlayer(exoPlayer)
```

Opaque values such as parcelables and nested bundles are ignored. URIs, DRM configuration, and `MediaItem.localConfiguration.tag` are not captured. `delivery_mode` is inferred from whether the player reports live content.

The SDK holds the player with a `WeakReference`. Releasing ExoPlayer is enough to avoid a leak. Call `untrackPlayer` only when you want a terminal stream stop immediately; it is optional.

```kotlin
amplitude.untrackPlayer(exoPlayer)
exoPlayer.release()
```

From Java, use `AmplitudeStreamingAnalytics.trackPlayer` and `AmplitudeStreamingAnalytics.untrackPlayer`.

## Event taxonomy

All event names are prefixed with `[Amplitude]`. Durations and positions are **seconds**.

### Content

| Event | When |
| --- | --- |
| `[Amplitude] Stream Started` | Content starts playing |
| `[Amplitude] Stream Stopped` | Content stops or is interrupted |

`Stream Stopped` includes `stop_reason`:

| `stop_reason` | When |
| --- | --- |
| `paused` | User or app pause |
| `ended` | Playback reached the end, or Media3 auto/repeat item transition |
| `error` | Player error (`error_message` when available) |
| `content_changed` | Media item changed without completing |
| `timeout` | Heartbeat while still playing (delayed event) |
| `untracked` | `untrackPlayer`, Amplitude teardown, or the player was collected |

Shared content properties: `stream_session_id`, `play_id`, `content_id`, `title`, `media_type` (`video` or `audio`), `delivery_mode`, `duration`, `start_time`, `position`, `play_time`, `percent_completed` (stopped).

`play_time` is seconds of playhead movement while playing (pauses, seeks, and buffering do not count). It is cumulative per `stream_session_id`; the latest Stream Stopped holds the session total. Seeking and buffering do not emit Stream Stopped.
