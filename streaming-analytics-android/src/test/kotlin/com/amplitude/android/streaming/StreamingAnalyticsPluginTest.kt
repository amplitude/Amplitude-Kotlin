package com.amplitude.android.streaming

import androidx.media3.common.Player
import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.android.trackPlayer
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(AmplitudePreview::class)
class StreamingAnalyticsPluginTest {
    @Nested
    inner class PluginContract {
        @Test
        fun `name is stable for Amplitude add dedupe`() {
            val plugin = StreamingAnalyticsPlugin()
            assertEquals("AmplitudeStreamingAnalytics", plugin.name)
            assertEquals(Plugin.Type.Before, plugin.type)
        }

        @Test
        fun `delayed events are consumed before the standard pipeline`() {
            val plugin = StreamingAnalyticsPlugin()
            val event =
                DelayedEvent(
                    eventType = "Video Content Stopped",
                    kind = DelayedEvent.Kind.DELAYED,
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
        fun `trackPlayer uses registered plugin`() {
            val amplitude = Amplitude(Configuration(apiKey = "test"))
            val plugin = StreamingAnalyticsPlugin()
            amplitude.add(plugin)

            val player = mockk<Player>(relaxed = true)
            amplitude.trackPlayer(player) { PlayerContent() }

            assertSame(plugin, amplitude.findPlugin<StreamingAnalyticsPlugin>())
            assertNotNull(plugin.streamingAnalytics)
        }

        @Test
        fun `trackPlayer adds plugin if not registered`() {
            val amplitude = Amplitude(Configuration(apiKey = "test"))
            val player = mockk<Player>(relaxed = true)
            amplitude.trackPlayer(player) { PlayerContent() }

            val plugin = amplitude.findPlugin<StreamingAnalyticsPlugin>()
            assertNotNull(plugin)
            assertNotNull(plugin?.streamingAnalytics)
        }
    }
}
