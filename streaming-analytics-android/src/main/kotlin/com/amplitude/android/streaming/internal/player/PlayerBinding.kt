package com.amplitude.android.streaming.internal.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.PlayerContentProvider
import com.amplitude.android.streaming.internal.AdContext
import com.amplitude.android.streaming.internal.StopReason
import com.amplitude.android.streaming.internal.StreamTracker
import com.amplitude.android.streaming.internal.util.Time
import com.amplitude.android.streaming.internal.util.runCatchingCancellable
import com.amplitude.core.AmplitudePreview
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

private const val ORPHAN_CHECK_MILLIS = 1_000L

/**
 * Turns player events into stream sessions, Stream Started, and ad events.
 */
@OptIn(AmplitudePreview::class)
internal class PlayerBinding internal constructor(
    player: Player,
    private val contentProvider: PlayerContentProvider,
    playerObserverFactory: PlayerObserverFactory,
    private val streamTracker: StreamTracker,
    private val heartbeatFactory: HeartbeatFactory,
    private val time: Time,
    parentScope: CoroutineScope,
    playerDispatcher: CoroutineDispatcher,
    private val onStopped: (PlayerBinding) -> Unit = {},
) {
    private val playerReference = WeakReference(player)
    private val scope =
        CoroutineScope(
            playerDispatcher +
                SupervisorJob(parentScope.coroutineContext[Job]),
        )
    private val mutex = Mutex()
    private val observer =
        playerObserverFactory.create(
            player = player,
            parentScope = scope,
            playerDispatcher = playerDispatcher,
        )
    private var eventJob: Job? = null
    private var playback: PlaybackState = PlaybackState.Idle()
    private val started = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)

    // TODO: wire picture-in-picture and background from the host app.
    private val playerState = PlayerState()

    private var options: PlayerContent = PlayerContent()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            runCatchingCancellable {
                if (stopped.get() || eventJob != null) return@runCatchingCancellable
                options = resolveOptions(playerReference.get()?.currentMediaItem)
                eventJob =
                    scope.launch {
                        observer.eventFlow.collect { event ->
                            if (stopped.get()) return@collect
                            mutex.withLock {
                                if (stopped.get()) return@withLock
                                runCatchingCancellable { handlePlayerEvent(event) }
                            }
                        }
                    }
            }
        }
        scope.launch {
            while (!stopped.get()) {
                delay(ORPHAN_CHECK_MILLIS.milliseconds)
                if (isOrphaned()) stop()
            }
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        val rewriteIdleLastSegment = !isOrphaned()
        eventJob?.cancel()
        eventJob = null
        // Independent of the graph job so teardown's scope.cancel() cannot drop finishAd.
        val cleanupScope = CoroutineScope(scope.coroutineContext.minusKey(Job))
        cleanupScope.launch {
            try {
                mutex.withLock {
                    finishPlayback(
                        StopReason.UNTRACKED,
                        rewriteIdleLastSegment = rewriteIdleLastSegment,
                    )
                }
            } finally {
                this@PlayerBinding.scope.cancel()
                cleanupScope.cancel()
                onStopped(this@PlayerBinding)
            }
        }
    }

    private suspend fun handlePlayerEvent(event: PlayerEvent) {
        if (isOrphaned()) {
            stop()
            return
        }
        when (event) {
            PlayerEvent.Playing -> onPlaying()
            PlayerEvent.Paused -> onPaused()
            PlayerEvent.Buffering -> onBuffering()
            PlayerEvent.Ready -> onReady()
            PlayerEvent.Ended -> finishSession(StopReason.COMPLETED)
            PlayerEvent.Seeking -> onSeeking()
            is PlayerEvent.Error -> finishSession(StopReason.ERROR, event.message)
            is PlayerEvent.MediaChanged -> {
                finishSession(null)
                options = resolveOptions(event.mediaItem)
                if (playerReference.get()?.isPlaying == true) onPlaying()
            }
            is PlayerEvent.AdStarted -> onAdStarted(event.ad)
            is PlayerEvent.AdStopped -> finishAd(event.ad, completed = event.completed)
            is PlayerEvent.AdSkipped -> skipAd(event.ad)
        }
    }

    private suspend fun onPlaying() {
        val player = playerReference.get()
        val current = playback
        if (current is PlaybackState.Ad) {
            playback =
                if (player?.isPlayingAd == true) {
                    resumeAdWatch(current)
                } else {
                    current.copy(paused = false)
                }
            return
        }
        if (player?.isPlayingAd == true) return
        when (current) {
            is PlaybackState.Content -> {
                current.segment.resumeWatch()
                current.segment.stopReason = null
                current.segment.errorMessage = null
                playback = current.copy(phase = ContentPhase.PLAYING)
            }
            is PlaybackState.Suspended -> resumeContent(current)
            is PlaybackState.Idle -> startContent(current.viewSessionId)
            is PlaybackState.Ad -> Unit
        }
    }

    private suspend fun startContent(viewSessionId: String?) {
        val id = viewSessionId ?: newViewSessionId()
        val snapshot = snapshot() ?: return
        val playId = UUID.randomUUID().toString()
        val segment =
            StreamSession(
                streamSessionId = id,
                startedInsertId = UUID.randomUUID().toString(),
                stoppedInsertId = UUID.randomUUID().toString(),
                playId = playId,
                startTimeMillis = snapshot.positionMillis,
                options = options,
                mediaType = snapshot.mediaType,
                snapshot = snapshot,
                time = time,
            ).also { it.resumeWatch() }
        streamTracker.trackStreamStarted(
            options = segment.options,
            snapshot = snapshot,
            playerState = playerState,
            mediaType = segment.mediaType,
            streamSessionId = id,
            playId = segment.playId,
            startTimeMillis = segment.startTimeMillis,
            timestamp = time.nowMillis(),
            insertId = segment.startedInsertId,
        )
        playback =
            PlaybackState.Content(
                viewSessionId = id,
                segment = segment,
                heartbeat = createHeartbeat(segment),
                phase = ContentPhase.PLAYING,
            )
    }

    private suspend fun onPaused() {
        when (val state = playback) {
            is PlaybackState.Content -> {
                finishSegment(state.segment, state.heartbeat, StopReason.PAUSED)
                playback = PlaybackState.Idle(state.viewSessionId, state.segment)
            }
            is PlaybackState.Suspended -> {
                finishSegment(state.segment, heartbeat = null, reason = StopReason.PAUSED)
                playback = PlaybackState.Idle(state.viewSessionId, state.segment)
            }
            is PlaybackState.Ad -> {
                state.content?.segment?.apply {
                    pauseWatch()
                    stopReason = StopReason.PAUSED
                }
                playback = pauseAdWatch(state)
            }
            is PlaybackState.Idle -> Unit
        }
    }

    private fun onBuffering() {
        interruptContent(ContentPhase.WAITING, StopReason.WAITING)
    }

    private fun onSeeking() {
        interruptContent(ContentPhase.SEEKING, StopReason.SEEKING)
    }

    private fun interruptContent(
        phase: ContentPhase,
        reason: StopReason,
    ) {
        when (val state = playback) {
            is PlaybackState.Content -> {
                state.segment.pauseWatch()
                state.segment.stopReason = reason
                playback = state.copy(phase = phase)
            }
            is PlaybackState.Suspended -> {
                state.segment.pauseWatch()
                state.segment.stopReason = reason
                playback = state.copy(phase = phase)
            }
            is PlaybackState.Ad -> {
                state.content?.segment?.apply {
                    pauseWatch()
                    stopReason = reason
                }
                playback = state.copy(content = state.content?.copy(phase = phase))
            }
            is PlaybackState.Idle -> Unit
        }
    }

    private fun onReady() {
        when (val state = playback) {
            is PlaybackState.Content -> {
                if (state.phase == ContentPhase.PLAYING) return
                state.segment.stopReason = null
                state.segment.resumeWatch()
                playback = state.copy(phase = ContentPhase.PLAYING)
            }
            is PlaybackState.Suspended -> {
                state.segment.stopReason = null
                playback = state.copy(phase = ContentPhase.PLAYING)
            }
            else -> Unit
        }
    }

    private suspend fun finishSession(
        reason: StopReason?,
        errorMessage: String? = null,
    ) {
        finishPlayback(reason, errorMessage)
        playback = PlaybackState.Idle()
    }

    private suspend fun onAdStarted(ad: AdContext) {
        val current = playback
        if (current is PlaybackState.Ad) {
            trackAdFinished(current, current.ad, completed = false)
        }
        val content =
            when (current) {
                is PlaybackState.Content -> {
                    current.segment.pauseWatch()
                    current.heartbeat.cancel()
                    PlaybackState.Suspended(
                        viewSessionId = current.viewSessionId,
                        segment = current.segment,
                        phase = current.phase,
                    )
                }
                is PlaybackState.Suspended -> current
                is PlaybackState.Ad -> current.content
                is PlaybackState.Idle -> null
            }
        val id = current.viewSessionId ?: newViewSessionId()
        playback =
            PlaybackState.Ad(
                viewSessionId = id,
                ad = ad,
                watchStartedAt = time.elapsedRealtime(),
                content = content,
            )
        streamTracker.trackAdStarted(options, ad, id)
    }

    private suspend fun finishAd(
        ad: AdContext,
        completed: Boolean,
    ) {
        val state = playback as? PlaybackState.Ad ?: return
        trackAdFinished(state, ad, completed)
        continueAfterAd(state)
    }

    private suspend fun skipAd(ad: AdContext) {
        val state = playback as? PlaybackState.Ad ?: return
        streamTracker.trackAdSkipped(options, ad, state.viewSessionId)
        continueAfterAd(state)
    }

    private fun trackAdFinished(
        state: PlaybackState.Ad,
        ad: AdContext,
        completed: Boolean,
    ) {
        val watchDuration = adWatchDurationMillis(pauseAdWatch(state))
        streamTracker.trackAdStopped(options, ad, state.viewSessionId, watchDuration, completed)
    }

    private suspend fun continueAfterAd(state: PlaybackState.Ad) {
        val content = state.content
        val player = playerReference.get()
        val playingContent = player != null && player.isPlaying && !player.isPlayingAd
        if (content == null) {
            playback = PlaybackState.Idle(state.viewSessionId)
            if (playingContent) startContent(state.viewSessionId)
            return
        }
        if (state.paused) {
            finishSegment(content.segment, heartbeat = null, reason = StopReason.PAUSED)
            playback = PlaybackState.Idle(state.viewSessionId, content.segment)
            return
        }
        playback = content
        if (playingContent) resumeContent(content)
    }

    private fun pauseAdWatch(state: PlaybackState.Ad): PlaybackState.Ad {
        val startedAt = state.watchStartedAt ?: return state.copy(paused = true)
        return state.copy(
            paused = true,
            watchStartedAt = null,
            accumulatedWatchMillis =
                state.accumulatedWatchMillis +
                    (time.elapsedRealtime() - startedAt).coerceAtLeast(0),
        )
    }

    private fun resumeAdWatch(state: PlaybackState.Ad): PlaybackState.Ad {
        if (state.watchStartedAt != null) return state.copy(paused = false)
        return state.copy(paused = false, watchStartedAt = time.elapsedRealtime())
    }

    private fun adWatchDurationMillis(state: PlaybackState.Ad): Long {
        val running =
            state.watchStartedAt?.let { (time.elapsedRealtime() - it).coerceAtLeast(0) } ?: 0
        return state.accumulatedWatchMillis + running
    }

    private fun resumeContent(state: PlaybackState.Suspended) {
        state.segment.resumeWatch()
        state.segment.stopReason = null
        state.segment.errorMessage = null
        playback =
            PlaybackState.Content(
                viewSessionId = state.viewSessionId,
                segment = state.segment,
                heartbeat = createHeartbeat(state.segment),
                phase = ContentPhase.PLAYING,
            )
    }

    private fun createHeartbeat(segment: StreamSession): Heartbeat =
        heartbeatFactory
            .create(
                scope = scope,
                stoppedEvent = { timestamp, isFinal ->
                    sendStreamStopped(
                        segment = segment,
                        timestamp = timestamp,
                        stopReason = if (isFinal) segment.stopReason else StopReason.TIMEOUT,
                    )
                },
            ).also { it.start() }

    private suspend fun finishPlayback(
        reason: StopReason?,
        errorMessage: String? = null,
        rewriteIdleLastSegment: Boolean = true,
    ) {
        when (val state = playback) {
            is PlaybackState.Content -> {
                finishSegment(state.segment, state.heartbeat, reason, errorMessage)
            }
            is PlaybackState.Suspended -> {
                finishSegment(
                    segment = state.segment,
                    heartbeat = null,
                    reason = reason,
                    errorMessage = errorMessage,
                )
            }
            is PlaybackState.Ad -> {
                trackAdFinished(state, state.ad, completed = false)
                state.content?.let {
                    finishSegment(
                        segment = it.segment,
                        heartbeat = null,
                        reason = reason,
                        errorMessage = errorMessage,
                    )
                }
            }
            is PlaybackState.Idle -> {
                if (reason == StopReason.UNTRACKED && rewriteIdleLastSegment) {
                    state.lastSegment?.let {
                        it.stopReason = reason
                        it.errorMessage = errorMessage
                        sendStreamStopped(it, time.nowMillis())
                    }
                }
            }
        }
    }

    private suspend fun finishSegment(
        segment: StreamSession,
        heartbeat: Heartbeat?,
        reason: StopReason?,
        errorMessage: String? = null,
    ) {
        segment.pauseWatch()
        segment.stopReason = reason
        segment.errorMessage = errorMessage
        freezeSegment(segment)
        if (heartbeat == null) {
            sendFinalStreamStopped(segment)
        } else {
            runCatchingCancellable {
                heartbeat.stop()
            }
        }
    }

    private suspend fun sendFinalStreamStopped(segment: StreamSession) {
        runCatchingCancellable {
            sendStreamStopped(segment, time.nowMillis())
        }
    }

    private suspend fun freezeSegment(segment: StreamSession) {
        segment.freeze(snapshot() ?: segment.snapshot)
    }

    private suspend fun sendStreamStopped(
        segment: StreamSession,
        timestamp: Long,
        stopReason: StopReason? = segment.stopReason,
    ) {
        val snapshot =
            if (segment.frozen || isOrphaned()) {
                if (!segment.frozen) segment.freeze(segment.snapshot)
                segment.snapshot
            } else {
                snapshot()?.also { segment.updateSnapshot(it) } ?: segment.snapshot
            }
        streamTracker.trackStreamStopped(
            options = segment.options,
            snapshot = snapshot,
            playerState = playerState,
            mediaType = segment.mediaType,
            streamSessionId = segment.streamSessionId,
            playId = segment.playId,
            startTimeMillis = segment.startTimeMillis,
            streamDurationMillis = segment.durationMillis(),
            timestamp = timestamp,
            insertId = segment.stoppedInsertId,
            stopReason = stopReason,
            errorMessage = segment.errorMessage,
        )
    }

    private suspend fun snapshot(): PlayerMediaSnapshot? =
        runCatchingCancellable { observer.snapshot() }.getOrNull()

    internal fun isBoundTo(player: Player): Boolean = playerReference.get() === player

    internal fun isOrphaned(): Boolean = playerReference.get() == null

    private fun newViewSessionId(): String = UUID.randomUUID().toString()

    private fun resolveOptions(mediaItem: MediaItem?): PlayerContent =
        try {
            contentProvider.optionsFor(mediaItem)
        } catch (_: Exception) {
            PlayerContent()
        }

    private enum class ContentPhase {
        PLAYING,
        WAITING,
        SEEKING,
    }

    private sealed interface PlaybackState {
        val viewSessionId: String?

        data class Idle(
            override val viewSessionId: String? = null,
            val lastSegment: StreamSession? = null,
        ) : PlaybackState

        data class Content(
            override val viewSessionId: String,
            val segment: StreamSession,
            val heartbeat: Heartbeat,
            val phase: ContentPhase,
        ) : PlaybackState

        data class Suspended(
            override val viewSessionId: String,
            val segment: StreamSession,
            val phase: ContentPhase,
        ) : PlaybackState

        data class Ad(
            override val viewSessionId: String,
            val ad: AdContext,
            val content: Suspended?,
            val watchStartedAt: Long? = null,
            val accumulatedWatchMillis: Long = 0,
            val paused: Boolean = false,
        ) : PlaybackState
    }
}
