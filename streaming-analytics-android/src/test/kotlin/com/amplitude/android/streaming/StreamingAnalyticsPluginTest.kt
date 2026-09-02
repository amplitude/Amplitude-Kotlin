package com.amplitude.android.streaming

import com.amplitude.core.AmplitudePreview
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@OptIn(AmplitudePreview::class)
class StreamingAnalyticsPluginTest {
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
