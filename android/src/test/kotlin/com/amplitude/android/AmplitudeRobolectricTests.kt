package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.core.Amplitude as CoreAmplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import kotlin.io.path.absolutePathString
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.TempDirectory
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AmplitudeRobolectricTests {
    private lateinit var amplitude: CoreAmplitude
    private var context: Context? = null
    private lateinit var connectivityManager: ConnectivityManager

    var tempDir = TempDirectory()

    @ExperimentalCoroutinesApi
    @Before
    fun setup() {
        context = mockk<Application>(relaxed = true)
        connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context!!.getDir(any(), any()) } returns File(tempDir.create("data").absolutePathString())
        every { context!!.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        amplitude = Amplitude(createConfiguration())
    }

    @After
    fun tearDown() {
        tempDir.destroy()
    }

    @Test
    fun `test optOut is true no event should be send`() {
        val fakeEventPlugin = FakeEventPlugin()
        val amplitudeInstance = Amplitude(createConfiguration(100, true))
        amplitudeInstance.add(fakeEventPlugin)

        val event1 = BaseEvent()
        event1.eventType = "test event 1"
        event1.timestamp = 1000
        amplitudeInstance.track(event1)

        amplitudeInstance.onEnterForeground(1500)

        val event2 = BaseEvent()
        event2.eventType = "test event 2"
        event2.timestamp = 1700
        amplitudeInstance.track(event2)

        amplitudeInstance.onExitForeground(2000)

        assertTrue { fakeEventPlugin.trackedEvents.isEmpty() }
    }

    private fun createConfiguration(
        minTimeBetweenSessionsMillis: Long? = null,
        optOut: Boolean? = null,
    ): Configuration {

        return Configuration(
            apiKey = "api-key",
            context = context!!,
            instanceName = "testInstance",
            identityStorageProvider = IMIdentityStorageProvider(),
            loggerProvider = ConsoleLoggerProvider(),
            optOut = optOut ?: false,
            minTimeBetweenSessionsMillis = minTimeBetweenSessionsMillis
                ?: Configuration.MIN_TIME_BETWEEN_SESSIONS_MILLIS,
        )
    }
}
