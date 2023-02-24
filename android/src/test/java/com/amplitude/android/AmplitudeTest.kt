package com.amplitude.android

import android.app.Application
import android.content.Context
import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.analytics.connector.Identity
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPlugin
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorage
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityConfiguration
import com.amplitude.id.IdentityContainer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

open class StubPlugin : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: com.amplitude.core.Amplitude
}

class AmplitudeTest {
    private var context: Context? = null
    private var amplitude: Amplitude? = null

    @BeforeEach
    fun setUp() {
        context = mockk<Application>(relaxed = true)
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

        val configuration = IdentityConfiguration(
            instanceName,
            identityStorageProvider = IMIdentityStorageProvider()
        )
        IdentityContainer.getInstance(configuration)
        amplitude = Amplitude(createConfiguration())
    }

    private fun setDispatcher(testScheduler: TestCoroutineScheduler) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // inject the amplitudeDispatcher field with reflection, as the field is val (read-only)
        val amplitudeDispatcherField = com.amplitude.core.Amplitude::class.java.getDeclaredField("amplitudeDispatcher")
        amplitudeDispatcherField.isAccessible = true
        amplitudeDispatcherField.set(amplitude, dispatcher)
    }

    private fun createConfiguration(minTimeBetweenSessionsMillis: Long? = null, storageProvider: StorageProvider = InMemoryStorageProvider()): Configuration {
        val configuration = Configuration(
            apiKey = "api-key",
            context = context!!,
            instanceName = "testInstance",
            storageProvider = storageProvider,
            trackingSessionEvents = minTimeBetweenSessionsMillis != null,
            loggerProvider = ConsoleLoggerProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider()
        )

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

            amplitude?.reset()
            advanceUntilIdle()
            Assertions.assertNull(amplitude?.store?.userId)
            Assertions.assertNotEquals("test device", amplitude?.store?.deviceId)
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
    fun amplitude_tracking_session() = runTest {
        setDispatcher(testScheduler)

        val amplitude = Amplitude(createConfiguration(100))

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        val event1 = BaseEvent()
        event1.eventType = "test event 1"
        event1.timestamp = 1000
        amplitude.track(event1)

        val event2 = BaseEvent()
        event2.eventType = "test event 2"
        event2.timestamp = 1050
        amplitude.track(event2)

        val event3 = BaseEvent()
        event3.eventType = "test event 3"
        event3.timestamp = 1200
        amplitude.track(event3)

        val event4 = BaseEvent()
        event4.eventType = "test event 4"
        event4.timestamp = 1350
        amplitude.track(event4)

        amplitude.onEnterForeground(1500)

        val event5 = BaseEvent()
        event5.eventType = "test event 5"
        event5.timestamp = 1700
        amplitude.track(event5)

        amplitude.onExitForeground()

        val event6 = BaseEvent()
        event6.eventType = "test event 6"
        event6.timestamp = 1750
        amplitude.track(event6)

        val event7 = BaseEvent()
        event7.eventType = "test event 7"
        event7.timestamp = 2000
        amplitude.track(event7)

        amplitude.onEnterForeground(2050)

        val event8 = BaseEvent()
        event8.eventType = "test event 8"
        event8.timestamp = 2200
        amplitude.track(event8)

        advanceUntilIdle()

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }

        tracks.sortBy { event -> event.eventId }

        Assertions.assertEquals(17, tracks.count())

        tracks[0].let {
            Assertions.assertEquals("session_start", it.eventType)
            Assertions.assertEquals(1000L, it.timestamp)
            Assertions.assertEquals(1000L, it.sessionId)
        }
        tracks[1].let {
            Assertions.assertEquals("test event 1", it.eventType)
            Assertions.assertEquals(1000L, it.timestamp)
            Assertions.assertEquals(1000L, it.sessionId)
        }
        tracks[2].let {
            Assertions.assertEquals("test event 2", it.eventType)
            Assertions.assertEquals(1050L, it.timestamp)
            Assertions.assertEquals(1000L, it.sessionId)
        }
        tracks[3].let {
            Assertions.assertEquals("session_end", it.eventType)
            Assertions.assertEquals(1050L, it.timestamp)
            Assertions.assertEquals(1000L, it.sessionId)
        }

        tracks[4].let {
            Assertions.assertEquals("session_start", it.eventType)
            Assertions.assertEquals(1200L, it.timestamp)
            Assertions.assertEquals(1200L, it.sessionId)
        }
        tracks[5].let {
            Assertions.assertEquals("test event 3", it.eventType)
            Assertions.assertEquals(1200L, it.timestamp)
            Assertions.assertEquals(1200L, it.sessionId)
        }
        tracks[6].let {
            Assertions.assertEquals("session_end", it.eventType)
            Assertions.assertEquals(1200L, it.timestamp)
            Assertions.assertEquals(1200L, it.sessionId)
        }

        tracks[7].let {
            Assertions.assertEquals("session_start", it.eventType)
            Assertions.assertEquals(1350L, it.timestamp)
            Assertions.assertEquals(1350L, it.sessionId)
        }
        tracks[8].let {
            Assertions.assertEquals("test event 4", it.eventType)
            Assertions.assertEquals(1350L, it.timestamp)
            Assertions.assertEquals(1350L, it.sessionId)
        }
        tracks[9].let {
            Assertions.assertEquals("session_end", it.eventType)
            Assertions.assertEquals(1350L, it.timestamp)
            Assertions.assertEquals(1350L, it.sessionId)
        }

        tracks[10].let {
            Assertions.assertEquals("session_start", it.eventType)
            Assertions.assertEquals(1500L, it.timestamp)
            Assertions.assertEquals(1500L, it.sessionId)
        }
        tracks[11].let {
            Assertions.assertEquals("test event 5", it.eventType)
            Assertions.assertEquals(1700L, it.timestamp)
            Assertions.assertEquals(1500L, it.sessionId)
        }
        tracks[12].let {
            Assertions.assertEquals("test event 6", it.eventType)
            Assertions.assertEquals(1750L, it.timestamp)
            Assertions.assertEquals(1500L, it.sessionId)
        }
        tracks[13].let {
            Assertions.assertEquals("session_end", it.eventType)
            Assertions.assertEquals(1750L, it.timestamp)
            Assertions.assertEquals(1500L, it.sessionId)
        }

        tracks[14].let {
            Assertions.assertEquals("session_start", it.eventType)
            Assertions.assertEquals(2000L, it.timestamp)
            Assertions.assertEquals(2000L, it.sessionId)
        }
        tracks[15].let {
            Assertions.assertEquals("test event 7", it.eventType)
            Assertions.assertEquals(2000L, it.timestamp)
            Assertions.assertEquals(2000L, it.sessionId)
        }
        tracks[16].let {
            Assertions.assertEquals("test event 8", it.eventType)
            Assertions.assertEquals(2200L, it.timestamp)
            Assertions.assertEquals(2000L, it.sessionId)
        }
    }

    @Test
    fun amplitude_session_restore() = runTest {
        setDispatcher(testScheduler)

        val storage = InMemoryStorage(amplitude!!)

        val amplitude1 = Amplitude(createConfiguration(100, InstanceStorageProvider(storage)))
        amplitude1.isBuilt.await()
        val timeline1 = amplitude1.timeline as Timeline

        amplitude1.onEnterForeground(1000)
        advanceUntilIdle()
        Thread.sleep(100)

        Assertions.assertEquals(1000L, timeline1.sessionId)
        Assertions.assertEquals(1L, timeline1.lastEventId)
        Assertions.assertEquals(1000L, timeline1.lastEventTime)

        val event1 = BaseEvent()
        event1.eventType = "test event 1"
        event1.timestamp = 1200
        amplitude1.track(event1)
        advanceUntilIdle()
        Thread.sleep(100)

        Assertions.assertEquals(1000L, timeline1.sessionId)
        Assertions.assertEquals(2L, timeline1.lastEventId)
        Assertions.assertEquals(1200L, timeline1.lastEventTime)

        val amplitude2 = Amplitude(createConfiguration(100, InstanceStorageProvider(storage)))
        amplitude2.isBuilt.await()
        val timeline2 = amplitude2.timeline as Timeline
        advanceUntilIdle()
        Thread.sleep(100)

        Assertions.assertEquals(1000L, timeline2.sessionId)
        Assertions.assertEquals(2L, timeline2.lastEventId)
        Assertions.assertEquals(1200L, timeline2.lastEventTime)

        val amplitude3 = Amplitude(createConfiguration(100, InstanceStorageProvider(storage)))
        amplitude3.isBuilt.await()
        val timeline3 = amplitude3.timeline as Timeline
        advanceUntilIdle()
        Thread.sleep(100)

        Assertions.assertEquals(1000L, timeline3.sessionId)
        Assertions.assertEquals(2L, timeline3.lastEventId)
        Assertions.assertEquals(1200L, timeline3.lastEventTime)

        amplitude3.onEnterForeground(1400)
        advanceUntilIdle()
        Thread.sleep(100)

        Assertions.assertEquals(1400L, timeline3.sessionId)
        Assertions.assertEquals(4L, timeline3.lastEventId)
        Assertions.assertEquals(1400L, timeline3.lastEventTime)
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

    companion object {
        const val instanceName = "testInstance"
    }
}

class InstanceStorageProvider(private val instance: Storage) : StorageProvider {
    override fun getStorage(amplitude: com.amplitude.core.Amplitude, prefix: String?): Storage {
        return instance
    }
}
