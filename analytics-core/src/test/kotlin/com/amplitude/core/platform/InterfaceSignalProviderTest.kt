package com.amplitude.core.platform

import com.amplitude.core.AmplitudeContext
import com.amplitude.core.AnalyticsClient
import com.amplitude.core.Configuration
import com.amplitude.core.utils.FakeAmplitude
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Date

class InterfaceSignalProviderTest {
    private class RecordingReceiver : InterfaceSignalReceiver {
        val changes = mutableListOf<InterfaceChangeSignal>()
        var startCount = 0
        var stopCount = 0

        override fun onInterfaceChanged(signal: InterfaceChangeSignal) {
            changes += signal
        }

        override fun onStartProviding() {
            startCount += 1
        }

        override fun onStopProviding() {
            stopCount += 1
        }
    }

    private class FakeProviderPlugin(
        override val name: String? = null,
        override var isProviding: Boolean = true,
    ) : UniversalPlugin, InterfaceSignalProvider {
        val receivers = mutableListOf<InterfaceSignalReceiver>()
        var setupClient: AnalyticsClient? = null
        var setupContext: AmplitudeContext? = null

        override fun setup(
            client: AnalyticsClient,
            context: AmplitudeContext,
        ) {
            setupClient = client
            setupContext = context
        }

        override fun addInterfaceSignalReceiver(receiver: InterfaceSignalReceiver) {
            if (receiver !in receivers) {
                receivers.add(receiver)
            }
        }

        override fun removeInterfaceSignalReceiver(receiver: InterfaceSignalReceiver) {
            receivers.remove(receiver)
        }
    }

    @Nested
    inner class AmplitudeAddRemove {
        @Test
        fun `adding an InterfaceSignalProvider plugin sets amplitude interfaceSignalProvider`() {
            val amplitude = FakeAmplitude(Configuration("test-api-key"))
            val provider = FakeProviderPlugin()

            amplitude.add(provider)

            assertSame(provider, amplitude.interfaceSignalProvider)
        }

        @Test
        fun `removing the current InterfaceSignalProvider plugin clears interfaceSignalProvider`() {
            val amplitude = FakeAmplitude(Configuration("test-api-key"))
            val provider = FakeProviderPlugin()
            amplitude.add(provider)

            amplitude.remove(provider)

            assertNull(amplitude.interfaceSignalProvider)
        }

        @Test
        fun `removing a different plugin leaves interfaceSignalProvider in place`() {
            val amplitude = FakeAmplitude(Configuration("test-api-key"))
            val provider = FakeProviderPlugin()
            val other = object : UniversalPlugin {}
            amplitude.add(provider)
            amplitude.add(other)

            amplitude.remove(other)

            assertSame(provider, amplitude.interfaceSignalProvider)
        }

        @Test
        fun `a later InterfaceSignalProvider replaces the previous one`() {
            val amplitude = FakeAmplitude(Configuration("test-api-key"))
            val first = FakeProviderPlugin()
            val second = FakeProviderPlugin()
            amplitude.add(first)

            amplitude.add(second)

            assertSame(second, amplitude.interfaceSignalProvider)
        }

        @Test
        fun `named duplicate InterfaceSignalProvider does not replace the incumbent`() {
            val amplitude = FakeAmplitude(Configuration("test-api-key"))
            val first = FakeProviderPlugin(name = "session-replay")
            val second = FakeProviderPlugin(name = "session-replay")
            amplitude.add(first)

            amplitude.add(second)

            assertSame(first, amplitude.interfaceSignalProvider)
        }

        @Test
        fun `Amplitude notifies when the provider changes`() {
            val changes = mutableListOf<Pair<InterfaceSignalProvider?, InterfaceSignalProvider?>>()
            val amplitude =
                object : FakeAmplitude(Configuration("test-api-key")) {
                    override fun onInterfaceSignalProviderChanged(
                        from: InterfaceSignalProvider?,
                        to: InterfaceSignalProvider?,
                    ) {
                        changes += from to to
                    }
                }
            val provider = FakeProviderPlugin()

            amplitude.add(provider)
            amplitude.remove(provider)

            assertSame(provider, changes[0].second)
            assertNull(changes[0].first)
            assertSame(provider, changes[1].first)
            assertNull(changes[1].second)
        }
    }

    @Nested
    inner class ProviderContract {
        @Test
        fun `provider delivers InterfaceChangeSignal to registered receivers`() {
            val provider = FakeProviderPlugin()
            val receiver = RecordingReceiver()
            provider.addInterfaceSignalReceiver(receiver)
            val signal = InterfaceChangeSignal(Date(1_700_000_000_000L))

            provider.receivers.forEach { it.onInterfaceChanged(signal) }

            assertSame(signal, receiver.changes.single())
            assertTrue(signal.time.time == 1_700_000_000_000L)
        }
    }
}
