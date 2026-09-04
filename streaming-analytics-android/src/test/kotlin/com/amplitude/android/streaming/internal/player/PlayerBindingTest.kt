package com.amplitude.android.streaming.internal.player

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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val STREAM_STARTED = "[Amplitude] Stream Started"
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
    }

    @Nested
    inner class Ads {
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

    private fun TestScope.withBinding(block: () -> Unit) {
        val binding =
            PlayerBinding(
                player = mockk(relaxed = true),
                contentProvider = { PlayerContent() },
                playerObserverFactory = PlayerObserverFactory { _, _ -> observer },
                streamTracker = StreamTracker(amplitude),
                time = Time(),
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
