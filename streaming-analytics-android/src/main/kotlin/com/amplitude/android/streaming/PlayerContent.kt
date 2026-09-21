package com.amplitude.android.streaming

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.amplitude.android.streaming.internal.util.deepCopy

/**
 * Metadata resolved from the current [androidx.media3.common.MediaItem].
 *
 * The library infers position, duration, errors, ended/buffering, and delivery mode from
 * the player.
 */
internal class PlayerContent(
    val contentId: String? = null,
    val title: String? = null,
    extraProperties: Map<String, Any?>? = null,
) {
    /**
     * Defensive copy so callers can mutate the map they passed without racing the tracker.
     * Nested maps and lists are copied too; later mutations of the originals are ignored.
     */
    val extraProperties: Map<String, Any?>? = extraProperties?.deepCopy()

    companion object {
        const val DELIVERY_MODE_LIVE: String = "live"
        const val DELIVERY_MODE_ON_DEMAND: String = "on_demand"
    }
}

internal fun MediaItem.toPlayerContent(): PlayerContent {
    val id: String? = mediaId
    val metadata: MediaMetadata? = mediaMetadata
    return PlayerContent(
        contentId = id?.takeIf { it.isNotBlank() },
        title = metadata?.title?.toString() ?: metadata?.displayTitle?.toString(),
        extraProperties = metadata?.extras?.jsonSafeProperties(),
    )
}

/**
 * Reading an app-supplied bundle can throw when it holds classes this process cannot
 * unmarshall, so a failure drops the extras rather than the event.
 */
@Suppress("DEPRECATION")
private fun Bundle.jsonSafeProperties(): Map<String, Any?>? =
    runCatching {
        keySet()
            .mapNotNull { key ->
                when (val value = get(key)) {
                    is CharSequence -> key to value.toString()
                    is Number, is Boolean -> key to value
                    else -> null
                }
            }.toMap()
            .ifEmpty { null }
    }.getOrNull()
