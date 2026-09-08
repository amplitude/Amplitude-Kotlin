package com.amplitude.android.streaming.internal.player

import com.amplitude.android.streaming.internal.util.Time
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeartbeatTest {
    @Test
    fun `should send immediately and again on the interval`() =
        runTest {
            val timestamps = mutableListOf<Long>()
            var now = 10_000L
            val heartbeat =
                Heartbeat(
                    scope = this,
                    stoppedEvent = { timestamps.add(it) },
                    time = time { now },
                )

            heartbeat.start()
            runCurrent()
            assertEquals(listOf(10_000L), timestamps)

            now = 11_000L
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(listOf(10_000L, 11_000L), timestamps)

            heartbeat.stop()
        }

    @Test
    fun `should timestamp stop with now rather than the last tick`() =
        runTest {
            val timestamps = mutableListOf<Long>()
            var now = 10_000L
            val heartbeat =
                Heartbeat(
                    scope = this,
                    stoppedEvent = { timestamps.add(it) },
                    time = time { now },
                )

            heartbeat.start()
            runCurrent()
            now = 10_500L
            heartbeat.stop()
            runCurrent()

            assertEquals(10_500L, timestamps.last())
        }

    @Test
    fun `should keep sending after a failed heartbeat`() =
        runTest {
            var calls = 0
            val heartbeat =
                Heartbeat(
                    scope = this,
                    stoppedEvent = {
                        calls++
                        if (calls == 1) error("snapshot failed")
                    },
                    time = time { 0L },
                )

            heartbeat.start()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(2, calls)
            heartbeat.stop()
        }

    @Test
    fun `should not start after stop`() =
        runTest {
            val timestamps = mutableListOf<Long>()
            val heartbeat =
                Heartbeat(
                    scope = this,
                    stoppedEvent = { timestamps.add(it) },
                    time = time { 0L },
                )

            heartbeat.start()
            runCurrent()
            heartbeat.stop()
            runCurrent()
            val sent = timestamps.size
            heartbeat.start()
            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(sent, timestamps.size)
        }

    @Test
    fun `should not send on cancel`() =
        runTest {
            val timestamps = mutableListOf<Long>()
            var now = 10_000L
            val heartbeat =
                Heartbeat(
                    scope = this,
                    stoppedEvent = { timestamps.add(it) },
                    time = time { now },
                )

            heartbeat.start()
            runCurrent()
            assertEquals(1, timestamps.size)
            now = 10_500L
            heartbeat.cancel()
            runCurrent()

            assertEquals(1, timestamps.size)
        }
}

private fun time(nowMillis: () -> Long): Time =
    mockk<Time>().also { time ->
        every { time.nowMillis() } answers { nowMillis() }
        every { time.elapsedRealtime() } answers { nowMillis() }
    }
