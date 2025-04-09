package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.analytics.connector.Identity
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.android.utilities.setupMockAndroidContext
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPlugin
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlin.concurrent.thread
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

open class StubPlugin : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: com.amplitude.core.Amplitude
}

@ExperimentalCoroutinesApi
class AmplitudeTest {
    private lateinit var amplitude: Amplitude

    private fun createConfiguration(
        minTimeBetweenSessionsMillis: Long? = null,
        storageProvider: StorageProvider = InMemoryStorageProvider(),
        deviceId: String? = null,
        sessionId: Long? = null,
    ): Configuration {
        setupMockAndroidContext()
        val context = mockk<Application>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every {
            context.getSystemService(
                Context.CONNECTIVITY_SERVICE
            )
        } returns connectivityManager
        val dirNameSlot = slot<String>()
        every { context.getDir(capture(dirNameSlot), any()) } answers {
            File("/tmp/amplitude-kotlin/${dirNameSlot.captured}")
        }

        val configuration = Configuration(
            apiKey = "api-key",
            context = context,
            instanceName = INSTANCE_NAME,
            storageProvider = storageProvider,
            autocapture = if (minTimeBetweenSessionsMillis != null) setOf(
                AutocaptureOption.SESSIONS
            ) else setOf(),
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
        val amplitude = createFakeAmplitude(
            scheduler = testScheduler,
            configuration = createConfiguration()
        )
        amplitude.isBuilt.await()
        amplitude.setUserId("test user")
        amplitude.setDeviceId("test device")
        advanceUntilIdle()
        assertEquals("test user", amplitude.store.userId)
        assertEquals("test device", amplitude.store.deviceId)
        assertEquals("test user", amplitude.getUserId())
        assertEquals("test device", amplitude.getDeviceId())

        amplitude.reset()
        advanceUntilIdle()
        assertNull(amplitude.store.userId)
        assertNotEquals("test device", amplitude.store.deviceId)
        assertNull(amplitude.getUserId())
        assertNotEquals("test device", amplitude.getDeviceId())
    }

    @Test
    fun amplitude_unset_country_with_remote_ip() = runTest {
        val amplitude = createFakeAmplitude(
            scheduler = testScheduler,
            configuration = createConfiguration()
        )
        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()
        val event = BaseEvent()
        event.eventType = "test event"
        amplitude.track(event)
        advanceUntilIdle()
        Thread.sleep(100)

        val track = slot<BaseEvent>()
        verify { mockedPlugin.track(capture(track)) }
        track.captured.let {
            assertEquals("\$remote", it.ip)
            assertNull(it.country)
        }
    }

    @Test
    fun amplitude_fetch_country_with_customized_ip() = runTest {
        val amplitude = createFakeAmplitude(
            scheduler = testScheduler,
            configuration = createConfiguration()
        )
        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()
        val event = BaseEvent()
        event.eventType = "test event"
        event.ip = "127.0.0.1"
        amplitude.track(event)
        advanceUntilIdle()
        Thread.sleep(100)

        val track = slot<BaseEvent>()
        verify { mockedPlugin.track(capture(track)) }
        track.captured.let {
            assertEquals("127.0.0.1", it.ip)
            assertEquals("US", it.country)
        }
    }

    @Test
    fun test_analytics_connector() = runTest {
        val amplitude = createFakeAmplitude(
            scheduler = testScheduler,
            configuration = createConfiguration()
        )
        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        val connector = AnalyticsConnector.getInstance(INSTANCE_NAME)
        val connectorUserId = "connector user id"
        val connectorDeviceId = "connector device id"
        var connectorIdentitySet = false
        val identityListener = { _: Identity ->
            if (connectorIdentitySet) {
                assertEquals(
                    connectorUserId, connector.identityStore.getIdentity().userId
                )
                assertEquals(
                    connectorDeviceId, connector.identityStore.getIdentity().deviceId
                )
                connectorIdentitySet = false
            }
        }
        connector.identityStore.addIdentityListener(identityListener)
        amplitude.setUserId(connectorUserId)
        amplitude.setDeviceId(connectorDeviceId)
        advanceUntilIdle()
        connectorIdentitySet = true
        connector.identityStore.removeIdentityListener(identityListener)
    }

    @Test
    fun amplitude_getDeviceId_should_return_not_null_after_isBuilt() = runTest {
        val amplitude = createFakeAmplitude(
            scheduler = testScheduler,
            configuration = createConfiguration()
        )
        amplitude.isBuilt.await()
        assertNotNull(amplitude.store.deviceId)
        assertNotNull(amplitude.getDeviceId())
    }

    @Test
    fun amplitude_should_set_deviceId_from_configuration() = runTest {
        val testDeviceId = "test device id"
        // set device Id in the config
        amplitude = Amplitude(createConfiguration(deviceId = testDeviceId))
        val amplitude = createFakeAmplitude(
            scheduler = testScheduler,
            configuration = createConfiguration()
        )

        if (amplitude.isBuilt!!.await()) {
            assertEquals(testDeviceId, amplitude.store?.deviceId)
            assertEquals(testDeviceId, amplitude.getDeviceId())
        }
    }

    @Test
    fun amplitude_should_set_sessionId_from_configuration() = runTest {
        val testSessionId = 1337L
        // set device Id in the config
        val amplitude = createFakeAmplitude(
            scheduler = testScheduler,
            configuration = createConfiguration(sessionId = testSessionId)
        )

        if (amplitude.isBuilt!!.await()) {
            assertEquals(testSessionId, amplitude.sessionId)
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

        val amplitude = object : Amplitude(
            createConfiguration(sessionId = testSessionId, minTimeBetweenSessionsMillis = 50)
        ) {
            override fun createTimeline(): Timeline {
                timeline.amplitude = this
                return timeline
            }
        }

        amplitude.isBuilt.await()
        // Fire a foreground event. This is fired using the delayed timeline. The event is
        // actually processed after 500ms
        val thread1 = thread {
            amplitude.onEnterForeground(1120)
        }
        Thread.sleep(100)
        // Un-mock the object so that there's no delay anymore
        unmockkObject(timeline)

        // Fire a test event that will be added to the queue before the foreground event.
        val thread2 = thread {
            val event = BaseEvent()
            event.eventType = "test_event"
            event.timestamp = 1100L
            amplitude.track(event)
        }
        thread1.join()
        thread2.join()

        // Wait for all events to have been processed
        advanceUntilIdle()

        // test_event should have created a new session and not extended an existing session
        assertEquals(1100, amplitude.sessionId)
    }

    companion object {
        private const val INSTANCE_NAME = "testInstance"
    }
}
