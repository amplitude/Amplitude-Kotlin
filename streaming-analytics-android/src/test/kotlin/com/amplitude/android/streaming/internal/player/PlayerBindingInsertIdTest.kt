package com.amplitude.android.streaming.internal.player

import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.internal.StreamTracker
import com.amplitude.android.streaming.internal.util.Time
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import com.amplitude.core.events.BaseEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(AmplitudePreview::class, ExperimentalCoroutinesApi::class)
class PlayerBindingInsertIdTest {
    @Nested
    inner class PerPlay {
        @Test
        fun `pause then play uses new insert ids and the same view session`() =
            runTest {
                val events = mutableListOf<BaseEvent>()
                val amplitude = mockk<Amplitude>(relaxed = true)
                every { amplitude.track(any<BaseEvent>(), any(), any()) } answers {
                    events.add(firstArg())
                    amplitude
                }
                val observer = TestPlayerObserver()
                val binding =
                    PlayerBinding(
                        player = mockk(relaxed = true),
                        contentProvider = { PlayerContent() },
                        playerObserverFactory = PlayerObserverFactory { _, _, _ -> observer },
                        streamTracker = StreamTracker(amplitude),
                        heartbeatFactory = HeartbeatFactory(time = Time()),
                        time = Time(),
                        parentScope = this,
                        playerDispatcher = UnconfinedTestDispatcher(testScheduler),
                    )
                binding.start()
                try {
                    runCurrent()
                    observer.emit(PlayerEvent.Playing)
                    runCurrent()
                    advanceTimeBy(1_000)
                    runCurrent()

                    observer.emit(PlayerEvent.Paused)
                    runCurrent()

                    observer.emit(PlayerEvent.Playing)
                    runCurrent()

                    val started = events.filter { it.eventType == "[Amplitude] Stream Started" }
                    val stopped = events.filter { it.eventType == "[Amplitude] Stream Stopped" }
                    assertEquals(2, started.size)
                    assertTrue(stopped.size >= 3)

                    val firstStoppedInsertId = stopped.first().insertId
                    val firstPlayStops = stopped.takeWhile { it.insertId == firstStoppedInsertId }
                    assertTrue(firstPlayStops.size >= 2)
                    assertTrue(stopped.any { it.insertId != firstStoppedInsertId })

                    assertNotEquals(started[0].insertId, firstStoppedInsertId)
                    assertNotEquals(started[0].insertId, started[1].insertId)
                    assertNotEquals(firstStoppedInsertId, stopped.last().insertId)
                    assertNotEquals(started[1].insertId, stopped.last().insertId)

                    val streamSessionId = started[0].eventProperties?.get("stream_session_id")
                    assertEquals(streamSessionId, started[1].eventProperties?.get("stream_session_id"))
                    assertTrue(stopped.all { it.eventProperties?.get("stream_session_id") == streamSessionId })
                } finally {
                    binding.stop()
                    runCurrent()
                }
            }
    }
}
