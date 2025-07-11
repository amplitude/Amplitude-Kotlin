package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.MainDispatcherRule
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.android.utilities.enterForeground
import com.amplitude.android.utilities.exitForeground
import com.amplitude.android.utilities.setupMockAndroidContext
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorage
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

@ExperimentalCoroutinesApi
class AmplitudeSessionTest {
    private fun createConfiguration(
        storageProvider: StorageProvider? = null,
        shouldTrackSessions: Boolean = true,
    ): Configuration {
        setupMockAndroidContext()
        val context = mockk<Application>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        val dirNameSlot = slot<String>()
        every { context.getDir(capture(dirNameSlot), any()) } answers {
            File("/tmp/amplitude-kotlin/${dirNameSlot.captured}")
        }

        return Configuration(
            apiKey = "api-key",
            context = context,
            instanceName = "testInstance",
            minTimeBetweenSessionsMillis = 100,
            storageProvider = storageProvider ?: InMemoryStorageProvider(),
            autocapture =
                autocaptureOptions {
                    if (shouldTrackSessions) {
                        +sessions
                    }
                },
            loggerProvider = ConsoleLoggerProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
        )
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun amplitude_closeBackgroundEventsShouldNotStartNewSession() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    server = null,
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )

            val fakeEventPlugin = FakeEventPlugin()
            amplitude.add(fakeEventPlugin)

            amplitude.isBuilt.await()

            amplitude.track(createEvent(1000, "test event 1"))
            amplitude.track(createEvent(1050L, "test event 2"))

            advanceUntilIdle()
            Thread.sleep(100)

            val trackedEvents = fakeEventPlugin.trackedEvents

            trackedEvents.sortBy { event -> event.eventId }

            assertEquals(3, trackedEvents.size)

            var event = trackedEvents[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = trackedEvents[1]
            assertEquals("test event 1", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = trackedEvents[2]
            assertEquals("test event 2", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1050L, event.timestamp)
        }

    @Test
    fun amplitude_distantBackgroundEventsShouldStartNewSession() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )

            val mockedPlugin = spyk(FakeEventPlugin())
            amplitude.add(mockedPlugin)

            amplitude.isBuilt.await()

            amplitude.track(createEvent(1000, "test event 1"))
            amplitude.track(createEvent(2000L, "test event 2"))

