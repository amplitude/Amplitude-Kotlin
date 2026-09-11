package com.amplitude.android.streaming

import androidx.media3.common.Player
import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.android.streaming.internal.StreamingAnalytics
import com.amplitude.android.trackPlayer
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.Timeline
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.amplitude.android.Amplitude as AndroidAmplitude

@OptIn(AmplitudePreview::class, ExperimentalCoroutinesApi::class)
class StreamingAnalyticsPluginTest {
    @Nested
    inner class PluginContract {
        @Test
        fun `name is stable for Amplitude add dedupe`() {
            val plugin = StreamingAnalyticsPlugin()
            assertEquals("AmplitudeStreamingAnalytics", plugin.name)
            assertEquals(Plugin.Type.Enrichment, plugin.type)
        }

        @Test
        fun `delayed events are consumed after before plugins and before destinations`() {
            val plugin = StreamingAnalyticsPlugin()
            val event =
                DelayedEvent(
                    eventType = "Video Content Stopped",
                    timestamp = 1L,
                    eventProperties = mutableMapOf(),
                )

            assertNull(plugin.execute(event))
        }

        @Test
        fun `standard events pass through unchanged`() {
            val plugin = StreamingAnalyticsPlugin()
            val event = BaseEvent()

            assertSame(event, plugin.execute(event))
        }

        @Test
        fun `public constructor is loadable by class name`() {
            val loaded =
                Class.forName("com.amplitude.android.streaming.StreamingAnalyticsPlugin")
                    .getDeclaredConstructor()
                    .newInstance() as StreamingAnalyticsPlugin
            assertEquals("AmplitudeStreamingAnalytics", loaded.name)
        }
    }

    @Nested
    inner class LifecycleAndWiring {
        @Test
        fun `setup initializes streamingAnalytics and teardown clears it`() {
            val amplitude = mockk<Amplitude>(relaxed = true)
            val plugin = StreamingAnalyticsPlugin()
            assertNull(plugin.streamingAnalytics)

            plugin.setup(amplitude)
            val instance = plugin.streamingAnalytics
            assertNotNull(instance)
            assertSame(instance, plugin.streamingAnalytics)

            plugin.teardown()
            assertNull(plugin.streamingAnalytics)
        }

        @Test
        fun `trackPlayer uses registered plugin`() =
            runTest {
                val isBuilt = CompletableDeferred<Boolean>()
                val amplitude = androidAmplitude(isBuilt)
                val streamingAnalytics = installMockedPlugin(amplitude)

                val player = mockk<Player>(relaxed = true)
                val contentProvider = PlayerContentProvider { PlayerContent() }
                amplitude.trackPlayer(player, contentProvider)
                isBuilt.complete(true)
                advanceUntilIdle()

                verify { streamingAnalytics.trackPlayer(player, contentProvider) }
            }

        @Test
        fun `trackPlayer waits for the instance to finish building`() =
            runTest {
                val isBuilt = CompletableDeferred<Boolean>()
                val amplitude = androidAmplitude(isBuilt)
                val player = mockk<Player>(relaxed = true)
                val contentProvider = PlayerContentProvider { PlayerContent() }

                amplitude.trackPlayer(player, contentProvider)
                advanceUntilIdle()

                val streamingAnalytics = installMockedPlugin(amplitude)
                isBuilt.complete(true)
                advanceUntilIdle()

                verify { streamingAnalytics.trackPlayer(player, contentProvider) }
            }

        @Test
        fun `trackPlayer logs an error when the plugin is not installed`() =
            runTest {
                val amplitude = androidAmplitude(CompletableDeferred(true))

                amplitude.trackPlayer(mockk<Player>(relaxed = true)) { PlayerContent() }
                advanceUntilIdle()

                verify { amplitude.logger.error("StreamingAnalyticsPlugin is not installed.") }
            }

        @Test
        fun `trackPlayer logs an error after plugin teardown`() =
            runTest {
                val amplitude = androidAmplitude(CompletableDeferred(true))
                val plugin = StreamingAnalyticsPlugin()
                amplitude.add(plugin)
                plugin.teardown()

                amplitude.trackPlayer(mockk<Player>(relaxed = true)) { PlayerContent() }
                advanceUntilIdle()

                verify { amplitude.logger.error("StreamingAnalyticsPlugin is not installed.") }
            }

        private fun installMockedPlugin(amplitude: AndroidAmplitude): StreamingAnalytics {
            val plugin = StreamingAnalyticsPlugin()
            amplitude.add(plugin)
            assertSame(plugin, amplitude.findPlugin<StreamingAnalyticsPlugin>())
            return mockk<StreamingAnalytics>(relaxed = true).also { plugin.streamingAnalytics = it }
        }

        private fun TestScope.androidAmplitude(isBuilt: CompletableDeferred<Boolean>): AndroidAmplitude {
            val timeline = Timeline()
            val amplitude = mockk<AndroidAmplitude>(relaxed = true)
            timeline.amplitude = amplitude
            every { amplitude.timeline } returns timeline
            every { amplitude.isBuilt } returns isBuilt
            every { amplitude.amplitudeScope } returns this as CoroutineScope
            every { amplitude.amplitudeDispatcher } returns StandardTestDispatcher(testScheduler)
            every { amplitude.add(any<Plugin>()) } answers {
                timeline.add(firstArg<Plugin>())
                amplitude
            }
            return amplitude
        }
    }
}
