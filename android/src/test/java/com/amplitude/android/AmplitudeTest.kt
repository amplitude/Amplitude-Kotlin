package com.amplitude.android

import android.app.Application
import android.content.Context
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPlugin
import com.amplitude.core.platform.Plugin
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
            "testInstance",
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

    private fun createConfiguration(minTimeBetweenSessionsMillis: Long? = null): Configuration {
        val configuration = Configuration(
            apiKey = "api-key",
            context = context!!,
            instanceName = "testInstance",
            storageProvider = InMemoryStorageProvider(),
            trackingSessionEvents = minTimeBetweenSessionsMillis != null,
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

        amplitude = Amplitude(createConfiguration(100))

        val mockedPlugin = spyk(StubPlugin())
        amplitude?.add(mockedPlugin)

        if (amplitude?.isBuilt!!.await()) {
            val event1 = BaseEvent()
            event1.eventType = "test event 1"
            event1.timestamp = 1000
            amplitude!!.track(event1)

            val event2 = BaseEvent()
            event2.eventType = "test event 2"
            event2.timestamp = 1050
            amplitude!!.track(event2)

            val event3 = BaseEvent()
            event3.eventType = "test event 3"
            event3.timestamp = 1200
            amplitude!!.track(event3)

            val event4 = BaseEvent()
            event4.eventType = "test event 4"
            event4.timestamp = 1350
            amplitude!!.track(event4)

            advanceUntilIdle()

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }

            tracks.sortBy { event -> event.eventId }

            Assertions.assertEquals(9, tracks.count())

            tracks[0].let {
                Assertions.assertEquals("session_start", it.eventType)
                Assertions.assertEquals(1000L, it.timestamp)
            }
            tracks[1].let {
                Assertions.assertEquals("test event 1", it.eventType)
                Assertions.assertEquals(1000L, it.timestamp)
            }
            tracks[2].let {
                Assertions.assertEquals("test event 2", it.eventType)
                Assertions.assertEquals(1050L, it.timestamp)
            }
            tracks[3].let {
                Assertions.assertEquals("session_end", it.eventType)
                Assertions.assertEquals(1050L, it.timestamp)
            }
            tracks[4].let {
                Assertions.assertEquals("session_start", it.eventType)
                Assertions.assertEquals(1200L, it.timestamp)
            }
            tracks[5].let {
                Assertions.assertEquals("test event 3", it.eventType)
                Assertions.assertEquals(1200L, it.timestamp)
            }
            tracks[6].let {
                Assertions.assertEquals("session_end", it.eventType)
                Assertions.assertEquals(1200L, it.timestamp)
            }
            tracks[7].let {
                Assertions.assertEquals("session_start", it.eventType)
                Assertions.assertEquals(1350L, it.timestamp)
            }
            tracks[8].let {
                Assertions.assertEquals("test event 4", it.eventType)
                Assertions.assertEquals(1350L, it.timestamp)
            }
        }
    }
}
