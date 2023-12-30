package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
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
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class AmplitudeSessionTest {
    @BeforeEach
    fun setUp() {
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
    }

    private fun setDispatcher(amplitude: Amplitude, testScheduler: TestCoroutineScheduler) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // inject the amplitudeDispatcher field with reflection, as the field is val (read-only)
        val amplitudeDispatcherField = com.amplitude.core.Amplitude::class.java.getDeclaredField("amplitudeDispatcher")
        amplitudeDispatcherField.isAccessible = true
        amplitudeDispatcherField.set(amplitude, dispatcher)
    }

    private fun createConfiguration(storageProvider: StorageProvider? = null): Configuration {
        val context = mockk<Application>(relaxed = true)
        var connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context!!.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager

        return Configuration(
            apiKey = "api-key",
            context = context,
            instanceName = "testInstance",
            minTimeBetweenSessionsMillis = 100,
            storageProvider = storageProvider ?: InMemoryStorageProvider(),
            trackingSessionEvents = true,
            loggerProvider = ConsoleLoggerProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider()
        )
    }

    @Test
    fun amplitude_closeBackgroundEventsShouldNotStartNewSession() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.track(createEvent(1000, "test event 1"))
        amplitude.track(createEvent(1050, "test event 2"))

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }

        tracks.sortBy { event -> event.eventId }

        Assertions.assertEquals(3, tracks.count())

        var event = tracks[0]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1050, event.timestamp)
    }

    @Test
    fun amplitude_distantBackgroundEventsShouldStartNewSession() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.track(createEvent(1000, "test event 1"))
        amplitude.track(createEvent(2000, "test event 2"))

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }

        tracks.sortBy { event -> event.eventId }

        Assertions.assertEquals(5, tracks.count())

        var event = tracks[0]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals(Amplitude.END_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[3]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(2000, event.sessionId)
        Assertions.assertEquals(2000, event.timestamp)

        event = tracks[4]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(2000, event.sessionId)
        Assertions.assertEquals(2000, event.timestamp)
    }

    @Test
    fun amplitude_foregroundEventsShouldNotStartNewSession() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.onEnterForeground(1000)
        amplitude.track(createEvent(1050, "test event 1"))
        amplitude.track(createEvent(2000, "test event 2"))

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }

        tracks.sortBy { event -> event.eventId }

        Assertions.assertEquals(3, tracks.count())

        var event = tracks[0]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1050, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(2000, event.timestamp)
    }

    @Test
    fun amplitude_closeBackgroundForegroundEventsShouldNotStartNewSession() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.track(createEvent(1000, "test event 1"))
        amplitude.onEnterForeground(1050)
        amplitude.track(createEvent(2000, "test event 2"))

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }

        tracks.sortBy { event -> event.eventId }

        Assertions.assertEquals(3, tracks.count())

        var event = tracks[0]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(2000, event.timestamp)
    }

    @Test
    fun amplitude_distantBackgroundForegroundEventsShouldStartNewSession() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.track(createEvent(1000, "test event 1"))
        amplitude.onEnterForeground(2000)
        amplitude.track(createEvent(3000, "test event 2"))

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }

        tracks.sortBy { event -> event.eventId }

        Assertions.assertEquals(5, tracks.count())

        var event = tracks[0]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals(Amplitude.END_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[3]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(2000, event.sessionId)
        Assertions.assertEquals(2000, event.timestamp)

        event = tracks[4]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(2000, event.sessionId)
        Assertions.assertEquals(3000, event.timestamp)
    }

    @Test
    fun amplitude_closeForegroundBackgroundEventsShouldNotStartNewSession() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.onEnterForeground(1000)
        amplitude.track(createEvent(1500, "test event 1"))
        amplitude.onExitForeground(2000)
        amplitude.track(createEvent(2050, "test event 2"))

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }

        tracks.sortBy { event -> event.eventId }

        Assertions.assertEquals(3, tracks.count())

        var event = tracks[0]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1500, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(2050, event.timestamp)
    }

    @Test
    fun amplitude_distantForegroundBackgroundEventsShouldStartNewSession() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.onEnterForeground(1000)
        amplitude.track(createEvent(1500, "test event 1"))
        amplitude.onExitForeground(2000)
        amplitude.track(createEvent(3000, "test event 2"))

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }

        tracks.sortBy { event -> event.eventId }

        Assertions.assertEquals(5, tracks.count())

        var event = tracks[0]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1500, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals(Amplitude.END_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(2000, event.timestamp)

        event = tracks[3]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(3000, event.sessionId)
        Assertions.assertEquals(3000, event.timestamp)

        event = tracks[4]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(3000, event.sessionId)
        Assertions.assertEquals(3000, event.timestamp)
    }

    @Test
    fun amplitude_sessionDataShouldBePersisted() = runTest {
        val storageProvider = InstanceStorageProvider(InMemoryStorage())

        val amplitude1 = Amplitude(createConfiguration(storageProvider))
        setDispatcher(amplitude1, testScheduler)
        amplitude1.isBuilt.await()

        amplitude1.onEnterForeground(1000)

        advanceUntilIdle()
        Thread.sleep(100)

        val timeline1 = amplitude1.timeline as Timeline

        Assertions.assertEquals(1000, amplitude1.sessionId)
        Assertions.assertEquals(1000, timeline1.sessionId)
        Assertions.assertEquals(1000, timeline1.lastEventTime)
        Assertions.assertEquals(1, timeline1.lastEventId)

        amplitude1.track(createEvent(1200, "test event 1"))

        advanceUntilIdle()
        Thread.sleep(100)

        Assertions.assertEquals(1000, amplitude1.sessionId)
        Assertions.assertEquals(1000, timeline1.sessionId)
        Assertions.assertEquals(1200, timeline1.lastEventTime)
        Assertions.assertEquals(2, timeline1.lastEventId)

        val amplitude2 = Amplitude(createConfiguration(storageProvider))
        setDispatcher(amplitude2, testScheduler)
        amplitude2.isBuilt.await()

        advanceUntilIdle()
        Thread.sleep(100)

        val timeline2 = amplitude2.timeline as Timeline
        Assertions.assertEquals(1000, amplitude2.sessionId)
        Assertions.assertEquals(1000, timeline2.sessionId)
        Assertions.assertEquals(1200, timeline2.lastEventTime)
        Assertions.assertEquals(2, timeline2.lastEventId)
    }

    @Test
    fun amplitude_explicitSessionForEventShouldBePreserved() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.track(createEvent(1000, "test event 1"))
        amplitude.track(createEvent(1050, "test event 2", 3000))
        amplitude.track(createEvent(1100, "test event 3"))

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }

        tracks.sortBy { event -> event.eventId }

        Assertions.assertEquals(4, tracks.count())

        var event = tracks[0]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(3000, event.sessionId)
        Assertions.assertEquals(1050, event.timestamp)

        event = tracks[3]
        Assertions.assertEquals("test event 3", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1100, event.timestamp)
    }

    @Test
    fun amplitude_explicitNoSessionForEventShouldBePreserved() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.track(createEvent(1000, "test event 1"))
        amplitude.track(createEvent(1050, "test event 2", -1))
        amplitude.track(createEvent(1100, "test event 3"))

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }

        tracks.sortBy { event -> event.eventId }

        Assertions.assertEquals(4, tracks.count())

        var event = tracks[0]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1000, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(-1, event.sessionId)
        Assertions.assertEquals(1050, event.timestamp)

        event = tracks[3]
        Assertions.assertEquals("test event 3", event.eventType)
        Assertions.assertEquals(1000, event.sessionId)
        Assertions.assertEquals(1100, event.timestamp)
    }

    @Suppress("DEPRECATION")
    @Test
    fun amplitude_noSessionEventsWhenDisabledWithTrackingSessionEvents() = runTest {
        val configuration = createConfiguration()
        configuration.trackingSessionEvents = false
        val amplitude = Amplitude(configuration)
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.track(createEvent(1000, "test event"))

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }
        Assertions.assertEquals(1, tracks.count())
    }

    @Test
    fun amplitude_noSessionEventsWhenDisabledWithDefaultTrackingOptions() = runTest {
        val configuration = createConfiguration()
        configuration.defaultTracking.sessions = false
        val amplitude = Amplitude(configuration)
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.track(createEvent(1000, "test event"))

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }
        Assertions.assertEquals(1, tracks.count())
    }

    private fun createEvent(timestamp: Long, eventType: String, sessionId: Long? = null): BaseEvent {
        val event = BaseEvent()
        event.userId = "user"
        event.timestamp = timestamp
        event.eventType = eventType
        event.sessionId = sessionId
        return event
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
