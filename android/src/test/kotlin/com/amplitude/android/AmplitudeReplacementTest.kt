package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Looper
import com.amplitude.MainDispatcherRule
import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.analytics.connector.AnalyticsEvent
import com.amplitude.analytics.connector.Identity
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.android.utilities.FakeAndroidAmplitude
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.android.utilities.setupMockAndroidContext
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@ExperimentalCoroutinesApi
@OptIn(GuardedAmplitudeFeature::class)
@RunWith(RobolectricTestRunner::class)
class AmplitudeReplacementTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = mockApplication()
    private val registered = mutableListOf<Application.ActivityLifecycleCallbacks>()
    private val unregistered = mutableListOf<Application.ActivityLifecycleCallbacks>()

    init {
        every { application.registerActivityLifecycleCallbacks(capture(registered)) } answers {}
        every { application.unregisterActivityLifecycleCallbacks(capture(unregistered)) } answers {}
    }

    @Test
    fun `newest instance with the same name takes over from the previous one`() =
        runTest {
            val first = createAmplitude("takeover")
            advanceUntilIdle()

            val second = createAmplitude("takeover")
            advanceUntilIdle()

            assertFalse(first.isActive)
            assertTrue(second.isActive)
            // Only the retired instance's own observer is unregistered.
            assertEquals(1, unregistered.size)
            assertSame(registered.first(), unregistered.first())
        }

    @Test
    fun `instances with different names stay active`() =
        runTest {
            val first = createAmplitude("isolated-a")
            val second = createAmplitude("isolated-b")
            advanceUntilIdle()

            assertTrue(first.isActive)
            assertTrue(second.isActive)
            assertTrue(unregistered.isEmpty())
        }

    @Test
    fun `a retired instance stops tracking and cannot come back`() =
        runTest {
            val first = createAmplitude("retired-tracking")
            val recorder = FakeEventPlugin()
            first.add(recorder)
            advanceUntilIdle()

            first.track(BaseEvent().apply { eventType = "before" })
            advanceUntilIdle()

            createAmplitude("retired-tracking")
            advanceUntilIdle()

            first.track(BaseEvent().apply { eventType = "after" })
            first.onEnterForeground(1_000L)
            first.onExitForeground(2_000L)
            advanceUntilIdle()

            assertEquals(listOf("before"), recorder.trackedEvents.map { it.eventType })
            assertFalse(first.isActive)
        }

    @Test
    fun `a retired instance stops session activity while still draining events`() =
        runTest {
            val first =
                createAmplitude("retired-sessions", autocapture = setOf(AutocaptureOption.SESSIONS))
            val recorder = FakeEventPlugin()
            first.add(recorder)
            advanceUntilIdle()
            val sessionIdBeforeRetirement = first.sessionId

            // Both are queued before the replacement, so both still reach the retired timeline.
            first.onEnterForeground(5_000L)
            first.track(BaseEvent().apply { eventType = "queued" })
            createAmplitude("retired-sessions")
            advanceUntilIdle()

            assertEquals(sessionIdBeforeRetirement, first.sessionId)
            assertEquals(listOf("queued"), recorder.trackedEvents.map { it.eventType })
        }

    @Test
    fun `a retired instance does not update connector user properties while draining`() =
        runTest {
            val first = createAmplitude(CONNECTOR_PROPERTIES_INSTANCE)
            advanceUntilIdle()

            // Watch every commit: the replacement's own setup resets the identity, which would
            // hide a stale write if we only looked at the final state.
            val connector = AnalyticsConnector.getInstance(CONNECTOR_PROPERTIES_INSTANCE)
            val updates = mutableListOf<Identity>()
            val listener: (Identity) -> Unit = { updates += it }
            connector.identityStore.addIdentityListener(listener)

            // Queued before the replacement, so it drains through the retired instance's plugins.
            first.track(
                BaseEvent().apply {
                    eventType = "queued"
                    userProperties = mutableMapOf("\$set" to mapOf("plan" to "stale"))
                },
            )
            createAmplitude(CONNECTOR_PROPERTIES_INSTANCE)
            advanceUntilIdle()
            connector.identityStore.removeIdentityListener(listener)

            assertTrue(updates.none { it.userProperties.containsKey("plan") })
        }

    @Test
    fun `a retired instance cannot rotate the identity it no longer owns`() =
        runTest {
            val first = createAmplitude("retired-identity")
            advanceUntilIdle()
            val deviceIdBeforeRetirement = first.getDeviceId()

            createAmplitude("retired-identity")
            advanceUntilIdle()

            first.reset()
            advanceUntilIdle()

            assertEquals(deviceIdBeforeRetirement, first.getDeviceId())
        }

    @Test
    fun `replacing an instance that is still building leaves it inert`() =
        runTest {
            // Never advanced before the replacement is created, so the build is still pending.
            val first = createAmplitude("still-building")

            val second = createAmplitude("still-building")
            val recorder = FakeEventPlugin()
            second.add(recorder)
            advanceUntilIdle()

            // The abandoned build installed nothing, so it owns no plugins, storage, or connector.
            assertTrue(first.plugins(UniversalPlugin::class.java).isEmpty())
            assertTrue(second.isActive)

            second.track(BaseEvent().apply { eventType = "after-replacement" })
            advanceUntilIdle()
            assertEquals(listOf("after-replacement"), recorder.trackedEvents.map { it.eventType })
        }

    @Test
    fun `does not wait for the previous instance's cleanup`() =
        runTest {
            val first = createAmplitude("blocked-cleanup")
            advanceUntilIdle()
            val teardowns = AtomicInteger()
            first.add(CountingTeardownPlugin(teardowns))

            val second = createAmplitude("blocked-cleanup")

            // Nothing has run on the previous instance yet — its plugin teardown is still queued —
            // and the replacement is already the active owner.
            assertEquals(0, teardowns.get())
            assertFalse(first.isActive)
            assertTrue(second.isActive)
            assertEquals(1, unregistered.size)

            val recorder = FakeEventPlugin()
            second.add(recorder)
            second.track(BaseEvent().apply { eventType = "after-replacement" })
            advanceUntilIdle()

            assertEquals(listOf("after-replacement"), recorder.trackedEvents.map { it.eventType })
            assertEquals(1, teardowns.get())
        }

    @Test
    fun `a failed activation keeps the existing instance active`() =
        runTest {
            val first = createAmplitude("failed-activation")
            val recorder = FakeEventPlugin()
            first.add(recorder)
            advanceUntilIdle()

            every {
                application.registerActivityLifecycleCallbacks(any())
            } throws IllegalStateException("cannot register")

            try {
                createAmplitude("failed-activation")
                fail("Expected the failed activation to propagate")
            } catch (_: IllegalStateException) {
                // expected
            }
            advanceUntilIdle()

            assertTrue(first.isActive)
            assertFalse(unregistered.contains(registered.single()))

            first.track(BaseEvent().apply { eventType = "still-working" })
            advanceUntilIdle()
            assertEquals(listOf("still-working"), recorder.trackedEvents.map { it.eventType })

            // The half-built instance is retired, so its build cannot take the connector receiver.
            AnalyticsConnector.getInstance("failed-activation").eventBridge
                .logEvent(AnalyticsEvent("\$exposure"))
            advanceUntilIdle()
            assertEquals(
                listOf("still-working", "\$exposure"),
                recorder.trackedEvents.map { it.eventType },
            )
        }

    @Test
    fun `connector events route to the newest instance after replacement`() =
        runTest {
            val first = createAmplitude(CONNECTOR_INSTANCE)
            val firstRecorder = FakeEventPlugin()
            first.add(firstRecorder)
            advanceUntilIdle()

            val second = createAmplitude(CONNECTOR_INSTANCE)
            val secondRecorder = FakeEventPlugin()
            second.add(secondRecorder)
            advanceUntilIdle()

            // Retiring the first instance must not clear the receiver the second one installed.
            AnalyticsConnector.getInstance(CONNECTOR_INSTANCE).eventBridge
                .logEvent(AnalyticsEvent("\$exposure"))
            advanceUntilIdle()

            assertEquals(listOf("\$exposure"), secondRecorder.trackedEvents.map { it.eventType })
            assertTrue(firstRecorder.trackedEvents.isEmpty())
        }

    @Test
    fun `events queued before the replacement still get processed`() =
        runTest {
            val first = createAmplitude("queued-events")
            val recorder = FakeEventPlugin()
            first.add(recorder)
            advanceUntilIdle()

            // Queued but not yet drained by the timeline when the replacement arrives.
            first.track(BaseEvent().apply { eventType = "queued" })
            createAmplitude("queued-events")
            advanceUntilIdle()

            // Drained through the plugins first, and only then are the plugins removed.
            assertEquals(listOf("queued"), recorder.trackedEvents.map { it.eventType })
            assertTrue(first.plugins(UniversalPlugin::class.java).isEmpty())
        }

    @Test
    fun `concurrent construction leaves exactly one active instance`() =
        runTest {
            val configurations = List(4) { configuration("concurrent") }
            val instances = mutableListOf<Amplitude>()
            configurations
                .map { config ->
                    thread {
                        val instance =
                            FakeAndroidAmplitude(
                                configuration = config,
                                androidTestDispatcher = StandardTestDispatcher(testScheduler),
                            )
                        synchronized(instances) { instances += instance }
                    }
                }.forEach { it.join() }
            advanceUntilIdle()

            assertEquals(1, instances.count { it.isActive })
            assertEquals(3, unregistered.size)
        }

    @Test
    fun `retirement unregisters the instance's own callbacks and stops autocapture`() =
        runTest {
            val first =
                createAmplitude(
                    "autocapture-stop",
                    autocapture = setOf(AutocaptureOption.ELEMENT_INTERACTIONS),
                )
            advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
            val lifecyclePlugin = first.findPlugin<AndroidLifecyclePlugin>()
            assertTrue(lifecyclePlugin != null)

            createAmplitude("autocapture-stop")

            // Both happen synchronously: autocapture must not wait on the event queue draining.
            assertSame(registered.first(), unregistered.single())
            assertTrue(lifecyclePlugin!!.eventJob!!.isCancelled)

            advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()
        }

    @Test
    fun `only the active instance may claim shared state`() =
        runTest {
            val first = createAmplitude("ownership")
            advanceUntilIdle()

            var claimedByFirst = false
            AmplitudeRegistry.runIfActive(first) { claimedByFirst = true }
            assertTrue(claimedByFirst)

            val second = createAmplitude("ownership")
            advanceUntilIdle()

            // A build that finishes after its instance was replaced must not claim anything.
            claimedByFirst = false
            AmplitudeRegistry.runIfActive(first) { claimedByFirst = true }
            assertFalse(claimedByFirst)

            var claimedBySecond = false
            AmplitudeRegistry.runIfActive(second) { claimedBySecond = true }
            assertTrue(claimedBySecond)
        }

    private fun TestScope.createAmplitude(
        instanceName: String,
        autocapture: Set<AutocaptureOption> = setOf(),
    ) = createFakeAmplitude(
        scheduler = testScheduler,
        configuration = configuration(instanceName, autocapture),
    )

    private fun configuration(
        instanceName: String,
        autocapture: Set<AutocaptureOption> = setOf(),
    ) = Configuration(
        apiKey = "api-key",
        context = application,
        instanceName = instanceName,
        autocapture = autocapture,
        loggerProvider = ConsoleLoggerProvider(),
        storageProvider = InMemoryStorageProvider(),
        identifyInterceptStorageProvider = InMemoryStorageProvider(),
        identityStorageProvider = IMIdentityStorageProvider(),
    )

    private fun mockApplication(): Application {
        setupMockAndroidContext()
        val context = mockk<Application>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        val dirNameSlot = slot<String>()
        every { context.getDir(capture(dirNameSlot), any()) } answers {
            File("/tmp/amplitude-kotlin/${dirNameSlot.captured}")
        }
        return context
    }

    private companion object {
        const val CONNECTOR_INSTANCE = "connector-handover"
        const val CONNECTOR_PROPERTIES_INSTANCE = "connector-user-properties"
    }
}

private class CountingTeardownPlugin(private val teardowns: AtomicInteger) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var amplitude: com.amplitude.core.Amplitude

    override fun teardown() {
        teardowns.incrementAndGet()
    }
}
