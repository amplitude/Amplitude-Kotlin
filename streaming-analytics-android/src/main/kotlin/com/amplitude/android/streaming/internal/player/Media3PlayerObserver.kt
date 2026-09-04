package com.amplitude.android.streaming.internal.player

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.amplitude.android.streaming.internal.AdContext
import com.amplitude.android.streaming.internal.MediaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private const val BUFFERING_DEBOUNCE_MILLIS = 500L
private const val EVENT_BUFFER_CAPACITY = 64

internal class Media3PlayerObserver(
    private val player: Player,
    private val scope: CoroutineScope,
    private val playerDispatcher: CoroutineDispatcher = player.createPlayerDispatcher(),
) : Player.Listener,
    PlayerObserver {
    private val _eventFlow =
        MutableSharedFlow<PlayerEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    override val eventFlow: SharedFlow<PlayerEvent> = _eventFlow.asSharedFlow()
    private val mutex = Mutex()
    private var bufferingJob: Job? = null
    private var observing = false
    private var activeAd: AdContext? = null

    init {
        scope.launch {
            try {
                _eventFlow.subscriptionCount
                    .map { it > 0 }
                    .distinctUntilChanged()
                    .collect { subscribed ->
                        if (subscribed) attach() else detach()
                    }
            } finally {
                withContext(NonCancellable) {
                    detach()
                }
            }
        }
    }

    override suspend fun snapshot(): PlayerMediaSnapshot =
        withContext(playerDispatcher) {
            val item = player.currentMediaItem
            val metadata = item?.mediaMetadata
            PlayerMediaSnapshot(
                positionMillis = player.currentPosition.coerceAtLeast(0L),
                durationMillis = player.duration,
                isLive = player.isCurrentMediaItemLive,
                mediaId = item?.mediaId?.takeIf { it.isNotEmpty() },
                title = metadata?.title?.toString() ?: metadata?.displayTitle?.toString(),
                mediaType = player.mediaType(),
            )
        }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            emit(PlayerEvent.Playing)
        } else if (player.playbackState == Player.STATE_READY) {
            emit(PlayerEvent.Paused)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> startBufferingDebounce()
            Player.STATE_READY -> {
                cancelBuffering()
                emit(PlayerEvent.Ready)
            }
            Player.STATE_ENDED -> {
                cancelBuffering()
                emit(PlayerEvent.Ended)
            }
            Player.STATE_IDLE -> cancelBuffering()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        cancelBuffering()
        emit(PlayerEvent.Error(error.message))
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            emit(PlayerEvent.Seeking)
        }
        if (oldPosition.adGroupIndex != C.INDEX_UNSET &&
            (
                newPosition.adGroupIndex == C.INDEX_UNSET ||
                    oldPosition.adGroupIndex != newPosition.adGroupIndex ||
                    oldPosition.adIndexInAdGroup != newPosition.adIndexInAdGroup
            )
        ) {
            finishAdForTransition(
                completed = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
                positionMillis = oldPosition.positionMs,
            )
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        emit(PlayerEvent.MediaChanged(mediaItem))
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        detectAdTransition()
    }

    internal fun detectAdTransition() {
        if (player.isPlayingAd) {
            val current = adContextFromPlayer()
            val previous = activeAd
            if (previous != null && !previous.isSameAdAs(current)) {
                finishAdForTransition(completed = false)
            }
            if (activeAd == null) {
                activeAd = current
                emit(PlayerEvent.AdStarted(current))
            }
        } else if (activeAd != null) {
            finishAdForTransition(completed = false)
        }
    }

    internal fun finishAdForTransition(
        completed: Boolean,
        positionMillis: Long? = null,
    ) {
        val ad = activeAd ?: return
        activeAd = null
        val finalAd = positionMillis?.let { ad.copy(positionMillis = it) } ?: ad
        if (completed) {
            emit(PlayerEvent.AdStopped(finalAd, completed = true))
        } else {
            emit(PlayerEvent.AdSkipped(finalAd))
        }
    }

    private suspend fun attach() {
        withContext(playerDispatcher) {
            mutex.withLock {
                if (observing) return@withLock
                observing = true
                player.addListener(this@Media3PlayerObserver)
                if (player.isPlaying) {
                    emit(PlayerEvent.Playing)
                }
                onPlaybackStateChanged(player.playbackState)
                detectAdTransition()
            }
        }
    }

    private suspend fun detach() {
        withContext(playerDispatcher) {
            mutex.withLock {
                if (observing) {
                    observing = false
                    player.removeListener(this@Media3PlayerObserver)
                }
                activeAd = null
                cancelBuffering()
            }
        }
    }

    private fun emit(event: PlayerEvent) {
        _eventFlow.tryEmit(event)
    }

    private fun startBufferingDebounce() {
        if (bufferingJob?.isActive == true) return
        bufferingJob =
            scope.launch {
                delay(BUFFERING_DEBOUNCE_MILLIS.milliseconds)
                emit(PlayerEvent.Buffering)
            }
    }

    private fun cancelBuffering() {
        bufferingJob?.cancel()
        bufferingJob = null
    }

    private fun adContextFromPlayer(): AdContext =
        AdContext(
            adGroupIndex = player.currentAdGroupIndex,
            adIndexInAdGroup = player.currentAdIndexInAdGroup,
            positionMillis = player.currentPosition.coerceAtLeast(0),
            durationMillis = player.duration,
            contentPositionMillis = player.contentPosition.coerceAtLeast(0),
            contentId = player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() },
        )
}

private fun AdContext.isSameAdAs(other: AdContext): Boolean =
    adGroupIndex == other.adGroupIndex && adIndexInAdGroup == other.adIndexInAdGroup

internal fun Player.createPlayerDispatcher(): CoroutineDispatcher {
    return Handler(applicationLooper).asCoroutineDispatcher()
}

private fun Player.mediaType(): MediaType {
    val groups = currentTracks.groups
    val hasVideo = groups.any { it.isType(C.TRACK_TYPE_VIDEO) }
    val hasAudio = groups.any { it.isType(C.TRACK_TYPE_AUDIO) }
    return when {
        hasVideo -> MediaType.VIDEO
        hasAudio -> MediaType.AUDIO
        else -> MediaType.VIDEO
    }
}

private fun Tracks.Group.isType(trackType: Int): Boolean = type == trackType
