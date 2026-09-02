package com.amplitude.android.streaming

import com.amplitude.core.AmplitudePreview

/**
 * App-supplied metadata for `trackPlayer`.
 *
 * The library infers position, duration, errors, and ended/buffering from the player.
 * Pass [contentId], [title], and [contentType] when the app already knows them.
 *
 * @param contentId Stable id for the current media item (`content_id` on events).
 * @param title Human-readable title.
 * @param contentType One of `VoD`, `live`, `audio`, or `podcast` when known.
 * @param extraProperties App extras merged onto started/stopped events.
 *
 * ```
 * PlayerContent(
 *     contentId = "ep-1",
 *     title = "Episode 1",
 *     contentType = PlayerContent.CONTENT_TYPE_PODCAST,
 * )
 * ```
 */
@AmplitudePreview
public class PlayerContent @JvmOverloads constructor(
    public val contentId: String? = null,
    public val title: String? = null,
    public val contentType: String? = null,
    extraProperties: Map<String, Any?>? = null,
) {
    /**
     * Defensive copy so callers can mutate the map they passed without racing the tracker.
     */
    public val extraProperties: Map<String, Any?>? = extraProperties?.toMap()

    public companion object {
        public const val CONTENT_TYPE_VOD: String = "VoD"
        public const val CONTENT_TYPE_LIVE: String = "Live"
        public const val CONTENT_TYPE_AUDIO: String = "Audio"
    }
}
