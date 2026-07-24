package com.amplitude.android

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.MainDispatcherRule
import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.analytics.connector.AnalyticsEvent
import com.amplitude.android.plugins.AndroidNetworkConnectivityCheckerPlugin
import com.amplitude.android.utilities.FakeAndroidAmplitude
import com.amplitude.core.State
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.core.utilities.http.AnalyticsResponse
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityConfiguration
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AmplitudeReplacementTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val instancesToClean = ConcurrentLinkedQueue<Amplitude>()

    @After
    fun tearDown() {
        instancesToClean.forEach { amplitude ->
            amplitude.deactivateForReplacement()
            amplitude.finishReplacementCleanup()
        }
    }

    @Test
    fun `same-name construction automatically retires the previous instance`() =
        runTest {
            val application = TestApplication()
            val instanceName = "replacement-customer-flow"
            val first =
                track(
                    FakeAndroidAmplitude(
                        configuration =
                            configuration(
                                application,
                                instanceName,
                                autocapture = setOf(AutocaptureOption.SCREEN_VIEWS),
                            ),
                        androidTestDispatcher = StandardTestDispatcher(testScheduler),
                    ),
                )
            val firstEvents = FakeEventPlugin()
            first.add(firstEvents)
            first.isBuilt.await()

            val activity = mockk<Activity>(relaxed = true)
            val firstCallback = application.registeredCallbacks.single()
            firstCallback.onActivityStarted(activity)
            advanceUntilIdle()
            assertEquals(
                listOf(Constants.EventTypes.SCREEN_VIEWED),
                firstEvents.trackedEvents.map { it.eventType },
            )

            first.flush()
            first.reset()

            val second =
                track(
                    FakeAndroidAmplitude(
                        configuration = configuration(application, instanceName),
                        androidTestDispatcher = StandardTestDispatcher(testScheduler),
                    ),
                )
            val secondEvents = FakeEventPlugin()
            second.add(secondEvents)
            second.isBuilt.await()

            firstCallback.onActivityStarted(activity)
            application.registeredCallbacks.single().onActivityStarted(activity)
            first.track("after replacement")
            second.track("new instance")
            advanceUntilIdle()

            assertFalse(first.isActiveForReplacement())
            assertTrue(second.isActiveForReplacement())
            assertEquals(
                listOf(Constants.EventTypes.SCREEN_VIEWED),
                firstEvents.trackedEvents.map { it.eventType },
            )
            assertEquals(listOf("new instance"), secondEvents.trackedEvents.map { it.eventType })
            assertEquals(1, application.registeredCallbacks.size)
            assertSame(firstCallback, application.unregisteredCallbacks.single())
        }

    @Test
    fun `different instance names remain active`() =
        runTest {
            val application = TestApplication()
            val first =
                track(
                    FakeAndroidAmplitude(
                        configuration = configuration(application, "replacement-first"),
                        androidTestDispatcher = StandardTestDispatcher(testScheduler),
                    ),
                )
            val second =
                track(
                    FakeAndroidAmplitude(
                        configuration = configuration(application, "replacement-second"),
                        androidTestDispatcher = StandardTestDispatcher(testScheduler),
                    ),
                )
            val firstEvents = FakeEventPlugin()
            val secondEvents = FakeEventPlugin()
            first.add(firstEvents)
            second.add(secondEvents)
            first.isBuilt.await()
            second.isBuilt.await()

            first.track("first")
            second.track("second")
            advanceUntilIdle()

            assertTrue(first.isActiveForReplacement())
            assertTrue(second.isActiveForReplacement())
            assertEquals(listOf("first"), firstEvents.trackedEvents.map { it.eventType })
            assertEquals(listOf("second"), secondEvents.trackedEvents.map { it.eventType })
            assertEquals(2, application.registeredCallbacks.size)
            assertTrue(application.unregisteredCallbacks.isEmpty())
        }

    @Test
    fun `Analytics Connector events are routed only to the replacement`() =
        runTest {
            val application = TestApplication()
            val instanceName = "replacement-connector"
            val first =
                track(
                    FakeAndroidAmplitude(
                        configuration = configuration(application, instanceName),
                        androidTestDispatcher = StandardTestDispatcher(testScheduler),
                    ),
                )
            val firstEvents = FakeEventPlugin()
            first.add(firstEvents)
            first.isBuilt.await()

            val second =
                track(
                    FakeAndroidAmplitude(
                        configuration = configuration(application, instanceName),
                        androidTestDispatcher = StandardTestDispatcher(testScheduler),
                    ),
                )
            val secondEvents = FakeEventPlugin()
            second.add(secondEvents)
            second.isBuilt.await()

            AnalyticsConnector.getInstance(instanceName).eventBridge.logEvent(
                AnalyticsEvent("connector event"),
            )
            advanceUntilIdle()

            assertTrue(firstEvents.trackedEvents.isEmpty())
            assertEquals(listOf("connector event"), secondEvents.trackedEvents.map { it.eventType })
        }

    @Test
    fun `plugin teardown failure does not block replacement`() =
        runTest {
            val application = TestApplication()
            val instanceName = "replacement-teardown-failure"
            val recordingPlugin = RecordingTeardownPlugin()
            val first =
                track(
                    FakeAndroidAmplitude(
                        configuration = configuration(application, instanceName),
                        androidTestDispatcher = StandardTestDispatcher(testScheduler),
                    ),
                )
            first.add(ThrowingTeardownPlugin())
            first.add(recordingPlugin)
            first.isBuilt.await()

            val second =
                track(
                    FakeAndroidAmplitude(
                        configuration = configuration(application, instanceName),
                        androidTestDispatcher = StandardTestDispatcher(testScheduler),
                    ),
                )
            second.isBuilt.await()

            assertFalse(first.isActiveForReplacement())
            assertTrue(second.isActiveForReplacement())
            assertTrue(recordingPlugin.tornDown)
            assertEquals(1, application.registeredCallbacks.size)
        }

    @Test
    fun `an in-flight build cannot install after replacement`() {
        val application = TestApplication()
        val instanceName = "replacement-in-flight"
        val buildEntered = CountDownLatch(1)
        val releaseBuild = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()

        try {
            val first =
                track(
                    PausedBuildAmplitude(
                        configuration = configuration(application, instanceName),
                        dispatcher = dispatcher,
                        buildEntered = buildEntered,
                        releaseBuild = releaseBuild,
                    ),
                )
            assertTrue(buildEntered.await(5, TimeUnit.SECONDS))

            val second = track(Amplitude(configuration(application, instanceName)))
            releaseBuild.countDown()

            assertFalse(first.isActiveForReplacement())
            assertTrue(second.isActiveForReplacement())
            assertEquals(1, application.registeredCallbacks.size)
        } finally {
            releaseBuild.countDown()
            instancesToClean.forEach { amplitude ->
                amplitude.deactivateForReplacement()
                amplitude.finishReplacementCleanup()
            }
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `concurrent same-name construction leaves one active instance`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val instanceName = "replacement-concurrent"
        val start = CountDownLatch(1)
        val instances = ConcurrentLinkedQueue<Amplitude>()
        val errors = ConcurrentLinkedQueue<Throwable>()
        val configurations =
            List(8) {
                configuration(application, instanceName)
            }
        val threads =
            configurations.map { configuration ->
                Thread {
                    try {
                        start.await()
                        val amplitude = track(Amplitude(configuration))
                        instances += amplitude
                    } catch (error: Throwable) {
                        errors += error
                    }
                }.apply { start() }
            }

        start.countDown()
        threads.forEach { it.join(10_000) }

        assertTrue(threads.none { it.isAlive })
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(1, instances.count { it.isActiveForReplacement() })
        assertSame(
            ActiveAmplitudeInstances.activeInstance(application, instanceName),
            instances.single { it.isActiveForReplacement() },
        )
    }

    private fun <T : Amplitude> track(amplitude: T): T {
        instancesToClean += amplitude
        return amplitude
    }

    private fun configuration(
        application: Application,
        instanceName: String,
        autocapture: Set<AutocaptureOption> = setOf(),
    ): Configuration {
        return Configuration(
            apiKey = "api-key",
            context = application,
            instanceName = instanceName,
            storageProvider = InMemoryStorageProvider(),
            loggerProvider = ConsoleLoggerProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
            autocapture = autocapture,
            migrateLegacyData = false,
            offline = AndroidNetworkConnectivityCheckerPlugin.Disabled,
            enableDiagnostics = false,
            enableAutocaptureRemoteConfig = false,
            httpClient =
                object : HttpClientInterface {
                    override fun upload(
                        events: String,
                        diagnostics: String?,
                    ): AnalyticsResponse = AnalyticsResponse.create(200, null)
                },
        )
    }

    private class TestApplication : Application() {
        val registeredCallbacks = CopyOnWriteArrayList<Application.ActivityLifecycleCallbacks>()
        val unregisteredCallbacks = CopyOnWriteArrayList<Application.ActivityLifecycleCallbacks>()

        init {
            attachBaseContext(ApplicationProvider.getApplicationContext())
        }

        override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
            registeredCallbacks += callback
        }

        override fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
            registeredCallbacks.remove(callback)
            unregisteredCallbacks += callback
        }
    }

    private class PausedBuildAmplitude(
        configuration: Configuration,
        dispatcher: CoroutineDispatcher,
        private val buildEntered: CountDownLatch,
        private val releaseBuild: CountDownLatch,
    ) : Amplitude(
            configuration = configuration,
            state = State(),
            amplitudeScope = CoroutineScope(SupervisorJob()),
            amplitudeDispatcher = dispatcher,
            networkIODispatcher = dispatcher,
            storageIODispatcher = dispatcher,
        ) {
        override suspend fun buildInternal(identityConfiguration: IdentityConfiguration) {
            buildEntered.countDown()
            releaseBuild.await()
            super.buildInternal(identityConfiguration)
        }
    }

    private class ThrowingTeardownPlugin : Plugin {
        override val type = Plugin.Type.Before
        override lateinit var amplitude: com.amplitude.core.Amplitude

        override fun teardown() {
            error("expected teardown failure")
        }
    }

    private class RecordingTeardownPlugin : Plugin {
        override val type = Plugin.Type.Enrichment
        override lateinit var amplitude: com.amplitude.core.Amplitude
        var tornDown = false

        override fun teardown() {
            tornDown = true
        }
    }
}
