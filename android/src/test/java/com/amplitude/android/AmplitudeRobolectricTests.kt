package com.amplitude.android

import android.app.Application
import android.content.Context
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.ConsoleLoggerProvider
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AmplitudeRobolectricTests {
    private lateinit var amplitude: Amplitude
    private var context: Context? = null

    @ExperimentalCoroutinesApi
    @Before
    fun setup() {
        context = mockk<Application>(relaxed = true)

        amplitude = Amplitude(createConfiguration())
    }

    @Test
    fun `test optOut is true no event should be send`() {
        val mockedPluginTest = spyk(StubPlugin())
        val amplitudeInstance = Amplitude(createConfiguration(100, true))
        amplitudeInstance.add(mockedPluginTest)

        val event1 = BaseEvent()
        event1.eventType = "test event 1"
        event1.timestamp = 1000
        amplitudeInstance.track(event1)

        amplitudeInstance.onEnterForeground(1500)

        val event2 = BaseEvent()
        event2.eventType = "test event 2"
        event2.timestamp = 1700
        amplitudeInstance.track(event2)

        amplitudeInstance.onExitForeground()

        verify(exactly = 0) { mockedPluginTest.track(any()) }
    }

    private fun createConfiguration(
        minTimeBetweenSessionsMillis: Long? = null,
        optOut: Boolean? = null,
    ): Configuration {

        return Configuration(
            apiKey = "api-key",
            context = context!!,
            instanceName = "testInstance",
            loggerProvider = ConsoleLoggerProvider(),
            optOut = optOut ?: false,
            minTimeBetweenSessionsMillis = minTimeBetweenSessionsMillis
                ?: Configuration.MIN_TIME_BETWEEN_SESSIONS_MILLIS,
        )
    }
}
