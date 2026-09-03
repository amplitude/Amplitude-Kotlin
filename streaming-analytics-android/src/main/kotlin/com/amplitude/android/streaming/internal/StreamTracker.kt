package com.amplitude.android.streaming.internal

import androidx.media3.common.C
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.android.streaming.internal.util.millisToSeconds
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview

private const val AD_STARTED = "Ad Started"
private const val AD_STOPPED = "Ad Stopped"
private const val AD_SKIPPED = "Ad Skipped"
private const val VIDEO_STARTED = "Video Content Started"
private const val VIDEO_STOPPED = "Video Content Stopped"
private const val AUDIO_STARTED = "Audio Content Started"
private const val AUDIO_STOPPED = "Audio Content Stopped"

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
        viewSessionId: String,
    ) {
        amplitude.track(
            eventType = AD_STARTED,
            eventProperties = adProperties(
                options = options,
                ad = ad,
                viewSessionId = viewSessionId
            ),
        )
    }

    fun trackAdStopped(
        options: PlayerContent,
        ad: AdContext,
        viewSessionId: String,
        watchDurationMillis: Long,
        completed: Boolean,
    ) {
        amplitude.track(
            eventType = AD_STOPPED,
            eventProperties =
                adProperties(options = options, ad = ad, viewSessionId = viewSessionId).apply {
                    put("ad_watch_duration", watchDurationMillis.millisToSeconds())
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
        viewSessionId: String,
    ) {
        amplitude.track(
            eventType = AD_SKIPPED,
            eventProperties = adProperties(
                options = options,
                ad = ad,
                viewSessionId = viewSessionId
            ),
        )
    }

    fun trackVideoStarted(
        options: PlayerContent,
        snapshot: PlayerMediaSnapshot,
        viewSessionId: String,
        timestamp: Long,
        insertId: String,
    ) {
        amplitude.track(
            event =
                DelayedEvent(
                    eventType = VIDEO_STARTED,
                    kind = DelayedEvent.Kind.INSTANT,
                    timestamp = timestamp,
                    eventProperties =
                        contentProperties(
                            options = options,
                            snapshot = snapshot,
                            viewSessionId = viewSessionId,
                            defaultContentType = PlayerContent.CONTENT_TYPE_VOD,
                        ).apply {
                            put("start_position", snapshot.positionMillis.millisToSeconds())
                        },
                ).also { it.insertId = insertId },
        )
    }

    fun trackVideoStopped(
        options: PlayerContent,
        snapshot: PlayerMediaSnapshot,
        viewSessionId: String,
        watchDurationMillis: Long,
        timestamp: Long,
        insertId: String,
        stopReason: StopReason? = null,
        errorMessage: String? = null,
    ) {
        amplitude.track(
            event =
                DelayedEvent(
                    eventType = VIDEO_STOPPED,
                    kind = DelayedEvent.Kind.DELAYED,
                    timestamp = timestamp,
                    eventProperties =
                        stoppedContentProperties(
                            options = options,
                            snapshot = snapshot,
                            viewSessionId = viewSessionId,
                            watchDurationMillis = watchDurationMillis,
                            stopReason = stopReason,
                            errorMessage = errorMessage,
                            defaultContentType = PlayerContent.CONTENT_TYPE_VOD,
                        ),
                ).also { it.insertId = insertId },
        )
    }

    fun trackAudioStarted(
        options: PlayerContent,
        snapshot: PlayerMediaSnapshot,
        viewSessionId: String,
        timestamp: Long,
        insertId: String,
    ) {
        amplitude.track(
            event =
                DelayedEvent(
                    eventType = AUDIO_STARTED,
                    kind = DelayedEvent.Kind.INSTANT,
                    timestamp = timestamp,
                    eventProperties =
                        contentProperties(
                            options = options,
                            snapshot = snapshot,
                            viewSessionId = viewSessionId,
                            defaultContentType = PlayerContent.CONTENT_TYPE_AUDIO,
                        ).apply {
                            put("start_position", snapshot.positionMillis.millisToSeconds())
                        },
                ).also { it.insertId = insertId },
        )
    }

    fun trackAudioStopped(
        options: PlayerContent,
        snapshot: PlayerMediaSnapshot,
        viewSessionId: String,
        watchDurationMillis: Long,
        timestamp: Long,
        insertId: String,
        stopReason: StopReason? = null,
        errorMessage: String? = null,
    ) {
        amplitude.track(
            event =
                DelayedEvent(
                    eventType = AUDIO_STOPPED,
                    kind = DelayedEvent.Kind.DELAYED,
                    timestamp = timestamp,
                    eventProperties =
                        stoppedContentProperties(
                            options = options,
                            snapshot = snapshot,
                            viewSessionId = viewSessionId,
                            watchDurationMillis = watchDurationMillis,
                            stopReason = stopReason,
                            errorMessage = errorMessage,
                            defaultContentType = PlayerContent.CONTENT_TYPE_AUDIO,
                        ),
                ).also { it.insertId = insertId },
        )
    }
}

@OptIn(AmplitudePreview::class)
private fun adProperties(
    options: PlayerContent,
    ad: AdContext,
    viewSessionId: String,
): MutableMap<String, Any?> =
    options.extraProperties.orEmpty().toMutableMap().apply {
        put("ad_id", ad.adId)
        put("content_id", options.contentId ?: ad.contentId)
        put("view_session_id", viewSessionId)
        put("ad_position", ad.contentPositionMillis.millisToSeconds())
        if (ad.durationMillis.isKnownDuration()) {
            put("ad_duration", ad.durationMillis.millisToSeconds())
        }
    }

@OptIn(AmplitudePreview::class)
private fun contentProperties(
    options: PlayerContent,
    snapshot: PlayerMediaSnapshot,
    viewSessionId: String,
    defaultContentType: String,
): MutableMap<String, Any?> =
    options.extraProperties.orEmpty().toMutableMap().apply {
        put("view_session_id", viewSessionId)
        (options.contentId ?: snapshot.mediaId)?.let { put("content_id", it) }
        (options.title ?: snapshot.title)?.let { put("title", it) }
        put(
            "content_type",
            options.contentType
                ?: if (snapshot.isLive) PlayerContent.CONTENT_TYPE_LIVE else defaultContentType,
        )
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
    viewSessionId: String,
    watchDurationMillis: Long,
    stopReason: StopReason?,
    errorMessage: String?,
    defaultContentType: String,
): MutableMap<String, Any?> =
    contentProperties(
        options = options,
        snapshot = snapshot,
        viewSessionId = viewSessionId,
        defaultContentType = defaultContentType,
    ).apply {
        put("current_time", snapshot.positionMillis.millisToSeconds())
        put("watch_duration", watchDurationMillis.millisToSeconds())
        stopReason?.let { put("stop_reason", it.value) }
        errorMessage?.let { put("error_message", it) }
        snapshot.percentCompleted()?.let { percentage ->
            put("percent_completed", percentage)
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
