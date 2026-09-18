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
import com.amplitude.android.Amplitude
import com.amplitude.android.trackPlayer
import com.amplitude.android.untrackPlayer
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.core.AmplitudePreview

@OptIn(AmplitudePreview::class)
amplitude.trackPlayer(exoPlayer) { mediaItem ->
    PlayerContent(
        contentId = mediaItem?.mediaId,
        title = "Episode 1",
        deliveryMode = PlayerContent.DELIVERY_MODE_ON_DEMAND,
        extraProperties = mapOf("show_id" to "show-42"),
    )
}
```

`PlayerContentProvider` runs for the item already loaded and again on each media transition. Keep it fast.

| Field | Event property | Notes |
| --- | --- | --- |
| `contentId` | `content_id` | Falls back to `MediaItem.mediaId` |
| `title` | `title` | Falls back to media metadata when omitted |
| `deliveryMode` | `delivery_mode` | `live` or `on_demand`; inferred from the timeline if omitted |
| `extraProperties` | merged onto stream events | Deep-copied at construction |

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
| `seeking` | Seek in progress |
| `waiting` | Buffering |
| `error` | Player error (`error_message` when available) |
| `content_changed` | Media item changed without completing |
| `timeout` | Heartbeat while still playing (delayed event) |
| `untracked` | `untrackPlayer`, Amplitude teardown, or the player was collected |

Shared content properties: `stream_session_id`, `play_id`, `content_id`, `title`, `media_type` (`video` or `audio`), `delivery_mode`, `duration`, `start_time`, `position`, `stream_duration`, `percent_completed` (stopped), `is_in_picture_in_picture`, `is_in_background`.