            advanceUntilIdle()
            Thread.sleep(100)

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }

            tracks.sortBy { event -> event.eventId }

            assertEquals(5, tracks.count())

            var event = tracks[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[1]
            assertEquals("test event 1", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[2]
            assertEquals(Amplitude.END_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[3]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(2000L, event.sessionId)
            assertEquals(2000L, event.timestamp)

            event = tracks[4]
            assertEquals("test event 2", event.eventType)
            assertEquals(2000L, event.sessionId)
            assertEquals(2000L, event.timestamp)
        }

    @Test
    fun amplitude_foregroundEventsShouldNotStartNewSession() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )

            val mockedPlugin = spyk(FakeEventPlugin())
            amplitude.add(mockedPlugin)

            amplitude.isBuilt.await()

            enterForeground(amplitude, 1000)
            amplitude.track(createEvent(1050L, "test event 1"))
            amplitude.track(createEvent(2000L, "test event 2"))

            advanceUntilIdle()
            Thread.sleep(100)

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }

            tracks.sortBy { event -> event.eventId }

            assertEquals(3, tracks.count())

            var event = tracks[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[1]
            assertEquals("test event 1", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1050L, event.timestamp)

            event = tracks[2]
            assertEquals("test event 2", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(2000L, event.timestamp)
        }

    @Test
    fun amplitude_closeBackgroundForegroundEventsShouldNotStartNewSession() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )

            val mockedPlugin = spyk(FakeEventPlugin())
            amplitude.add(mockedPlugin)

            amplitude.isBuilt.await()

            amplitude.track(createEvent(1000, "test event 1"))
            enterForeground(amplitude, 1050L)
            amplitude.track(createEvent(2000L, "test event 2"))

            advanceUntilIdle()
            Thread.sleep(100)

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }

            tracks.sortBy { event -> event.eventId }

            assertEquals(3, tracks.count())

            var event = tracks[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[1]
            assertEquals("test event 1", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[2]
            assertEquals("test event 2", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(2000L, event.timestamp)
        }

    @Test
    fun amplitude_distantBackgroundForegroundEventsShouldStartNewSession() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )

            val mockedPlugin = spyk(FakeEventPlugin())
            amplitude.add(mockedPlugin)

            amplitude.isBuilt.await()

            amplitude.track(createEvent(1000, "test event 1"))
            enterForeground(amplitude, 2000L)
            amplitude.track(createEvent(3000L, "test event 2"))

            advanceUntilIdle()
            Thread.sleep(100)

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }

            tracks.sortBy { event -> event.eventId }

            assertEquals(5, tracks.count())

            var event = tracks[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[1]
            assertEquals("test event 1", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[2]
            assertEquals(Amplitude.END_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[3]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(2000L, event.sessionId)
            assertEquals(2000L, event.timestamp)

            event = tracks[4]
            assertEquals("test event 2", event.eventType)
            assertEquals(2000L, event.sessionId)
            assertEquals(3000L, event.timestamp)
        }

    @Test
    fun amplitude_closeForegroundBackgroundEventsShouldNotStartNewSession() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )

            val mockedPlugin = spyk(FakeEventPlugin())
            amplitude.add(mockedPlugin)

            amplitude.isBuilt.await()

            enterForeground(amplitude, 1000)
            amplitude.track(createEvent(1500, "test event 1"))
            exitForeground(amplitude, 2000L)
            amplitude.track(createEvent(2050, "test event 2"))

            advanceUntilIdle()
            Thread.sleep(100)

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }

            tracks.sortBy { event -> event.eventId }

            assertEquals(3, tracks.count())

            var event = tracks[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[1]
            assertEquals("test event 1", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1500L, event.timestamp)

            event = tracks[2]
            assertEquals("test event 2", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(2050L, event.timestamp)
        }

    @Test
    fun amplitude_distantForegroundBackgroundEventsShouldStartNewSession() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )

            val mockedPlugin = spyk(FakeEventPlugin())
            amplitude.add(mockedPlugin)

            amplitude.isBuilt.await()

            enterForeground(amplitude, 1000)
            amplitude.track(createEvent(1500, "test event 1"))
            exitForeground(amplitude, 2000L)
            amplitude.track(createEvent(3000L, "test event 2"))

            advanceUntilIdle()
            Thread.sleep(100)

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }

            tracks.sortBy { event -> event.eventId }

            assertEquals(5, tracks.count())

            var event = tracks[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[1]
            assertEquals("test event 1", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1500L, event.timestamp)

            event = tracks[2]
            assertEquals(Amplitude.END_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(2000L, event.timestamp)

            event = tracks[3]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(3000L, event.sessionId)
            assertEquals(3000L, event.timestamp)

            event = tracks[4]
            assertEquals("test event 2", event.eventType)
            assertEquals(3000L, event.sessionId)
            assertEquals(3000L, event.timestamp)
        }

    @Test
    fun amplitude_sessionDataShouldBePersisted() =
        runTest {
            val storageProvider = InstanceStorageProvider(InMemoryStorage())
            val amplitude1 =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(storageProvider),
                )
            amplitude1.isBuilt.await()

            enterForeground(amplitude1, 1000)

            advanceUntilIdle()
            Thread.sleep(100)

            val timeline1 = amplitude1.timeline as Timeline

            assertEquals(1000L, amplitude1.sessionId)
            assertEquals(1000L, timeline1.sessionId)
            assertEquals(1000L, timeline1.lastEventTime)
            assertEquals(1, timeline1.lastEventId)

            amplitude1.track(createEvent(1200, "test event 1"))

            advanceUntilIdle()
            Thread.sleep(100)

            assertEquals(1000L, amplitude1.sessionId)
            assertEquals(1000L, timeline1.sessionId)
            assertEquals(1200, timeline1.lastEventTime)
            assertEquals(2, timeline1.lastEventId)

            val amplitude2 =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(storageProvider),
                )
            amplitude2.isBuilt.await()

            advanceUntilIdle()
            Thread.sleep(100)

            val timeline2 = amplitude2.timeline as Timeline
            assertEquals(1000L, amplitude2.sessionId)
            assertEquals(1000L, timeline2.sessionId)
            assertEquals(1200, timeline2.lastEventTime)
            assertEquals(2, timeline2.lastEventId)
        }

    @Test
    fun amplitude_explicitSessionForEventShouldBePreserved() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )

            val mockedPlugin = spyk(FakeEventPlugin())
            amplitude.add(mockedPlugin)

            amplitude.isBuilt.await()

            amplitude.track(createEvent(1000, "test event 1"))
            amplitude.track(createEvent(1050L, "test event 2", 3000L))
            amplitude.track(createEvent(1100L, "test event 3"))

            advanceUntilIdle()
            Thread.sleep(100)

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }

            tracks.sortBy { event -> event.eventId }

            assertEquals(4, tracks.count())

            var event = tracks[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[1]
            assertEquals("test event 1", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[2]
            assertEquals("test event 2", event.eventType)
            assertEquals(3000L, event.sessionId)
            assertEquals(1050L, event.timestamp)

            event = tracks[3]
            assertEquals("test event 3", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1100L, event.timestamp)
        }

    @Test
    fun amplitude_explicitNoSessionForEventShouldBePreserved() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )

            val mockedPlugin = spyk(FakeEventPlugin())
            amplitude.add(mockedPlugin)

            amplitude.isBuilt.await()

            amplitude.track(createEvent(1000, "test event 1"))
            amplitude.track(createEvent(1050L, "test event 2", -1))
            amplitude.track(createEvent(1100L, "test event 3"))

            advanceUntilIdle()
            Thread.sleep(100)

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }

            tracks.sortBy { event -> event.eventId }

            assertEquals(4, tracks.count())

            var event = tracks[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[1]
            assertEquals("test event 1", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[2]
            assertEquals("test event 2", event.eventType)
            assertEquals(-1L, event.sessionId)
            assertEquals(1050L, event.timestamp)

            event = tracks[3]
            assertEquals("test event 3", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1100L, event.timestamp)
        }

    @Test
    @Suppress("DEPRECATION")
    fun amplitude_noSessionEventsWhenDisabledWithTrackingSessionEvents() =
        runTest {
            val configuration = createConfiguration()
            configuration.trackingSessionEvents = false
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = configuration,
                )

            val mockedPlugin = spyk(FakeEventPlugin())
            amplitude.add(mockedPlugin)

            amplitude.isBuilt.await()

            amplitude.track(createEvent(1000, "test event"))

            advanceUntilIdle()
            Thread.sleep(100)

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }
            assertEquals(1, tracks.count())
        }

    @Test
    @Suppress("DEPRECATION")
    fun amplitude_noSessionEventsWhenDisabledWithDefaultTrackingOptions() =
        runTest {
            val configuration = createConfiguration()
            configuration.defaultTracking.sessions = false
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = configuration,
                )

            val mockedPlugin = spyk(FakeEventPlugin())
            amplitude.add(mockedPlugin)

            amplitude.isBuilt.await()

            amplitude.track(createEvent(1000, "test event"))

            advanceUntilIdle()
            Thread.sleep(100)

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }
            assertEquals(1, tracks.count())
        }

    @Test
    fun amplitude_noSessionEventsWhenDisabledWithAutocaptureOptions() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(shouldTrackSessions = false),
                )

            val mockedPlugin = spyk(FakeEventPlugin())
            amplitude.add(mockedPlugin)

            amplitude.isBuilt.await()

            amplitude.track(createEvent(1000, "test event"))

            advanceUntilIdle()
            Thread.sleep(100)

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }
            assertEquals(1, tracks.count())
        }

    private fun createEvent(
        timestamp: Long,
        eventType: String,
        sessionId: Long? = null,
    ): BaseEvent {
        val event = BaseEvent()
        event.userId = "user"
        event.timestamp = timestamp
        event.eventType = eventType
        event.sessionId = sessionId
        return event
    }
}

class InstanceStorageProvider(private val instance: Storage) : StorageProvider {
    override fun getStorage(
        amplitude: com.amplitude.core.Amplitude,
        prefix: String?,
    ): Storage {
        return instance
    }
}
