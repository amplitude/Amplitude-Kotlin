package com.amplitude.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.core.events.BaseEvent
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AmplitudeOtherTest {
    private lateinit var amplitude: Amplitude

    @ExperimentalCoroutinesApi
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val apiKey = "test-api-key"
        amplitude = Amplitude(apiKey, context) {
            this.minTimeBetweenSessionsMillis = 100
            this.optOut = true
        }
    }

    @Test
    fun `test optOut is true no event should be send`() {
        val mockedPluginTest = spyk(StubPlugin())
        amplitude.add(mockedPluginTest)

        val event1 = BaseEvent()
        event1.eventType = "test event 1"
        event1.timestamp = 1000
        amplitude.track(event1)

        amplitude.onEnterForeground(1500)

        val event2 = BaseEvent()
        event2.eventType = "test event 2"
        event2.timestamp = 1700
        amplitude.track(event2)

        amplitude.onExitForeground()

        verify(exactly = 0) { mockedPluginTest.track(any()) }
    }
}
