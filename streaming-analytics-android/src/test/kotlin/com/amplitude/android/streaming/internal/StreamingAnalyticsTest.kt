package com.amplitude.android.streaming.internal

import com.amplitude.android.streaming.StreamingAnalyticsPlugin
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@OptIn(AmplitudePreview::class)
class StreamingAnalyticsTest {
    @Test
    fun `from returns the same instance until teardown`() {
        val amplitude = mockk<Amplitude>(relaxed = true)
        val first = StreamingAnalytics.from(amplitude)
        assertSame(first, StreamingAnalytics.from(amplitude))
        first.teardown()
        assertNotSame(first, StreamingAnalytics.from(amplitude))
    }

    @Test
    fun `plugin teardown does not create an instance`() {
        val amplitude = mockk<Amplitude>(relaxed = true)
        val plugin =
            StreamingAnalyticsPlugin().also { it.amplitude = amplitude }
        plugin.teardown()
        val created = StreamingAnalytics.from(amplitude)
        plugin.teardown()
        assertNotSame(created, StreamingAnalytics.from(amplitude))
    }
}
