package com.amplitude.android.plugins

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.utils.mockSystemTime
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.platform.ObservePlugin
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class ObservePluginTest {
    private lateinit var amplitude: Amplitude
    private lateinit var configuration: Configuration

    private val mockedContext = mockk<Application>(relaxed = true)
    private lateinit var connectivityManager: ConnectivityManager

    private fun setDispatcher(testScheduler: TestCoroutineScheduler) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // inject the amplitudeDispatcher field with reflection, as the field is val (read-only)
        val amplitudeDispatcherField = com.amplitude.core.Amplitude::class.java.getDeclaredField("amplitudeDispatcher")
        amplitudeDispatcherField.isAccessible = true
        amplitudeDispatcherField.set(amplitude, dispatcher)
    }

    @Before
    fun setup() {
        mockkConstructor(AndroidContextProvider::class)
        every { anyConstructed<AndroidContextProvider>().osName } returns "android"
        every { anyConstructed<AndroidContextProvider>().osVersion } returns "10"
        every { anyConstructed<AndroidContextProvider>().brand } returns "google"
        every { anyConstructed<AndroidContextProvider>().manufacturer } returns "Android"
        every { anyConstructed<AndroidContextProvider>().model } returns "Android SDK built for x86"
        every { anyConstructed<AndroidContextProvider>().language } returns "English"
        every { anyConstructed<AndroidContextProvider>().advertisingId } returns ""
        every { anyConstructed<AndroidContextProvider>().versionName } returns "1.0"
        every { anyConstructed<AndroidContextProvider>().carrier } returns "Android"
        every { anyConstructed<AndroidContextProvider>().country } returns "US"
        every { anyConstructed<AndroidContextProvider>().mostRecentLocation } returns null
        every { anyConstructed<AndroidContextProvider>().appSetId } returns ""

        connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { mockedContext!!.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager

        configuration = Configuration(
            apiKey = "api-key",
            context = mockedContext,
            storageProvider = InMemoryStorageProvider(),
            loggerProvider = ConsoleLoggerProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
            trackingSessionEvents = false,
            minTimeBetweenSessionsMillis = 500
        )
    }

    @Test
    fun `test onSessionIdChanged is called on instantiation`() = runTest {
        val testStartTime: Long = 1000
        val observePlugin = spyk(object : ObservePlugin() {
            override fun onSessionIdChanged(sessionId: Long?) {
                println("sessionId = $sessionId")
            }
        })
        configuration.plugins = listOf(observePlugin)
        configuration.defaultTracking.sessions = true

        mockSystemTime(testStartTime)

        amplitude = Amplitude(configuration)
        setDispatcher(testScheduler)
        amplitude.isBuilt.await()

        Assertions.assertEquals(testStartTime, amplitude.sessionId)

        advanceUntilIdle()
        Thread.sleep(100)

        val sessionIds = mutableListOf<Long>()
        verify { observePlugin.onSessionIdChanged(capture(sessionIds)) }

        Assertions.assertEquals(1, sessionIds.count())
        Assertions.assertEquals(testStartTime, sessionIds[0])
    }

    @Test
    fun `test onSessionIdChanged is called on instantiation with config sessionId`() = runTest {
        val testSessionId: Long = 1337
        val observePlugin = spyk(object : ObservePlugin() {
            override fun onSessionIdChanged(sessionId: Long?) {
                println("sessionId = $sessionId")
            }
        })
        configuration.plugins = listOf(observePlugin)
        configuration.defaultTracking.sessions = true
        configuration.sessionId = testSessionId

        amplitude = Amplitude(configuration)
        setDispatcher(testScheduler)
        amplitude.isBuilt.await()

        Assertions.assertEquals(testSessionId, amplitude.sessionId)

        advanceUntilIdle()
        Thread.sleep(100)

        val sessionIds = mutableListOf<Long>()
        verify { observePlugin.onSessionIdChanged(capture(sessionIds)) }

        Assertions.assertEquals(1, sessionIds.count())
        Assertions.assertEquals(testSessionId, sessionIds[0])
    }

    @Test
    fun `test onSessionIdChanged is called on session end`() = runTest {
        val testStartTime: Long = 1000
        val observePlugin = spyk(object : ObservePlugin() {
            override fun onSessionIdChanged(sessionId: Long?) {
                println("sessionId = $sessionId")
            }
        })
        configuration.plugins = listOf(observePlugin)
        configuration.defaultTracking.sessions = true

        mockSystemTime(testStartTime)

        amplitude = Amplitude(configuration)
        setDispatcher(testScheduler)
        amplitude.isBuilt.await()

        Assertions.assertEquals(testStartTime, amplitude.sessionId)

        val exitForegroundTime = mockSystemTime(testStartTime + 1000)
        amplitude.onExitForeground(exitForegroundTime)

        val enterForegroundTime = mockSystemTime(exitForegroundTime + 2000)
        amplitude.onEnterForeground(enterForegroundTime)

        advanceUntilIdle()
        Thread.sleep(100)

        Assertions.assertEquals(enterForegroundTime, amplitude.sessionId)

        val sessionIds = mutableListOf<Long>()
        verify { observePlugin.onSessionIdChanged(capture(sessionIds)) }

        Assertions.assertEquals(2, sessionIds.count())
        Assertions.assertEquals(testStartTime, sessionIds[0])
        Assertions.assertEquals(enterForegroundTime, sessionIds[1])
    }
}
