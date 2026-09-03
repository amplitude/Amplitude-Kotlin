package com.amplitude.android.streaming.internal

import androidx.media3.common.C
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.android.streaming.internal.util.millisToSeconds
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview

private const val AD_STARTED = "[Amplitude] Ad Started"
private const val AD_STOPPED = "[Amplitude] Ad Stopped"
private const val AD_SKIPPED = "[Amplitude] Ad Skipped"
private const val STREAM_STARTED = "[Amplitude] Stream Started"
private const val STREAM_STOPPED = "[Amplitude] Stream Stopped"

internal val StreamingDiGraph.streamTracker: StreamTracker by weak {
    StreamTracker(
        amplitude = amplitude,
    )
}

@OptIn(AmplitudePreview::class)
internal class StreamTracker(
    private val amplitude: Amplitude,
) {
    fun trackAdStarted(
        options: PlayerContent,
        ad: AdContext,
        streamSessionId: String,
    ) {
        amplitude.track(
            eventType = AD_STARTED,
            eventProperties =
                adProperties(
                    options = options,
                    ad = ad,
                    streamSessionId = streamSessionId,
                ),
        )
    }

    fun trackAdStopped(
        options: PlayerContent,
        ad: AdContext,
        streamSessionId: String,
        streamDurationMillis: Long,
        completed: Boolean,
    ) {
        amplitude.track(
            eventType = AD_STOPPED,
            eventProperties =
                adProperties(options = options, ad = ad, streamSessionId = streamSessionId).apply {
                    put("ad_stream_duration", streamDurationMillis.millisToSeconds())
                    put("ad_completion_status", if (completed) "completed" else "abandoned")
                    ad.percentCompleted()?.let { percentage ->
                        put("ad_percent_completed", percentage)
                    }
                },
        )
    }

    fun trackAdSkipped(
        options: PlayerContent,
        ad: AdContext,
        streamSessionId: String,
    ) {
        amplitude.track(
            eventType = AD_SKIPPED,
            eventProperties =
                adProperties(
                    options = options,
                    ad = ad,
                    streamSessionId = streamSessionId,
                ),
        )
    }

    fun trackStreamStarted(
        options: PlayerContent,
        snapshot: PlayerMediaSnapshot,
        mediaType: MediaType,
        streamSessionId: String,
        timestamp: Long,
        insertId: String,
    ) {
        amplitude.track(
            event =
                DelayedEvent(
                    eventType = STREAM_STARTED,
                    kind = DelayedEvent.Kind.INSTANT,
                    timestamp = timestamp,
                    eventProperties =
                        contentProperties(
                            options = options,
                            snapshot = snapshot,
                            mediaType = mediaType,
                            streamSessionId = streamSessionId,
                        ).apply {
                            put("start_position", snapshot.positionMillis.millisToSeconds())
                        },
                ).also { it.insertId = insertId },
        )
    }

    fun trackStreamStopped(
        options: PlayerContent,
        snapshot: PlayerMediaSnapshot,
        mediaType: MediaType,
        streamSessionId: String,
        streamDurationMillis: Long,
        timestamp: Long,
        insertId: String,
        stopReason: StopReason? = null,
        errorMessage: String? = null,
    ) {
        amplitude.track(
            event =
                DelayedEvent(
                    eventType = STREAM_STOPPED,
                    kind = DelayedEvent.Kind.DELAYED,
                    timestamp = timestamp,
                    eventProperties =
                        stoppedContentProperties(
                            options = options,
                            snapshot = snapshot,
                            mediaType = mediaType,
                            streamSessionId = streamSessionId,
                            streamDurationMillis = streamDurationMillis,
                            stopReason = stopReason,
                            errorMessage = errorMessage,
                        ),
                ).also { it.insertId = insertId },
        )
    }
}

@OptIn(AmplitudePreview::class)
private fun adProperties(
    options: PlayerContent,
    ad: AdContext,
    streamSessionId: String,
): MutableMap<String, Any?> =
    options.extraProperties.orEmpty().toMutableMap().apply {
        put("ad_id", ad.adId)
        put("content_id", options.contentId ?: ad.contentId)
        put("stream_session_id", streamSessionId)
        put("ad_position", ad.contentPositionMillis.millisToSeconds())
        if (ad.durationMillis.isKnownDuration()) {
            put("ad_duration", ad.durationMillis.millisToSeconds())
        }
    }

