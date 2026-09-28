package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.MainDispatcherRule
import com.amplitude.android.utilities.FakeAndroidAmplitude
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AmplitudeSessionTest {
    private fun createConfiguration(
        storageProvider: StorageProvider? = null,
        shouldTrackSessions: Boolean = true,
        optOut: Boolean = false,
    ): Configuration {
        setupMockAndroidContext()
        val context = mockk<Application>(relaxed = true)
        every { context.applicationContext } returns context
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
            optOut = optOut,
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

            // Out-of-session event (sessionId=-1) should NOT extend the session.
            // So test event 3 at t=1100 sees lastEventTime=1000, and 1100-1000=100 >= 100ms timeout,
            // which triggers a new session.
            assertEquals(6, tracks.count())

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

            // Session timed out: session_end for old session, session_start for new
            event = tracks[3]
            assertEquals(Amplitude.END_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = tracks[4]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(1100L, event.sessionId)
            assertEquals(1100L, event.timestamp)

            event = tracks[5]
            assertEquals("test event 3", event.eventType)
            assertEquals(1100L, event.sessionId)
            assertEquals(1100L, event.timestamp)
        }

    @Test
    fun amplitude_backgroundOutOfSessionEventShouldNotCreateSessionEvents() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )

            val mockedPlugin = spyk(FakeEventPlugin())
            amplitude.add(mockedPlugin)

            amplitude.isBuilt.await()

            amplitude.track(createEvent(800, "setup event"))
            amplitude.track(createEvent(1000, "out of session event", -1))

            advanceUntilIdle()
            Thread.sleep(100)

            val tracks = mutableListOf<BaseEvent>()

            verify {
                mockedPlugin.track(capture(tracks))
            }

            tracks.sortBy { event -> event.eventId }

            // 3 events (not 5 as in amplitude_distantBackgroundEventsShouldStartNewSession):
            // out-of-session event should not trigger session_end + session_start
            assertEquals(3, tracks.count())

            var event = tracks[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(800L, event.sessionId)
            assertEquals(800L, event.timestamp)

            event = tracks[1]
            assertEquals("setup event", event.eventType)
            assertEquals(800L, event.sessionId)
            assertEquals(800L, event.timestamp)

            event = tracks[2]
            assertEquals("out of session event", event.eventType)
            assertEquals(-1L, event.sessionId)
            assertEquals(1000L, event.timestamp)
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

    @Test
    fun `should start a new session after opting in on a cold start with an expired session`() =
        runTest {
            val storageProvider = InstanceStorageProvider(InMemoryStorage())
            val previousAmplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(storageProvider, shouldTrackSessions = false),
                )
            previousAmplitude.isBuilt.await()
            previousAmplitude.track(createEvent(1000L, "previous event"))
            advanceUntilIdle()

            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(storageProvider, shouldTrackSessions = false, optOut = true),
                )
            val eventPlugin = FakeEventPlugin()
            amplitude.add(eventPlugin)
            amplitude.isBuilt.await()
            assertEquals(1000L, amplitude.sessionId)

            enterForeground(amplitude, 2000L)
            amplitude.track(createEvent(2025L, "opted out event"))
            advanceUntilIdle()
            assertEquals(0, eventPlugin.trackedEvents.size)
            assertEquals(1000L, (amplitude.timeline as Timeline).lastEventTime)

            amplitude.configuration.optOut = false
            amplitude.track(createEvent(2050L, "opted in event"))
            advanceUntilIdle()

            val event = eventPlugin.trackedEvents.single()
            assertEquals("opted in event", event.eventType)
            assertEquals(2050L, event.sessionId)
            assertEquals(2050L, amplitude.sessionId)

            amplitude.track(createEvent(3000L, "later foreground event"))
            advanceUntilIdle()
            assertEquals(2050L, eventPlugin.trackedEvents.last().sessionId)
        }

    @Test
    fun `should resume a recent saved session after opting in on a cold start`() =
        runTest {
            val amplitude = createOptedOutAmplitudeWithSavedSession()
            val eventPlugin = FakeEventPlugin()
            amplitude.add(eventPlugin)

            enterForeground(amplitude, 1025L)
            amplitude.optOut = false
            amplitude.track(createEvent(1050L, "opted in event"))
            advanceUntilIdle()

            assertEquals(1000L, eventPlugin.trackedEvents.single().sessionId)

            amplitude.track(createEvent(2000L, "later foreground event"))
            advanceUntilIdle()
            assertEquals(1000L, eventPlugin.trackedEvents.last().sessionId)
        }

    @Test
    fun `should not extend an unchecked saved session when backgrounded while opted out`() =
        runTest {
            val amplitude = createOptedOutAmplitudeWithSavedSession()
            val eventPlugin = FakeEventPlugin()
            amplitude.add(eventPlugin)

            enterForeground(amplitude, 2000L)
            exitForeground(amplitude, 2025L)
            assertEquals(1000L, (amplitude.timeline as Timeline).lastEventTime)
            assertEquals(0, eventPlugin.trackedEvents.size)

            amplitude.optOut = false
            amplitude.track(createEvent(2050L, "opted in event"))
            advanceUntilIdle()
            assertEquals(2050L, eventPlugin.trackedEvents.single().sessionId)

            amplitude.track(createEvent(3000L, "later background event"))
            advanceUntilIdle()
            assertEquals(3000L, eventPlugin.trackedEvents.last().sessionId)
        }

    @Test
    fun `should emit session events after opting in with an expired saved session`() =
        runTest {
            val amplitude = createOptedOutAmplitudeWithSavedSession(shouldTrackSessions = true)
            val eventPlugin = FakeEventPlugin()
            amplitude.add(eventPlugin)

            enterForeground(amplitude, 2000L)
            assertEquals(0, eventPlugin.trackedEvents.size)

            amplitude.optOut = false
            amplitude.track(createEvent(2050L, "opted in event"))
            advanceUntilIdle()

            val events = eventPlugin.trackedEvents.sortedBy { it.eventId }
            assertEquals(listOf(Amplitude.END_SESSION_EVENT, Amplitude.START_SESSION_EVENT, "opted in event"), events.map { it.eventType })
            assertEquals(listOf(1000L, 2050L, 2050L), events.map { it.sessionId })
            assertEquals(listOf(1000L, 2050L, 2050L), events.map { it.timestamp })
        }

    @Test
    fun `should keep an explicitly started session active in the foreground after opting in`() =
        runTest {
            val amplitude = createOptedOutAmplitudeWithSavedSession()
            val eventPlugin = FakeEventPlugin()
            amplitude.add(eventPlugin)

            enterForeground(amplitude, 2000L)
            amplitude.optOut = false
            amplitude.track(createEvent(2050L, Amplitude.START_SESSION_EVENT))
            amplitude.track(createEvent(3000L, "later foreground event"))
            advanceUntilIdle()

            assertEquals(listOf(2050L, 2050L), eventPlugin.trackedEvents.map { it.sessionId })
        }

    @Test
    fun amplitude_optInAfterForegroundWhileOptedOutShouldStartSession() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(optOut = true),
                )

            val fakeEventPlugin = FakeEventPlugin()
            amplitude.add(fakeEventPlugin)

            amplitude.isBuilt.await()

            // The app foregrounds while opted out, so no session is started. Regression guard for
            // issue #154: the foreground signal must still leave the timeline able to open a session
            // once the user opts in, even though it produced nothing at the time.
            enterForeground(amplitude, 1000L)
            amplitude.track(createEvent(1050L, "opted out event"))

            advanceUntilIdle()
            Thread.sleep(100)

            assertEquals(0, fakeEventPlugin.trackedEvents.count())
            assertEquals(-1L, amplitude.sessionId)

            amplitude.optOut = false
            amplitude.track(createEvent(2000L, "opted in event"))

            advanceUntilIdle()
            Thread.sleep(100)

            val trackedEvents = fakeEventPlugin.trackedEvents

            trackedEvents.sortBy { event -> event.eventId }

            assertEquals(2, trackedEvents.count())

            var event = trackedEvents[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(2000L, event.sessionId)
            assertEquals(2000L, event.timestamp)

            event = trackedEvents[1]
            assertEquals("opted in event", event.eventType)
            assertEquals(2000L, event.sessionId)
            assertEquals(2000L, event.timestamp)

            assertEquals(2000L, amplitude.sessionId)
        }

    @Test
    fun amplitude_optInAfterForegroundWhileOptedOutShouldRotateSessionNormally() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(optOut = true),
                )

            val fakeEventPlugin = FakeEventPlugin()
            amplitude.add(fakeEventPlugin)

            amplitude.isBuilt.await()

            enterForeground(amplitude, 1000L)
            amplitude.optOut = false
            amplitude.track(createEvent(2000L, "test event 1"))
            exitForeground(amplitude, 2100L)
            amplitude.track(createEvent(5000L, "test event 2"))

            advanceUntilIdle()
            Thread.sleep(100)

            val trackedEvents = fakeEventPlugin.trackedEvents

            trackedEvents.sortBy { event -> event.eventId }

            // The session recovered on opt-in behaves like any other: it times out in the
            // background and rotates.
            assertEquals(5, trackedEvents.count())

            var event = trackedEvents[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(2000L, event.sessionId)
            assertEquals(2000L, event.timestamp)

            event = trackedEvents[1]
            assertEquals("test event 1", event.eventType)
            assertEquals(2000L, event.sessionId)
            assertEquals(2000L, event.timestamp)

            event = trackedEvents[2]
            assertEquals(Amplitude.END_SESSION_EVENT, event.eventType)
            assertEquals(2000L, event.sessionId)
            assertEquals(2100L, event.timestamp)

            event = trackedEvents[3]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(5000L, event.sessionId)
            assertEquals(5000L, event.timestamp)

            event = trackedEvents[4]
            assertEquals("test event 2", event.eventType)
            assertEquals(5000L, event.sessionId)
            assertEquals(5000L, event.timestamp)
        }

    @Test
    fun amplitude_optOutMidSessionShouldPreserveSessionId() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )

            val fakeEventPlugin = FakeEventPlugin()
            amplitude.add(fakeEventPlugin)

            amplitude.isBuilt.await()

            enterForeground(amplitude, 1000L)
            amplitude.track(createEvent(1050L, "test event 1"))
            amplitude.optOut = true
            amplitude.track(createEvent(1100L, "opted out event"))
            amplitude.optOut = false
            amplitude.track(createEvent(1150L, "test event 2"))

            advanceUntilIdle()
            Thread.sleep(100)

            val trackedEvents = fakeEventPlugin.trackedEvents

            trackedEvents.sortBy { event -> event.eventId }

            // Opting out drops the event but leaves the live session intact, so opting back in
            // resumes the same session rather than starting a new one.
            assertEquals(3, trackedEvents.count())

            var event = trackedEvents[0]
            assertEquals(Amplitude.START_SESSION_EVENT, event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1000L, event.timestamp)

            event = trackedEvents[1]
            assertEquals("test event 1", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1050L, event.timestamp)

            event = trackedEvents[2]
            assertEquals("test event 2", event.eventType)
            assertEquals(1000L, event.sessionId)
            assertEquals(1150L, event.timestamp)
        }

    private suspend fun TestScope.createOptedOutAmplitudeWithSavedSession(shouldTrackSessions: Boolean = false): FakeAndroidAmplitude {
        val storage = InMemoryStorage()
        storage.write(Storage.Constants.PREVIOUS_SESSION_ID, "1000")
        storage.write(Storage.Constants.LAST_EVENT_TIME, "1000")
        val amplitude =
            createFakeAmplitude(
                scheduler = testScheduler,
                configuration = createConfiguration(InstanceStorageProvider(storage), shouldTrackSessions, optOut = true),
            )
        amplitude.isBuilt.await()
        advanceUntilIdle()
        assertEquals(1000L, amplitude.sessionId)
        return amplitude
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
