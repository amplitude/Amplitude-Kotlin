# Streaming Analytics Android

Streaming Analytics observes Media3 players and emits `[Amplitude] Stream Started` and
`[Amplitude] Stream Stopped` events.

## Track a player

```kotlin
amplitude.trackPlayer(exoPlayer)
```

Set content identity on the player's `MediaItem`. Streaming events report `mediaId` as
`content_id`, which is the primary key for joining playback to a catalog. A blank media ID is
omitted; the SDK never substitutes or captures the playback URI.

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

Opaque values such as parcelables and nested bundles are ignored. URIs, DRM configuration, and
`MediaItem.localConfiguration.tag` are not captured. `delivery_mode` is inferred from whether
the player reports live content.
