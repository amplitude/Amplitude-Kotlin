package com.amplitude.android.streaming.internal.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.PlayerContentProvider
import com.amplitude.android.streaming.internal.AdContext
import com.amplitude.android.streaming.internal.StopReason
import com.amplitude.android.streaming.internal.StreamTracker
import com.amplitude.android.streaming.internal.util.Time
import com.amplitude.core.AmplitudePreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(AmplitudePreview::class)
internal class PlayerBinding internal constructor(
    val player: Player,
    private val contentProvider: PlayerContentProvider,
    playerObserverFactory: PlayerObserverFactory,
    private val streamTracker: StreamTracker,
    private val time: Time,
    parentScope: CoroutineScope,
) {
    private val scope =
        CoroutineScope(
            parentScope.coroutineContext +
                SupervisorJob(parentScope.coroutineContext[Job]),
        )
    private val mutex = Mutex()
    private val observer =
        playerObserverFactory.create(
            player = player,
            parentScope = scope,
        )
    private var eventJob: Job? = null
    private var streamActive = false
    private var session: StreamSession? = null
    private var viewSessionId: String? = null
    private var activeAd: AdContext? = null
    private var adWatchStartedAt: Long? = null
    private val stopped = AtomicBoolean(false)

    // TODO: wire picture-in-picture and background from the host app.
    private val playerState = PlayerState()

    private var options: PlayerContent = resolveOptions(null)

    fun start() {
        if (eventJob != null) return
        eventJob =
            scope.launch {
                observer.eventFlow.collect { event ->
                    if (stopped.get()) return@collect
                    mutex.withLock {
                        if (stopped.get()) return@withLock
                        handlePlayerEvent(event)
                    }
                }
            }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        eventJob?.cancel()
        eventJob = null
        // Independent of the graph job so teardown's scope.cancel() cannot drop finishAd.
        val cleanupScope = CoroutineScope(scope.coroutineContext.minusKey(Job))
        cleanupScope.launch {
            try {
                mutex.withLock {
                    session?.pauseWatch()
                    session?.stopReason = StopReason.UNSUBSCRIBED
                    finishAd(completed = false)
                    streamActive = false
                    viewSessionId = null
                    session = null
                }
            } finally {
                this@PlayerBinding.scope.cancel()
                cleanupScope.cancel()
            }
        }
    }

    private suspend fun handlePlayerEvent(event: PlayerEvent) {
        when (event) {
            PlayerEvent.Playing -> onPlaying()
            PlayerEvent.Paused -> onPaused()
            PlayerEvent.Buffering -> onBuffering()
            PlayerEvent.Ready -> Unit
            PlayerEvent.Ended -> finishSession(StopReason.COMPLETED)
            PlayerEvent.Seeking -> {
                session?.stopReason = StopReason.SEEKING
            }
            is PlayerEvent.Error -> {
                session?.errorMessage = event.message
                finishSession(StopReason.ERROR)
            }
            is PlayerEvent.MediaChanged -> {
                finishSession(null)
                options = resolveOptions(event.mediaItem)
            }
            is PlayerEvent.AdStarted -> onAdStarted(event.ad)
            is PlayerEvent.AdStopped -> onAdStopped(event.ad, event.completed)
            is PlayerEvent.AdSkipped -> onAdSkipped(event.ad)
        }
    }

    private suspend fun onPlaying() {
        session?.resumeWatch()
        session?.stopReason = null
        session?.errorMessage = null
        if (streamActive) return
        streamActive = true
        val id = viewSessionId ?: newViewSessionId().also { viewSessionId = it }
        val snapshot = snapshot()
        val streamSession =
            StreamSession(
                streamSessionId = id,
                startedInsertId = UUID.randomUUID().toString(),
                options = options,
                mediaType = snapshot.mediaType,
                time = time,
            ).also { it.resumeWatch() }
        session = streamSession
        streamTracker.trackStreamStarted(
            options = streamSession.options,
            snapshot = snapshot,
            playerState = playerState,
            mediaType = streamSession.mediaType,
            streamSessionId = id,
            timestamp = time.nowMillis(),
            insertId = streamSession.startedInsertId,
        )
        // TODO: heartbeat a delayed Stream Stopped event while this stream stays active.
    }

    private fun onPaused() {
        session?.pauseWatch()
        session?.stopReason = StopReason.PAUSED
        streamActive = false
    }

    private fun onBuffering() {
        session?.pauseWatch()
        session?.stopReason = StopReason.WAITING
    }

    private fun finishSession(reason: StopReason?) {
        session?.pauseWatch()
        finishAd(completed = false)
        session?.stopReason = reason
        streamActive = false
        session = null
        viewSessionId = null
    }

    private fun onAdStarted(ad: AdContext) {
        val id = viewSessionId ?: newViewSessionId().also { viewSessionId = it }
        activeAd = ad
        adWatchStartedAt = time.elapsedRealtime()
        streamTracker.trackAdStarted(options, ad, id)
    }

    private fun onAdStopped(
        ad: AdContext,
        completed: Boolean,
    ) {
        val id = viewSessionId ?: return
        val watchDuration = adWatchStartedAt?.let { time.elapsedRealtime() - it }?.coerceAtLeast(0) ?: 0
        streamTracker.trackAdStopped(options, ad, id, watchDuration, completed)
        activeAd = null
        adWatchStartedAt = null
    }

    private fun onAdSkipped(ad: AdContext) {
        val id = viewSessionId ?: return
        if (activeAd == null) return
        streamTracker.trackAdSkipped(options, ad, id)
        activeAd = null
        adWatchStartedAt = null
    }

    private fun finishAd(completed: Boolean) {
        activeAd?.let { onAdStopped(it, completed) }
    }

    private suspend fun snapshot(): PlayerMediaSnapshot = observer.snapshot()

    private fun newViewSessionId(): String = UUID.randomUUID().toString()

    private fun resolveOptions(mediaItem: MediaItem?): PlayerContent =
        try {
            contentProvider.optionsFor(mediaItem)
        } catch (_: Exception) {
            PlayerContent()
        }
}
