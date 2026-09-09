package com.amplitude.android.streaming

import com.amplitude.android.streaming.internal.util.deepCopy
import com.amplitude.core.AmplitudePreview

/**
 * App-supplied metadata for `trackPlayer`.
 *
 * The library infers position, duration, errors, and ended/buffering from the player.
 * Pass [contentId], [title], and [contentType] when the app already knows them.
 *
 * @param contentId Stable id for the current media item (`content_id` on events).
 * @param title Human-readable title.
 * @param contentType One of [CONTENT_TYPE_VOD], [CONTENT_TYPE_LIVE], or [CONTENT_TYPE_AUDIO]
 * when known.
 * @param extraProperties App extras merged onto started/stopped events.
 *
 * ```
 * PlayerContent(
 *     contentId = "ep-1",
 *     title = "Episode 1",
 *     contentType = PlayerContent.CONTENT_TYPE_AUDIO,
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
     * Nested maps and lists are copied too; later mutations of the originals are ignored.
     */
    public val extraProperties: Map<String, Any?>? = extraProperties?.deepCopy()

    public companion object {
        public const val CONTENT_TYPE_VOD: String = "VoD"
        public const val CONTENT_TYPE_LIVE: String = "Live"
        public const val CONTENT_TYPE_AUDIO: String = "Audio"
    }
}
