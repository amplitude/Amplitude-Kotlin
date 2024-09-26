package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.analytics.connector.Identity
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPlugin
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.concurrent.thread

open class StubPlugin : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: com.amplitude.core.Amplitude
}

@ExperimentalCoroutinesApi
class AmplitudeTest {
    private var context: Context? = null
    private var amplitude: Amplitude? = null
    private lateinit var connectivityManager: ConnectivityManager

    @BeforeEach
    fun setUp() {
        context = mockk<Application>(relaxed = true)
        connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context!!.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { context!!.getDir(any(), any()) } returns File("/tmp/amplitude-kotlin-test")

        mockkStatic(AndroidLifecyclePlugin::class)

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

        amplitude = Amplitude(createConfiguration())
    }

    private fun setDispatcher(testScheduler: TestCoroutineScheduler) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // inject the amplitudeDispatcher field with reflection, as the field is val (read-only)
        val amplitudeDispatcherField = com.amplitude.core.Amplitude::class.java.getDeclaredField("amplitudeDispatcher")
        amplitudeDispatcherField.isAccessible = true
        amplitudeDispatcherField.set(amplitude, dispatcher)
    }

    private fun createConfiguration(
        minTimeBetweenSessionsMillis: Long? = null,
        storageProvider: StorageProvider = InMemoryStorageProvider(),
        deviceId: String? = null,
        sessionId: Long? = null,
    ): Configuration {
        val configuration = Configuration(
            apiKey = "api-key",
            context = context!!,
            instanceName = instanceName,
            storageProvider = storageProvider,
            autocapture = if (minTimeBetweenSessionsMillis != null) setOf(AutocaptureOption.SESSIONS) else setOf(),
            loggerProvider = ConsoleLoggerProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
            sessionId = sessionId,
        )

        if (deviceId != null) {
            configuration.deviceId = deviceId
        }

        if (minTimeBetweenSessionsMillis != null) {
            configuration.minTimeBetweenSessionsMillis = minTimeBetweenSessionsMillis
        }

        return configuration
    }

    @Test
    fun amplitude_reset_wipesUserIdDeviceId() = runTest {
        setDispatcher(testScheduler)
        if (amplitude?.isBuilt!!.await()) {
            amplitude?.setUserId("test user")
            amplitude?.setDeviceId("test device")
            advanceUntilIdle()
            Assertions.assertEquals("test user", amplitude?.store?.userId)
            Assertions.assertEquals("test device", amplitude?.store?.deviceId)
            Assertions.assertEquals("test user", amplitude?.getUserId())
            Assertions.assertEquals("test device", amplitude?.getDeviceId())

            amplitude?.reset()
            advanceUntilIdle()
            Assertions.assertNull(amplitude?.store?.userId)
            Assertions.assertNotEquals("test device", amplitude?.store?.deviceId)
            Assertions.assertNull(amplitude?.getUserId())
            Assertions.assertNotEquals("test device", amplitude?.getDeviceId())
        }
    }

    @Test
    fun amplitude_unset_country_with_remote_ip() = runTest {
        setDispatcher(testScheduler)
        val mockedPlugin = spyk(StubPlugin())
        amplitude?.add(mockedPlugin)

        if (amplitude?.isBuilt!!.await()) {
            val event = BaseEvent()
            event.eventType = "test event"
            amplitude?.track(event)
            advanceUntilIdle()
            Thread.sleep(100)

            val track = slot<BaseEvent>()
            verify { mockedPlugin.track(capture(track)) }
            track.captured.let {
                Assertions.assertEquals("\$remote", it.ip)
                Assertions.assertNull(it.country)
            }
        }
    }

    @Test
    fun amplitude_fetch_country_with_customized_ip() = runTest {
        setDispatcher(testScheduler)
        val mockedPlugin = spyk(StubPlugin())
        amplitude?.add(mockedPlugin)

        if (amplitude?.isBuilt!!.await()) {
            val event = BaseEvent()
            event.eventType = "test event"
            event.ip = "127.0.0.1"
            amplitude?.track(event)
            advanceUntilIdle()
            Thread.sleep(100)

            val track = slot<BaseEvent>()
            verify { mockedPlugin.track(capture(track)) }
            track.captured.let {
                Assertions.assertEquals("127.0.0.1", it.ip)
                Assertions.assertEquals("US", it.country)
            }
        }
    }

    @Test
    fun test_analytics_connector() = runTest {
        setDispatcher(testScheduler)
        val mockedPlugin = spyk(StubPlugin())
        amplitude?.add(mockedPlugin)

        if (amplitude?.isBuilt!!.await()) {

            val connector = AnalyticsConnector.getInstance(instanceName)
            val connectorUserId = "connector user id"
            val connectorDeviceId = "connector device id"
            var connectorIdentitySet = false
            val identityListener = { _: Identity ->
                if (connectorIdentitySet) {
                    Assertions.assertEquals(connectorUserId, connector.identityStore.getIdentity().userId)
                    Assertions.assertEquals(connectorDeviceId, connector.identityStore.getIdentity().deviceId)
                    connectorIdentitySet = false
                }
            }
            connector.identityStore.addIdentityListener(identityListener)
            amplitude?.setUserId(connectorUserId)
            amplitude?.setDeviceId(connectorDeviceId)
            advanceUntilIdle()
            connectorIdentitySet = true
            connector.identityStore.removeIdentityListener(identityListener)
        }
    }

    @Test
    fun amplitude_getDeviceId_should_return_not_null_after_isBuilt() = runTest {
        setDispatcher(testScheduler)
        if (amplitude?.isBuilt!!.await()) {
            Assertions.assertNotNull(amplitude?.store?.deviceId)
            Assertions.assertNotNull(amplitude?.getDeviceId())
        }
    }

    @Test
    fun amplitude_should_set_deviceId_from_configuration() = runTest {
        val testDeviceId = "test device id"
        // set device Id in the config
        amplitude = Amplitude(createConfiguration(deviceId = testDeviceId))
        setDispatcher(testScheduler)

        if (amplitude?.isBuilt!!.await()) {
            Assertions.assertEquals(testDeviceId, amplitude?.store?.deviceId)
            Assertions.assertEquals(testDeviceId, amplitude?.getDeviceId())
        }
    }

    @Test
    fun amplitude_should_set_sessionId_from_configuration() = runTest {
        val testSessionId = 1337L
        // set device Id in the config
        amplitude = Amplitude(createConfiguration(sessionId = testSessionId))
        setDispatcher(testScheduler)

        if (amplitude?.isBuilt!!.await()) {
            Assertions.assertEquals(testSessionId, amplitude?.sessionId)
        }
    }

    /**
     * Here is what we want to test. In the older version of the SDK, we were unintentionally
     * extending a session. Here's how enter foreground was being handled before.
     *  - Application is entering foreground
     *  - Amplitude.inForeground flag is set to true.
     *  - Immediately after this, we expect the dummy foreground event to be processed and create
     *    a new session if needed.
     *
     *  If another event is fired between 2 and 3 from a different thread, we would unintentionally
     *  extend the session thinking that the app was in foreground.
     *
     *  The delay between foreground flag being set and the session being initialized was a problem.
     *
     *  We fix this by moving the foreground property inside the timeline. We expect every event
     *  processed before foreground = true in Timeline to be considered a background fired event.
     *
     *  This test checks for that scenario
     */
    @Test
    fun amplitude_should_correctly_start_new_session() = runTest {
        val testSessionId = 1000L

        // Creates a mocked timeline that waits for 500ms before processing the event. This
        // is to create an artificial delay
        val baseEventParam = slot<BaseEvent>()
        val timeline = Timeline(testSessionId)
        mockkObject(timeline)
        every { timeline.process(incomingEvent = capture(baseEventParam)) } answers {
            if (baseEventParam.captured.eventType == Amplitude.DUMMY_ENTER_FOREGROUND_EVENT) {
                Thread.sleep(500)
            }
            callOriginal()
        }

        amplitude = object : Amplitude(createConfiguration(sessionId = testSessionId, minTimeBetweenSessionsMillis = 50)) {
            override fun createTimeline(): Timeline {
                timeline.amplitude = this
                return timeline
            }
        }
        setDispatcher(testScheduler)

        if (amplitude?.isBuilt!!.await()) {
            // Fire a foreground event. This is fired using the delayed timeline. The event is
            // actually processed after 500ms
            val thread1 = thread {
                amplitude?.onEnterForeground(1120)
            }
            Thread.sleep(100)
            // Un-mock the object so that there's no delay anymore
            unmockkObject(timeline)

            // Fire a test event that will be added to the queue before the foreground event.
            val thread2 = thread {
                val event = BaseEvent()
                event.eventType = "test_event"
                event.timestamp = 1100L
                amplitude?.track(event)
            }
            thread1.join()
            thread2.join()

            // Wait for all events to have been processed
            advanceUntilIdle()

            // test_event should have created a new session and not extended an existing session
            Assertions.assertEquals(1100, amplitude?.sessionId)
        }
    }

    companion object {
        const val instanceName = "testInstance"
    }
}
