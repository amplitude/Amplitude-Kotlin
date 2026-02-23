package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.MainDispatcherRule
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
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.concurrent.thread

internal class FakeEventPlugin : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: com.amplitude.core.Amplitude
    internal val trackedEvents = mutableListOf<BaseEvent>()

    override fun track(payload: BaseEvent): BaseEvent? {
        trackedEvents += payload
        return super.track(payload)
    }

    override fun identify(payload: com.amplitude.core.events.IdentifyEvent): com.amplitude.core.events.IdentifyEvent? {
        trackedEvents += payload
        return super.identify(payload)
    }
}

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AmplitudeTest {
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
                Context.CONNECTIVITY_SERVICE,
            )
        } returns connectivityManager
        val dirNameSlot = slot<String>()
        every { context.getDir(capture(dirNameSlot), any()) } answers {
            File("/tmp/amplitude-kotlin/${dirNameSlot.captured}")
        }

        val configuration =
            Configuration(
                apiKey = "api-key",
                context = context,
                instanceName = INSTANCE_NAME,
                storageProvider = storageProvider,
                autocapture =
                    if (minTimeBetweenSessionsMillis != null) {
                        setOf(
                            AutocaptureOption.SESSIONS,
                        )
                    } else {
                        setOf()
                    },
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

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun amplitude_reset_wipesUserIdDeviceId() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
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
    fun amplitude_unset_country_with_remote_ip() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )
            val fakeEventPlugin = FakeEventPlugin()
            amplitude.add(fakeEventPlugin)

            amplitude.isBuilt.await()
            val event = BaseEvent()
            event.eventType = "test event"
            amplitude.track(event)
            advanceUntilIdle()
            Thread.sleep(100)

            assertTrue(fakeEventPlugin.trackedEvents.size == 1)
            with(fakeEventPlugin.trackedEvents.first()) {
                assertEquals("\$remote", ip)
                assertNull(country)
            }
        }

    @Test
    fun amplitude_fetch_country_with_customized_ip() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )
            val fakeEventPlugin = FakeEventPlugin()
            amplitude.add(fakeEventPlugin)

            amplitude.isBuilt.await()
            val event = BaseEvent()
            event.eventType = "test event"
            event.ip = "127.0.0.1"
            amplitude.track(event)
            advanceUntilIdle()
            Thread.sleep(100)

            assertTrue(fakeEventPlugin.trackedEvents.size == 1)
            with(fakeEventPlugin.trackedEvents.first()) {
                assertEquals("127.0.0.1", ip)
                assertEquals("US", country)
            }
        }

    @Test
    fun test_analytics_connector() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )

            amplitude.isBuilt.await()

            val connector = AnalyticsConnector.getInstance(INSTANCE_NAME)
            val connectorUserId = "connector user id"
            val connectorDeviceId = "connector device id"
            var connectorIdentitySet = false
            val identityListener = { _: Identity ->
                if (connectorIdentitySet) {
                    assertEquals(
                        connectorUserId,
                        connector.identityStore.getIdentity().userId,
                    )
                    assertEquals(
                        connectorDeviceId,
                        connector.identityStore.getIdentity().deviceId,
                    )
                    connectorIdentitySet = false
                }
            }
            connector.identityStore.addIdentityListener(identityListener)
            amplitude.setUserId(connectorUserId)
            amplitude.setDeviceId(connectorDeviceId)
            connectorIdentitySet = true
            advanceUntilIdle()
            connector.identityStore.removeIdentityListener(identityListener)
        }

    @Test
    fun amplitude_getDeviceId_should_return_not_null_after_isBuilt() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )
            amplitude.isBuilt.await()
            assertNotNull(amplitude.store.deviceId)
            assertNotNull(amplitude.getDeviceId())
        }

    @Test
    fun amplitude_should_set_deviceId_from_configuration() =
        runTest {
            val testDeviceId = "test device id"
            // set device Id in the config
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration =
                        createConfiguration(
                            deviceId = testDeviceId,
                        ),
                )

            amplitude.isBuilt.await()
            assertEquals(testDeviceId, amplitude.store.deviceId)
            assertEquals(testDeviceId, amplitude.getDeviceId())
        }

    @Test
    fun amplitude_should_set_sessionId_from_configuration() =
        runTest {
            val testSessionId = 1337L
            // set device Id in the config
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(sessionId = testSessionId),
                )

            amplitude.isBuilt.await()
            assertEquals(testSessionId, amplitude.sessionId)
        }

    /**
     * Here is what we want to test. In the older version of the SDK, we were unintentionally
     * extending a session. Here's how enter foreground was being handled before.
     *  1. Application is entering foreground
     *  2. Amplitude.inForeground flag is set to true.
     *  3. Immediately after this, we expect the foreground event to be processed and create
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
    fun amplitude_should_correctly_start_new_session() =
        runTest {
            val testSessionId = 1000L

            val amplitude =
                object : Amplitude(
                    createConfiguration(sessionId = testSessionId, minTimeBetweenSessionsMillis = 50),
                ) {}

            amplitude.isBuilt.await()
            // Fire a delayed foreground event.
            val thread1 =
                thread {
                    Thread.sleep(500)
                    (amplitude.timeline as Timeline).onEnterForeground(1120)
                }
            Thread.sleep(100)

            // Fire a test event that will be before the foreground event.
            val thread2 =
                thread {
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

    @Test
    fun setUserId_then_track_event_has_new_userId() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )
            val fakeEventPlugin = FakeEventPlugin()
            amplitude.add(fakeEventPlugin)
            amplitude.isBuilt.await()
            advanceUntilIdle()

            amplitude.setUserId("new-user")
            amplitude.track("test_event")
            advanceUntilIdle()

            val event = fakeEventPlugin.trackedEvents.first { it.eventType == "test_event" }
            assertEquals("new-user", event.userId)
        }

    @Test
    fun setDeviceId_then_track_event_has_new_deviceId() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )
            val fakeEventPlugin = FakeEventPlugin()
            amplitude.add(fakeEventPlugin)
            amplitude.isBuilt.await()
            advanceUntilIdle()

            amplitude.setDeviceId("custom-device")
            amplitude.track("test_event")
            advanceUntilIdle()

            val event = fakeEventPlugin.trackedEvents.first { it.eventType == "test_event" }
            assertEquals("custom-device", event.deviceId)
        }

    @Test
    fun identify_with_userId_option_then_track_both_have_new_userId() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )
            val fakeEventPlugin = FakeEventPlugin()
            amplitude.add(fakeEventPlugin)
            amplitude.isBuilt.await()
            advanceUntilIdle()

            val identify = com.amplitude.core.events.Identify().set("key", "value")
            amplitude.identify(
                identify,
                com.amplitude.core.events.EventOptions().apply { userId = "identify-user" },
            )
            amplitude.track("test_event")
            advanceUntilIdle()

            val identifyEvent =
                fakeEventPlugin.trackedEvents.first { it.eventType == "\$identify" }
            assertEquals("identify-user", identifyEvent.userId)

            val trackEvent = fakeEventPlugin.trackedEvents.first { it.eventType == "test_event" }
            assertEquals("identify-user", trackEvent.userId)
        }

    @Test
    fun reset_then_track_event_has_null_userId_and_new_deviceId() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = createConfiguration(),
                )
            val fakeEventPlugin = FakeEventPlugin()
            amplitude.add(fakeEventPlugin)
            amplitude.isBuilt.await()
            advanceUntilIdle()

            amplitude.setUserId("old-user")
            amplitude.setDeviceId("old-device")
            advanceUntilIdle()
            assertEquals("old-user", amplitude.getUserId())
            assertEquals("old-device", amplitude.getDeviceId())

            amplitude.reset()
            amplitude.track("test_event")
            advanceUntilIdle()

            val event = fakeEventPlugin.trackedEvents.first { it.eventType == "test_event" }
            assertNull(event.userId)
            assertNotEquals("old-device", event.deviceId)
            assertNotNull(event.deviceId)
        }

    companion object {
        private const val INSTANCE_NAME = "testInstance"
    }
}
