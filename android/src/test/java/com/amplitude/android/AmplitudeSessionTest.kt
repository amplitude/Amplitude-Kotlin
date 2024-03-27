package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.android.utils.mockSystemTime
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
        mockSystemTime(StartTime)

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
            defaultTracking = DefaultTrackingOptions(sessions = true),
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

        amplitude.track(createEvent(StartTime, "test event 1"))
        val event2Time = StartTime + 50
        amplitude.track(createEvent(event2Time, "test event 2"))

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
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(event2Time, event.timestamp)
    }

    @Test
    fun amplitude_distantBackgroundEventsShouldStartNewSession() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.track(createEvent(StartTime, "test event 1"))
        val event2Time = mockSystemTime(StartTime + 1000)
        amplitude.track(createEvent(event2Time, "test event 2"))

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
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals(Amplitude.END_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[3]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(event2Time, event.sessionId)
        Assertions.assertEquals(event2Time, event.timestamp)

        event = tracks[4]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(event2Time, event.sessionId)
        Assertions.assertEquals(event2Time, event.timestamp)
    }

    @Test
    fun amplitude_foregroundEventsShouldNotStartNewSession() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.onEnterForeground(StartTime)
        val event1Time = StartTime + 50
        amplitude.track(createEvent(event1Time, "test event 1"))
        val event2Time = event1Time + 50
        amplitude.track(createEvent(event2Time, "test event 2"))

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
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(event1Time, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(event2Time, event.timestamp)
    }

    @Test
    fun amplitude_closeBackgroundForegroundEventsShouldNotStartNewSession() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.track(createEvent(StartTime, "test event 1"))
        amplitude.onEnterForeground(1050)
        val event2Time = StartTime + 1000
        amplitude.track(createEvent(event2Time, "test event 2"))

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
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(event2Time, event.timestamp)
    }

    @Test
    fun amplitude_distantBackgroundForegroundEventsShouldStartNewSession() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        amplitude.track(createEvent(StartTime, "test event 1"))
        val enterForegroundTime = mockSystemTime(StartTime + 1000)
        amplitude.onEnterForeground(enterForegroundTime)
        val event2Time = mockSystemTime(StartTime + 2000)
        amplitude.track(createEvent(event2Time, "test event 2"))

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
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals(Amplitude.END_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[3]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(enterForegroundTime, event.sessionId)
        Assertions.assertEquals(enterForegroundTime, event.timestamp)

        event = tracks[4]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(enterForegroundTime, event.sessionId)
        Assertions.assertEquals(event2Time, event.timestamp)
    }

    @Test
    fun amplitude_closeForegroundBackgroundEventsShouldNotStartNewSession() = runTest {
        val mockedPlugin = spyk(StubPlugin())

        val config = createConfiguration()
        config.plugins = listOf(mockedPlugin)

        val amplitude = Amplitude(config)

        setDispatcher(amplitude, testScheduler)
        amplitude.isBuilt.await()

        val event1Time = StartTime + 500
        val exitForegroundTime = StartTime + 1000
        val event2Time = exitForegroundTime + 50

        amplitude.onEnterForeground(StartTime)
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
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(event1Time, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(event2Time, event.timestamp)
    }

    @Test
    fun amplitude_distantForegroundBackgroundEventsShouldStartNewSession() = runTest {
        // set up config
        val config = createConfiguration()
        val mockedPlugin = spyk(StubPlugin())
        config.plugins = listOf(mockedPlugin)

        // create instance (starts session) (1)
        val amplitude = Amplitude(config)
        setDispatcher(amplitude, testScheduler)
        amplitude.isBuilt.await()

        // Enter FG (2)
        amplitude.onEnterForeground(StartTime)
        val event1Time = mockSystemTime(StartTime + 500)
        // Track (3)
        amplitude.track(createEvent(event1Time, "test event 1"))
        // Exit FG (4)
        val exitForegroundTime = mockSystemTime(StartTime + 1000)
        amplitude.onExitForeground(exitForegroundTime)
        // Track (5)
        val event2Time = mockSystemTime(exitForegroundTime + 1000)
        amplitude.track(createEvent(event2Time, "test event 2"))

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
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(event1Time, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals(Amplitude.END_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(exitForegroundTime, event.timestamp)

        event = tracks[3]
        Assertions.assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
        Assertions.assertEquals(event2Time, event.sessionId)
        Assertions.assertEquals(event2Time, event.timestamp)

        event = tracks[4]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(event2Time, event.sessionId)
        Assertions.assertEquals(event2Time, event.timestamp)
    }

    @Test
    fun amplitude_sessionDataShouldBePersisted() = runTest {
        val storageProvider = InstanceStorageProvider(InMemoryStorage())

        val amplitude1 = Amplitude(createConfiguration(storageProvider))
        setDispatcher(amplitude1, testScheduler)
        amplitude1.isBuilt.await()

        amplitude1.onEnterForeground(StartTime)

        advanceUntilIdle()
        Thread.sleep(100)

        val timeline1 = getAndroidTimeline(amplitude1)
        Assertions.assertEquals(StartTime, timeline1.session.sessionId)
        Assertions.assertEquals(StartTime, timeline1.session.lastEventTime)
        Assertions.assertEquals(1, timeline1.lastEventId)

        val event1Time = mockSystemTime(StartTime + 200)
        amplitude1.track(createEvent(event1Time, "test event 1"))

        advanceUntilIdle()
        Thread.sleep(100)

        Assertions.assertEquals(StartTime, timeline1.session.sessionId)
        Assertions.assertEquals(event1Time, timeline1.session.lastEventTime)
        Assertions.assertEquals(2, timeline1.lastEventId)

        // Inc time by 50ms
        val instance2CreationTime = mockSystemTime(StartTime + 250)
        // Create another instance (with same instance name, ie.e shared storage
        val amplitude2 = Amplitude(createConfiguration(storageProvider))
        setDispatcher(amplitude2, testScheduler)
        amplitude2.isBuilt.await()

        val timeline2 = getAndroidTimeline(amplitude2)
        Assertions.assertEquals(StartTime, timeline2.session.sessionId)
        // Last event time is the SDK creation time (1250)
        Assertions.assertEquals(instance2CreationTime, timeline2.session.lastEventTime)
        Assertions.assertEquals(2, timeline2.lastEventId)
    }

    @Test
    fun amplitude_explicitSessionForEventShouldBePreserved() = runTest {
        val config = createConfiguration()
        val mockedPlugin = spyk(StubPlugin())
        config.plugins = listOf(mockedPlugin)

        val amplitude = Amplitude(config)
        setDispatcher(amplitude, testScheduler)

        amplitude.isBuilt.await()

        val event1Time = StartTime
        val event2Time = StartTime + 50
        val event2SessionId = 3000L
        val event3Time = StartTime + 100

        amplitude.track(createEvent(event1Time, "test event 1"))
        amplitude.track(createEvent(event2Time, "test event 2", event2SessionId))
        amplitude.track(createEvent(event3Time, "test event 3"))

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
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(event1Time, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(event2SessionId, event.sessionId)
        Assertions.assertEquals(event2Time, event.timestamp)

        event = tracks[3]
        Assertions.assertEquals("test event 3", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(event3Time, event.timestamp)
    }

    @Test
    fun amplitude_explicitNoSessionForEventShouldBePreserved() = runTest {
        val amplitude = Amplitude(createConfiguration())
        setDispatcher(amplitude, testScheduler)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)

        amplitude.isBuilt.await()

        val event1Time = StartTime
        val event2Time = StartTime + 50
        val event2SessionId = -1L
        val event3Time = StartTime + 100

        amplitude.track(createEvent(event1Time, "test event 1"))
        amplitude.track(createEvent(event2Time, "test event 2", event2SessionId))
        amplitude.track(createEvent(event3Time, "test event 3"))

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
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(StartTime, event.timestamp)

        event = tracks[1]
        Assertions.assertEquals("test event 1", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(event1Time, event.timestamp)

        event = tracks[2]
        Assertions.assertEquals("test event 2", event.eventType)
        Assertions.assertEquals(event2SessionId, event.sessionId)
        Assertions.assertEquals(event2Time, event.timestamp)

        event = tracks[3]
        Assertions.assertEquals("test event 3", event.eventType)
        Assertions.assertEquals(StartTime, event.sessionId)
        Assertions.assertEquals(event3Time, event.timestamp)
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

        amplitude.track(createEvent(StartTime, "test event"))

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

        amplitude.track(createEvent(StartTime, "test event"))

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()

        verify {
            mockedPlugin.track(capture(tracks))
        }
        Assertions.assertEquals(1, tracks.count())
    }

    @Test
    fun amplitude_shouldStartNewSessionOnInitializationInForegroundBasedOnSessionTimeout() = runTest {
        val storageProvider = InstanceStorageProvider(InMemoryStorage())
        val config = createConfiguration(storageProvider)

        val startTime: Long = 1000
        mockSystemTime(startTime)

        // Create an instance in the background
        // This will start a new session (1)
        val amplitude1 = Amplitude(config)
        setDispatcher(amplitude1, testScheduler)
        amplitude1.isBuilt.await()

        // enter foreground (2)
        val enterForegroundTime = mockSystemTime(startTime)
        amplitude1.onEnterForeground(enterForegroundTime)

        advanceUntilIdle()
        Thread.sleep(100)

        val timeline1 = getAndroidTimeline(amplitude1)
        Assertions.assertEquals(enterForegroundTime, timeline1.session.sessionId)
        Assertions.assertEquals(enterForegroundTime, timeline1.session.lastEventTime)
        Assertions.assertEquals(1, timeline1.lastEventId)

        // track event (set last event time) (3)
        val event1Time = mockSystemTime(enterForegroundTime + 200)
        amplitude1.track(createEvent(event1Time, "test event 1"))

        advanceUntilIdle()
        Thread.sleep(100)

        // valid session and last event time
        Assertions.assertEquals(startTime, timeline1.session.sessionId)
        Assertions.assertEquals(event1Time, timeline1.session.lastEventTime)
        Assertions.assertEquals(2, timeline1.lastEventId)

        // exit foreground (4)
        val exitForegroundTime = mockSystemTime(event1Time + 100)
        amplitude1.onExitForeground(exitForegroundTime)

        advanceUntilIdle()
        Thread.sleep(100)

        // advance to new session
        val newSessionTime = mockSystemTime(exitForegroundTime + config.minTimeBetweenSessionsMillis + 100)

        // Create a new instance to simulate recreation at startup in foreground
        // This will end current session and start a new session (5 + 6)
        val amplitude2 = Amplitude(createConfiguration(storageProvider))
        setDispatcher(amplitude2, testScheduler)
        amplitude2.isBuilt.await()

        advanceUntilIdle()
        Thread.sleep(100)

        val timeline2 = getAndroidTimeline(amplitude2)
        Assertions.assertEquals(newSessionTime, timeline2.session.sessionId)
        Assertions.assertEquals(newSessionTime, timeline2.session.lastEventTime)
        // 6 events = session_start, enter foreground, track, exit foreground, session_end, session_start
        Assertions.assertEquals(6, timeline2.lastEventId)
    }

    private fun createEvent(timestamp: Long, eventType: String, sessionId: Long? = null): BaseEvent {
        val event = BaseEvent()
        event.userId = "user"
        event.timestamp = timestamp
        event.eventType = eventType
        event.sessionId = sessionId
        return event
    }

    private fun getAndroidTimeline(amplitude: Amplitude): Timeline {
        return amplitude.timeline as Timeline
    }

    companion object {
        const val instanceName = "testInstance"
        private const val StartTime: Long = 1000
    }
}

class InstanceStorageProvider(private val instance: Storage) : StorageProvider {
    override fun getStorage(amplitude: com.amplitude.core.Amplitude, prefix: String?): Storage {
        return instance
    }
}
