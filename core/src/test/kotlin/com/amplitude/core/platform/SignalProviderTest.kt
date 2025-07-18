package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utils.FakeAmplitude
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Date

@ExperimentalCoroutinesApi
class SignalProviderTest {
    private val testDispatcher = StandardTestDispatcher()
    private val amplitude =
        FakeAmplitude(
            Configuration("test-api-key"),
            testDispatcher = testDispatcher,
        )
    private val signalProviderPlugin = TestSignalProviderPlugin()

    @Test
    fun `test signal collection lifecycle - add and remove plugin`() =
        runTest(testDispatcher) {
            amplitude.isBuilt.await()

            // Collect signals from amplitude's signalFlow
            val collectedSignals = mutableListOf<Signal>()
            val collectionJob =
                launch {
                    amplitude.signalFlow.collectLatest { signal ->
                        collectedSignals.add(signal)
                    }
                }
            advanceUntilIdle()

            // Add the signal provider plugin
            amplitude.add(signalProviderPlugin)
            advanceUntilIdle()

            // Emit a signal from the plugin
            val date1 = Date(1_000_000_000_000)
            val signal1 = UiChangeSignal(date1)
            signalProviderPlugin.emitSignal(signal1)
            advanceUntilIdle()

            // Verify we collected the signal
            assertEquals(1, collectedSignals.size)
            assertEquals(signal1, collectedSignals.first())
            assertEquals(date1.time, (collectedSignals.first() as UiChangeSignal).timestamp.time)

            // Remove the plugin
            amplitude.remove(signalProviderPlugin)
            advanceUntilIdle()

            // Emit another signal (should not be collected)
            val date2 = Date(2_000_000_000_000)
            val signal2 = UiChangeSignal(date2)
            signalProviderPlugin.emitSignal(signal2)
            advanceUntilIdle()

            // Verify we still only have the first signal
            assertEquals(1, collectedSignals.size)
            assertEquals(signal1, collectedSignals.first())

            // Clean up
            collectionJob.cancel()
        }

    @Test
    fun `test multiple signal providers emit signals simultaneously`() =
        runTest(testDispatcher) {
            amplitude.isBuilt.await()

            // Collect signals from amplitude's signalFlow
            val collectedSignals = mutableListOf<Signal>()
            val collectionJob =
                launch {
                    amplitude.signalFlow.collectLatest { signal ->
                        collectedSignals.add(signal)
                    }
                }
            advanceUntilIdle()

            // Add two signal provider plugins
            val plugin1 = TestSignalProviderPlugin()
            val plugin2 = TestSignalProviderPlugin()
            amplitude.add(plugin1)
            amplitude.add(plugin2)
            advanceUntilIdle()

            // Emit signals from both plugins
            val signal1 = UiChangeSignal(Date())
            val signal2 = UiChangeSignal(Date())

            plugin1.emitSignal(signal1)
            plugin2.emitSignal(signal2)
            advanceUntilIdle()

            // Verify both signals were collected
            assertEquals(2, collectedSignals.size)
            assertEquals(setOf(signal1, signal2), collectedSignals.toSet())

            // Clean up
            collectionJob.cancel()
        }

    @Test
    fun `test signal emission is blocked after plugin removal`() =
        runTest(testDispatcher) {
            amplitude.isBuilt.await()

            val collectedSignals = mutableListOf<Signal>()
            val collectionJob =
                launch {
                    amplitude.signalFlow.collect { signal ->
                        collectedSignals.add(signal)
                    }
                }
            advanceUntilIdle()

            // Add plugin
            amplitude.add(signalProviderPlugin)
            advanceUntilIdle()

            // Emit signal - should work
            val signal1 = UiChangeSignal(Date(1000))
            signalProviderPlugin.emitSignal(signal1)
            advanceUntilIdle()

            assertEquals(1, collectedSignals.size)
            assertEquals(signal1, collectedSignals.first())

            // Remove plugin
            amplitude.remove(signalProviderPlugin)
            advanceUntilIdle()

            // Try to emit signal after removal - should be blocked
            val signal2 = UiChangeSignal(Date(2000))
            signalProviderPlugin.emitSignal(signal2)
            advanceUntilIdle()

            // Should still only have the first signal
            assertEquals(1, collectedSignals.size)
            assertEquals(signal1, collectedSignals.first())

            collectionJob.cancel()
        }

    @Test
    fun `test timeline remove calls teardown and blocks signal emission`() =
        runTest(testDispatcher) {
            amplitude.isBuilt.await()

            val testPlugin = TestSignalProviderPlugin()
            val collectedSignals = mutableListOf<Signal>()

            val collectionJob =
                launch {
                    amplitude.signalFlow.collect { signal ->
                        collectedSignals.add(signal)
                    }
                }
            advanceUntilIdle()

            // Add plugin
            amplitude.add(testPlugin)
            advanceUntilIdle()

            // Verify plugin is active and can emit
            assertFalse(testPlugin.teardownCalled)
            val signal1 = UiChangeSignal(Date(1000))
            testPlugin.emitSignal(signal1)
            advanceUntilIdle()

            assertEquals(1, collectedSignals.size)

            // Remove plugin through amplitude.remove() which delegates to timeline.remove()
            amplitude.remove(testPlugin)
            advanceUntilIdle()

            // Verify teardown was called through the timeline removal process
            assertTrue(testPlugin.teardownCalled, "Timeline.remove() should have called teardown()")

            // Try to emit signal after removal - should be blocked
            val signal2 = UiChangeSignal(Date(2000))
            testPlugin.emitSignal(signal2)
            advanceUntilIdle()

            // Should still only have the first signal
            assertEquals(1, collectedSignals.size, "Signal emission should be blocked after teardown")

            collectionJob.cancel()
        }

    /**
     * Test plugin that extends SignalProvider to emit UiChangeSignal
     */
    private class TestSignalProviderPlugin : SignalProvider(), EventPlugin {
        override val type: Plugin.Type = Plugin.Type.Before
        override lateinit var amplitude: Amplitude
        var teardownCalled = false
            private set

        override fun track(payload: BaseEvent): BaseEvent = payload

        override fun teardown() {
            teardownCalled = true
            super<SignalProvider>.teardown()
        }
    }
}
