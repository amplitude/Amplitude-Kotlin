package com.amplitude.android.streaming.internal.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.internal.AdContext
import com.amplitude.android.streaming.internal.StreamTracker
import com.amplitude.android.streaming.internal.util.Time
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import com.amplitude.core.events.BaseEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val STREAM_STARTED = "[Amplitude] Stream Started"
private const val STREAM_STOPPED = "[Amplitude] Stream Stopped"
private const val AD_STARTED = "[Amplitude] Ad Started"
private const val AD_SKIPPED = "[Amplitude] Ad Skipped"
private const val AD_STOPPED = "[Amplitude] Ad Stopped"
private const val STREAM_SESSION_ID = "stream_session_id"

@OptIn(AmplitudePreview::class, ExperimentalCoroutinesApi::class)
class PlayerBindingTest {
    private val tracked = mutableListOf<BaseEvent>()
    private val amplitude =
        mockk<Amplitude>(relaxed = true).also { amplitude ->
            every { amplitude.track(any<BaseEvent>(), any(), any()) } answers {
                tracked.add(firstArg())
                amplitude
            }
            every { amplitude.track(any<String>(), any(), any()) } answers {
                tracked.add(
                    BaseEvent().apply {
                        eventType = firstArg()
                        eventProperties = secondArg<Map<String, Any?>?>()?.toMutableMap()
                    },
                )
                amplitude
            }
        }
    private val observer = TestPlayerObserver()

    @Nested
    inner class StreamStarted {
        @Test
        fun `should track a single Stream Started while playback continues`() =
            runTest {
                withBinding {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()

                    assertEquals(1, startedEvents().size)
                }
            }

        @Test
        fun `should resolve PlayerContent from the current media item when tracking starts`() =
            runTest {
                val mediaItem =
                    MediaItem.Builder()
                        .setMediaId("ep-1")
                        .setUri("https://example.com/ep-1")
                        .build()
                val player = mockk<Player>(relaxed = true)
                every { player.currentMediaItem } returns mediaItem
                withBinding(
                    player = player,
                    contentProvider = { item -> PlayerContent(contentId = item?.mediaId) },
                ) {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()

                    assertEquals(
                        "ep-1",
                        startedEvents().single().eventProperties?.get("content_id"),
                    )
                }
            }

        @Test
        fun `should keep the stream session but rotate the insert id across a pause`() =
            runTest {
                withBinding {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.Paused)
                    runCurrent()
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()

                    val started = startedEvents()
                    assertEquals(2, started.size)
                    assertNotEquals(started[0].insertId, started[1].insertId)
                    assertEquals(
                        started[0].eventProperties?.get(STREAM_SESSION_ID),
                        started[1].eventProperties?.get(STREAM_SESSION_ID),
                    )
                }
            }