@OptIn(AmplitudePreview::class)
private fun contentProperties(
    options: PlayerContent,
    snapshot: PlayerMediaSnapshot,
    mediaType: MediaType,
    streamSessionId: String,
): MutableMap<String, Any?> =
    options.extraProperties.orEmpty().toMutableMap().apply {
        put("stream_session_id", streamSessionId)
        put("media_type", mediaType.value)
        (options.contentId ?: snapshot.mediaId)?.let { put("content_id", it) }
        (options.title ?: snapshot.title)?.let { put("title", it) }
        put("delivery_mode", deliveryMode(options = options, snapshot = snapshot))
        put("is_in_picture_in_picture", snapshot.isInPictureInPicture)
        put("is_in_background", snapshot.isInBackground)
        if (snapshot.hasKnownDuration()) {
            put("duration", snapshot.durationMillis.millisToSeconds())
        }
    }

@OptIn(AmplitudePreview::class)
private fun stoppedContentProperties(
    options: PlayerContent,
    snapshot: PlayerMediaSnapshot,
    mediaType: MediaType,
    streamSessionId: String,
    streamDurationMillis: Long,
    stopReason: StopReason?,
    errorMessage: String?,
): MutableMap<String, Any?> =
    contentProperties(
        options = options,
        snapshot = snapshot,
        mediaType = mediaType,
        streamSessionId = streamSessionId,
    ).apply {
        put("current_time", snapshot.positionMillis.millisToSeconds())
        put("stream_duration", streamDurationMillis.millisToSeconds())
        stopReason?.let { put("stop_reason", it.value) }
        errorMessage?.let { put("error_message", it) }
        snapshot.percentCompleted()?.let { percentage ->
            put("percent_completed", percentage)
        }
    }

@OptIn(AmplitudePreview::class)
private fun deliveryMode(
    options: PlayerContent,
    snapshot: PlayerMediaSnapshot,
): String =
    when (options.deliveryMode) {
        PlayerContent.DELIVERY_MODE_LIVE -> PlayerContent.DELIVERY_MODE_LIVE
        PlayerContent.DELIVERY_MODE_ON_DEMAND -> PlayerContent.DELIVERY_MODE_ON_DEMAND
        else ->
            if (snapshot.isLive) {
                PlayerContent.DELIVERY_MODE_LIVE
            } else {
                PlayerContent.DELIVERY_MODE_ON_DEMAND
            }
    }

private fun PlayerMediaSnapshot.hasKnownDuration(): Boolean =
    !isLive && durationMillis.isKnownDuration()

private fun Long.isKnownDuration(): Boolean = this != C.TIME_UNSET && this > 0

private fun PlayerMediaSnapshot.percentCompleted(): Double? {
    if (!hasKnownDuration()) {
        return null
    }
    return (positionMillis.toDouble() / durationMillis.toDouble() * 100.0)
        .coerceIn(0.0, 100.0)
}

internal data class AdContext(
    val adGroupIndex: Int,
    val adIndexInAdGroup: Int,
    val positionMillis: Long,
    val durationMillis: Long,
    val contentPositionMillis: Long,
    val contentId: String?,
) {
    val adId: String
        get() = "${contentId.orEmpty()}:$adGroupIndex:$adIndexInAdGroup"
}

internal fun AdContext.percentCompleted(): Double? {
    if (!durationMillis.isKnownDuration()) {
        return null
    }
    return (100.0 * positionMillis.toDouble() / durationMillis)
        .coerceIn(0.0, 100.0)
}

internal data class PlayerMediaSnapshot(
    val positionMillis: Long,
    val durationMillis: Long = C.TIME_UNSET,
    val isLive: Boolean = false,
    val mediaId: String? = null,
    val title: String? = null,
    val isInPictureInPicture: Boolean = false,
    val isInBackground: Boolean = false,
)

internal enum class MediaType(
    val value: String,
) {
    VIDEO("video"),
    AUDIO("audio"),
}

internal enum class StopReason(
    val value: String,
) {
    COMPLETED("completed"),
    PAUSED("paused"),
    SEEKING("seeking"),
    WAITING("waiting"),
    ERROR("error"),
    UNSUBSCRIBED("unsubscribed"),
}