        @Test
        fun `should start a new stream session when the media item changes`() =
            runTest {
                withBinding {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.MediaChanged(null))
                    runCurrent()
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()

                    val started = startedEvents()
                    assertEquals(2, started.size)
                    assertNotEquals(
                        started[0].eventProperties?.get(STREAM_SESSION_ID),
                        started[1].eventProperties?.get(STREAM_SESSION_ID),
                    )
                }
            }
    }

    @Nested
    inner class Heartbeat {
        @Test
        fun `should keep accumulated watch duration on the terminal Stream Stopped`() =
            runTest {
                var elapsed = 0L
                val time = mockk<Time>()
                every { time.elapsedRealtime() } answers { elapsed }
                every { time.nowMillis() } answers { elapsed }

                withBinding(time = time) {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    elapsed = 5_000L
                    observer.emit(PlayerEvent.Ended)
                    runCurrent()

                    val stopped = tracked.filter { it.eventType == STREAM_STOPPED }
                    assertEquals(5.0, stopped.last().eventProperties?.get("stream_duration"))
                    assertEquals("completed", stopped.last().eventProperties?.get("stop_reason"))
                }
            }

        @Test
        fun `should not let a stale heartbeat overwrite the resumed Stream Stopped`() =
            runTest {
                var elapsed = 0L
                val time = mockk<Time>()
                every { time.elapsedRealtime() } answers { elapsed }
                every { time.nowMillis() } answers { elapsed }

                withBinding(time = time) {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    elapsed = 5_000L
                    observer.emit(PlayerEvent.Paused)
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    elapsed = 15_000L
                    advanceTimeBy(1_000)
                    runCurrent()

                    val stopped = tracked.filter { it.eventType == STREAM_STOPPED }
                    val firstInsertId = stopped.first().insertId
                    val firstPlayStops = stopped.filter { it.insertId == firstInsertId }
                    assertEquals(5.0, firstPlayStops.last().eventProperties?.get("stream_duration"))
                    assertEquals("paused", firstPlayStops.last().eventProperties?.get("stop_reason"))
                    assertTrue(
                        stopped.any {
                            it.insertId != firstInsertId &&
                                (it.eventProperties?.get("stream_duration") as Double) > 5.0
                        },
                    )
                }
            }

        @Test
        fun `should stop the heartbeat job even if freeze snapshot fails`() =
            runTest {
                withBinding {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.snapshotError = IllegalStateException("snapshot failed")
                    observer.emit(PlayerEvent.Paused)
                    runCurrent()
                    val stoppedAfterPause = tracked.count { it.eventType == STREAM_STOPPED }
                    observer.snapshotError = null

                    advanceTimeBy(2_000)
                    runCurrent()

                    assertEquals(stoppedAfterPause, tracked.count { it.eventType == STREAM_STOPPED })
                    assertTrue(stoppedAfterPause >= 1)

                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    val started = startedEvents()
                    assertEquals(2, started.size)
                    assertNotEquals(started[0].insertId, started[1].insertId)
                }
            }

        @Test
        fun `should freeze the last heartbeat snapshot if freeze snapshot fails`() =
            runTest {
                withBinding {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.positionMillis = 5_000L
                    advanceTimeBy(1_000)
                    runCurrent()
                    observer.snapshotError = IllegalStateException("snapshot failed")
                    observer.emit(PlayerEvent.Paused)
                    runCurrent()

                    val stopped = tracked.filter { it.eventType == STREAM_STOPPED }
                    assertEquals(5.0, stopped.last().eventProperties?.get("current_time"))
                }
            }

        @Test
        fun `should not keep stop_reason seeking on heartbeats after playback is ready`() =
            runTest {
                withBinding {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.Seeking)
                    runCurrent()
                    observer.emit(PlayerEvent.Ready)
                    runCurrent()
                    advanceTimeBy(1_000)
                    runCurrent()

                    val afterReady =
                        tracked.filter { it.eventType == STREAM_STOPPED }.last()
                    assertEquals(null, afterReady.eventProperties?.get("stop_reason"))
                }
            }
    }

    @Nested
    inner class Stop {
        @Test
        fun `should not track Stream Started from an event queued before stop`() =
            runTest {
                val binding =
                    PlayerBinding(
                        player = mockk(relaxed = true),
                        contentProvider = { PlayerContent() },
                        playerObserverFactory = PlayerObserverFactory { _, _ -> observer },
                        streamTracker = StreamTracker(amplitude),
                        heartbeatFactory = HeartbeatFactory(time = Time()),
                        time = Time(),
                        parentScope = this,
                    )
                binding.start()
                runCurrent()
                try {
                    observer.emit(PlayerEvent.Playing)
                    binding.stop()
                    runCurrent()

                    assertEquals(0, startedEvents().size)
                } finally {
                    binding.stop()
                    runCurrent()
                }
            }

        @Test
        fun `should track Ad Stopped when the graph scope is cancelled during stop`() =
            runTest {
                val parentJob = SupervisorJob()
                val binding =
                    PlayerBinding(
                        player = mockk(relaxed = true),
                        contentProvider = { PlayerContent() },
                        playerObserverFactory = PlayerObserverFactory { _, _ -> observer },
                        streamTracker = StreamTracker(amplitude),
                        heartbeatFactory = HeartbeatFactory(time = Time()),
                        time = Time(),
                        parentScope = CoroutineScope(coroutineContext + parentJob),
                    )
                binding.start()
                runCurrent()
                try {
                    observer.emit(
                        PlayerEvent.AdStarted(
                            AdContext(
                                adGroupIndex = 0,
                                adIndexInAdGroup = 0,
                                positionMillis = 0L,
                                durationMillis = 15_000L,
                                contentPositionMillis = 1_000L,
                                contentId = "media-1",
                            ),
                        ),
                    )
                    runCurrent()
                    binding.stop()
                    parentJob.cancel()
                    runCurrent()

                    assertEquals(1, tracked.count { it.eventType == AD_STOPPED })
                } finally {
                    binding.stop()
                    runCurrent()
                }
            }

        @Test
        fun `should upsert delayed Stream Stopped as unsubscribed after pause`() =
            runTest {
                val binding =
                    PlayerBinding(
                        player = mockk(relaxed = true),
                        contentProvider = { PlayerContent() },
                        playerObserverFactory = PlayerObserverFactory { _, _ -> observer },
                        streamTracker = StreamTracker(amplitude),
                        heartbeatFactory = HeartbeatFactory(time = Time()),
                        time = Time(),
                        parentScope = this,
                    )
                binding.start()
                runCurrent()
                try {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.Paused)
                    runCurrent()

                    val paused = tracked.filter { it.eventType == STREAM_STOPPED }
                    assertTrue(paused.isNotEmpty())
                    assertEquals("paused", paused.last().eventProperties?.get("stop_reason"))
                    val insertId = paused.last().insertId

                    binding.stop()
                    runCurrent()

                    val samePlay = tracked.filter { it.eventType == STREAM_STOPPED && it.insertId == insertId }
                    assertEquals("unsubscribed", samePlay.last().eventProperties?.get("stop_reason"))
                } finally {
                    binding.stop()
                    runCurrent()
                }
            }
    }

    @Nested
    inner class Ads {
        @Test
        fun `should not track Stream Started while an ad is playing`() =
            runTest {
                val player = mockk<Player>(relaxed = true)
                every { player.isPlayingAd } returns true
                withBinding(player) {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.AdStarted(testAd()))
                    runCurrent()

                    assertEquals(0, startedEvents().size)
                    assertEquals(1, tracked.count { it.eventType == AD_STARTED })
                }
            }

        @Test
        fun `should track Stream Started after an ad completes while content is playing`() =
            runTest {
                val player = mockk<Player>(relaxed = true)
                every { player.isPlayingAd } returns true
                every { player.isPlaying } returns true
                withBinding(player) {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.AdStarted(testAd()))
                    runCurrent()
                    every { player.isPlayingAd } returns false
                    observer.emit(PlayerEvent.AdStopped(testAd(), completed = true))
                    runCurrent()

                    assertEquals(1, startedEvents().size)
                    assertEquals(
                        tracked.single { it.eventType == AD_STARTED }.eventProperties?.get(STREAM_SESSION_ID),
                        startedEvents().single().eventProperties?.get(STREAM_SESSION_ID),
                    )
                }
            }

        @Test
        fun `should not upsert content Stream Stopped as paused during an ad`() =
            runTest {
                val player = mockk<Player>(relaxed = true)
                every { player.isPlaying } returns true
                withBinding(player) {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.AdStarted(testAd()))
                    runCurrent()
                    val stoppedBeforePause = tracked.count { it.eventType == STREAM_STOPPED }
                    observer.emit(PlayerEvent.Paused)
                    runCurrent()

                    assertEquals(stoppedBeforePause, tracked.count { it.eventType == STREAM_STOPPED })
                    assertTrue(
                        tracked.none {
                            it.eventType == STREAM_STOPPED &&
                                it.eventProperties?.get("stop_reason") == "paused"
                        },
                    )
                }
            }

        @Test
        fun `should finish a suspended content segment when an ad ends paused`() =
            runTest {
                val player = mockk<Player>(relaxed = true)
                every { player.isPlaying } returns true
                withBinding(player) {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.AdStarted(testAd()))
                    runCurrent()
                    observer.emit(PlayerEvent.Paused)
                    runCurrent()
                    every { player.isPlaying } returns false
                    observer.emit(PlayerEvent.AdStopped(testAd(), completed = true))
                    runCurrent()

                    val firstStart = startedEvents().single()
                    val pausedStop =
                        tracked
                            .filter { it.eventType == STREAM_STOPPED }
                            .last()
                    assertEquals("paused", pausedStop.eventProperties?.get("stop_reason"))

                    every { player.isPlaying } returns true
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()

                    val starts = startedEvents()
                    assertEquals(2, starts.size)
                    assertNotEquals(firstStart.insertId, starts.last().insertId)
                    assertEquals(
                        firstStart.eventProperties?.get(STREAM_SESSION_ID),
                        starts.last().eventProperties?.get(STREAM_SESSION_ID),
                    )
                }
            }

        @Test
        fun `should finish the ad and suspended content when playback ends`() =
            runTest {
                val player = mockk<Player>(relaxed = true)
                every { player.isPlaying } returns true
                withBinding(player) {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.AdStarted(testAd()))
                    runCurrent()
                    observer.emit(PlayerEvent.Ended)
                    runCurrent()

                    assertEquals(1, tracked.count { it.eventType == AD_STOPPED })
                    val stopped =
                        tracked
                            .filter { it.eventType == STREAM_STOPPED }
                            .last()
                    assertEquals("completed", stopped.eventProperties?.get("stop_reason"))
                }
            }

        @Test
        fun `should not start a new content stream after a mid-roll ad`() =
            runTest {
                val player = mockk<Player>(relaxed = true)
                every { player.isPlaying } returns true
                withBinding(player) {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.AdStarted(testAd()))
                    runCurrent()
                    observer.emit(PlayerEvent.AdStopped(testAd(), completed = true))
                    runCurrent()

                    assertEquals(1, startedEvents().size)
                }
            }

        @Test
        fun `should not count ad time toward content stream duration`() =
            runTest {
                var elapsed = 0L
                val time = mockk<Time>()
                every { time.elapsedRealtime() } answers { elapsed }
                every { time.nowMillis() } answers { elapsed }
                val player = mockk<Player>(relaxed = true)
                every { player.isPlaying } returns true
                withBinding(player, time) {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    elapsed = 5_000L
                    observer.emit(PlayerEvent.AdStarted(testAd()))
                    runCurrent()
                    elapsed = 15_000L
                    observer.emit(PlayerEvent.AdStopped(testAd(), completed = true))
                    runCurrent()
                    observer.emit(PlayerEvent.Ended)
                    runCurrent()

                    val stopped = tracked.filter { it.eventType == STREAM_STOPPED }
                    assertEquals(5.0, stopped.last().eventProperties?.get("stream_duration"))
                }
            }

        @Test
        fun `should track Ad Skipped for an in-session skip`() =
            runTest {
                withBinding {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.AdStarted(testAd()))
                    runCurrent()
                    observer.emit(PlayerEvent.AdSkipped(testAd()))
                    runCurrent()

                    assertEquals(1, tracked.count { it.eventType == AD_SKIPPED })
                    assertEquals(0, tracked.count { it.eventType == AD_STOPPED })
                    assertEquals(
                        startedEvents().single().eventProperties?.get(STREAM_SESSION_ID),
                        tracked.single { it.eventType == AD_SKIPPED }.eventProperties?.get(STREAM_SESSION_ID),
                    )
                }
            }

        @Test
        fun `should not emit Ad Skipped after the session already finished the ad`() =
            runTest {
                withBinding {
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    observer.emit(PlayerEvent.AdStarted(testAd()))
                    runCurrent()
                    observer.emit(PlayerEvent.MediaChanged(null))
                    runCurrent()
                    observer.emit(PlayerEvent.AdSkipped(testAd()))
                    runCurrent()

                    assertEquals(1, tracked.count { it.eventType == AD_STOPPED })
                    assertEquals(0, tracked.count { it.eventType == AD_SKIPPED })
                }
            }
    }

    private fun startedEvents(): List<BaseEvent> = tracked.filter { it.eventType == STREAM_STARTED }

    private fun testAd() =
        AdContext(
            adGroupIndex = 0,
            adIndexInAdGroup = 0,
            positionMillis = 0L,
            durationMillis = 15_000L,
            contentPositionMillis = 1_000L,
            contentId = "media-1",
        )

    private fun TestScope.withBinding(
        player: Player = mockk(relaxed = true),
        time: Time = Time(),
        contentProvider: (MediaItem?) -> PlayerContent = { PlayerContent() },
        block: () -> Unit,
    ) {
        val binding =
            PlayerBinding(
                player = player,
                contentProvider = contentProvider,
                playerObserverFactory = PlayerObserverFactory { _, _ -> observer },
                streamTracker = StreamTracker(amplitude),
                heartbeatFactory = HeartbeatFactory(time = time),
                time = time,
                parentScope = this,
            )
        binding.start()
        runCurrent()
        try {
            block()
        } finally {
            binding.stop()
            runCurrent()
        }
    }
}
