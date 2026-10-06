@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.amplitude.android

import android.app.Activity
import android.app.Application
import android.content.pm.ProviderInfo
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleInitializer
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.startup.InitializationProvider
import androidx.test.core.app.ApplicationProvider
import com.amplitude.MainDispatcherRule
import com.amplitude.android.Constants.EventProperties
import com.amplitude.android.Constants.EventTypes
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.android.utilities.ActivityLifecycleObserver
import com.amplitude.android.utilities.ProcessLifecycleObserver
import com.amplitude.android.utilities.Time
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.android.utilities.getVersionCode
import com.amplitude.core.State
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.utilities.InMemoryStorage
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityConfiguration
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ProcessLifecycleTest {
    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var previousProcessLifecycle: LifecycleRegistry

    @Before
    fun isolateProcessLifecycle() {
        // Other SDK fixtures share AndroidX's singleton and can outlive their test scheduler.
        // Give this integration fixture its own real registry, then restore the original below.
        val processOwner = ProcessLifecycleOwner.get()
        val registry = ProcessLifecycleOwner::class.java.getDeclaredField("registry").apply { isAccessible = true }
        previousProcessLifecycle = registry.get(processOwner) as LifecycleRegistry
        registry.set(
            processOwner,
            LifecycleRegistry(processOwner).apply { handleLifecycleEvent(Lifecycle.Event.ON_CREATE) },
        )
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(mainDispatcher)

    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)

        init {
            lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
    }

    private class Events : com.amplitude.core.platform.EventPlugin {
        override val type = Plugin.Type.Before
        override lateinit var amplitude: com.amplitude.core.Amplitude
        val events = mutableListOf<BaseEvent>()
        val flushes = mutableListOf<List<String>>()

        override fun track(payload: BaseEvent): BaseEvent {
            events.add(payload)
            return payload
        }

        override fun flush() {
            flushes.add(events.map { it.eventType })
        }
    }

    private val instances = mutableListOf<Amplitude>()
    private val observers = mutableListOf<ProcessLifecycleObserver>()
    private val plugins = mutableListOf<AndroidLifecyclePlugin>()
    private val owner = Owner()
    private var now = 1_000L
    private val time =
        mockk<Time>().also { time ->
            every { time.nowMillis() } answers { now }
        }

    private suspend fun TestScope.create(
        name: String = "process-test",
        autocapture: Set<AutocaptureOption> = setOf(AutocaptureOption.APP_LIFECYCLES, AutocaptureOption.SESSIONS),
        flush: Boolean = true,
        buildGate: CompletableDeferred<Unit>? = null,
    ): Amplitude {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
        val storage = InMemoryStorage()
        storage.write(Storage.Constants.APP_BUILD, packageInfo.getVersionCode().toString())
        storage.write(Storage.Constants.APP_VERSION, packageInfo.versionName ?: "Unknown")
        val configuration =
            Configuration(
                apiKey = "api-key",
                context = ApplicationProvider.getApplicationContext<Application>(),
                instanceName = name,
                autocapture = autocapture,
                enableAutocaptureRemoteConfig = false,
                offline = true,
                minTimeBetweenSessionsMillis = 100,
                flushEventsOnClose = flush,
                storageProvider = InstanceStorageProvider(storage),
                identifyInterceptStorageProvider = InMemoryStorageProvider(),
                identityStorageProvider = IMIdentityStorageProvider(),
            )
        return if (buildGate == null) {
            createFakeAmplitude(scheduler = testScheduler, configuration = configuration)
        } else {
            DelayedAmplitude(configuration, StandardTestDispatcher(testScheduler), buildGate)
        }.also { instances.add(it) }
    }

    private class DelayedAmplitude(
        configuration: Configuration,
        dispatcher: TestDispatcher,
        private val buildGate: CompletableDeferred<Unit>,
    ) : Amplitude(
            configuration,
            State(),
            amplitudeScope = TestScope(dispatcher),
            amplitudeDispatcher = dispatcher,
            networkIODispatcher = dispatcher,
            storageIODispatcher = dispatcher,
        ) {
        var lifecyclePlugin: AndroidLifecyclePlugin? = null

        override suspend fun buildInternal(identityConfiguration: IdentityConfiguration) {
            buildGate.await()
            super.buildInternal(identityConfiguration)
            if (isActive) {
                lifecyclePlugin?.let { replacement ->
                    findPlugin<AndroidLifecyclePlugin>()?.let { remove(it) }
                    add(replacement)
                }
            }
        }
    }

    private fun TestScope.observe(amplitude: Amplitude): ProcessLifecycleObserver {
        amplitude.findPlugin<AndroidLifecyclePlugin>()?.let { amplitude.remove(it) }
        return ProcessLifecycleObserver(owner.lifecycle, time).also {
            observers.add(it)
            val plugin = AndroidLifecyclePlugin(ActivityLifecycleObserver(), it)
            plugins.add(plugin)
            if (amplitude is DelayedAmplitude && !amplitude.isBuilt.isCompleted) {
                amplitude.lifecyclePlugin = plugin
            } else {
                amplitude.add(plugin)
            }
            runCurrent()
        }
    }

    private suspend fun TestScope.record(amplitude: Amplitude): Events {
        amplitude.isBuilt.await()
        amplitude.findPlugin<AmplitudeDestination>()?.let { amplitude.remove(it) }
        advanceUntilIdle()
        return Events().also { amplitude.add(it) }
    }

    private fun start(timestamp: Long) {
        now = timestamp
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    private fun stop(timestamp: Long) {
        now = timestamp
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    @After
    fun cleanup() =
        runTest(mainDispatcher) {
            plugins.forEach { it.teardown() }
            instances.forEach { it.findPlugin<AndroidLifecyclePlugin>()?.teardown() }
            observers.forEach { it.stop() }
            runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            ProcessLifecycleOwner::class.java.getDeclaredField("registry").apply { isAccessible = true }
                .set(ProcessLifecycleOwner.get(), previousProcessLifecycle)
        }

    @Test
    fun `subscription receives initial foreground and subsequent transitions without replay`() =
        runTest {
            val observer = ProcessLifecycleObserver(owner.lifecycle, time)
            observers.add(observer)
            start(1_000)
            val received = mutableListOf<ProcessLifecycleObserver.Transition>()
            val collector =
                backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    observer.events.onSubscription { observer.start() }.collect {
                        received.add(it)
                    }
                }
            runCurrent()
            stop(1_050)
            start(2_000)
            runCurrent()
            assertEquals(
                listOf(
                    ProcessLifecycleObserver.Transition(1_000, true),
                    ProcessLifecycleObserver.Transition(1_050, false),
                    ProcessLifecycleObserver.Transition(2_000, true),
                ),
                received,
            )
            assertTrue(observer.events.replayCache.isEmpty())
            stop(2_050)
            runCurrent()
            assertEquals(ProcessLifecycleObserver.Transition(2_050, false), received.last())
            assertTrue(observer.events.replayCache.isEmpty())
            collector.cancel()
            observer.stop()
            runCurrent()
            assertEquals(0, owner.lifecycle.observerCount)
        }

    @Test
    fun `slow subscriber receives a burst of process transitions in order`() =
        runTest {
            val observer = ProcessLifecycleObserver(owner.lifecycle, time)
            observers.add(observer)
            val received = mutableListOf<ProcessLifecycleObserver.Transition>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                observer.events.onSubscription { observer.start() }.collect {
                    delay(10)
                    received.add(it)
                }
            }
            runCurrent()

            val expected =
                (0 until 1_000).map { index ->
                    val timestamp = 1_000L + index
                    val foreground = index % 2 == 0
                    if (foreground) start(timestamp) else stop(timestamp)
                    ProcessLifecycleObserver.Transition(timestamp, foreground)
                }
            advanceTimeBy(10_010)
            runCurrent()
            assertEquals(expected, received)
        }

    @Test
    fun `restarting observation in foreground synchronizes the new subscription once`() =
        runTest {
            val observer = ProcessLifecycleObserver(owner.lifecycle, time)
            observers.add(observer)
            start(1_000)
            val received = mutableListOf<ProcessLifecycleObserver.Transition>()
            val first =
                backgroundScope.launch {
                    observer.events.onSubscription { observer.start() }.collect { received.add(it) }
                }
            runCurrent()
            observer.start()
            runCurrent()
            assertEquals(1, owner.lifecycle.observerCount)
            assertEquals(listOf(ProcessLifecycleObserver.Transition(1_000, true)), received)
            first.cancel()
            observer.stop()
            runCurrent()
            assertEquals(0, owner.lifecycle.observerCount)

            now = 2_000
            backgroundScope.launch {
                observer.events.onSubscription { observer.start() }.collect { received.add(it) }
            }
            runCurrent()
            assertEquals(1, owner.lifecycle.observerCount)
            assertEquals(
                listOf(
                    ProcessLifecycleObserver.Transition(1_000, true),
                    ProcessLifecycleObserver.Transition(2_000, true),
                ),
                received,
            )
        }

    @Test
    fun `short background interval preserves session and lifecycle ordering`() =
        runTest {
            val amplitude = create()
            val events = record(amplitude)
            observe(amplitude)
            start(1_000)
            stop(1_050)
            start(1_100)
            val trackingStartedAt = System.currentTimeMillis()
            advanceUntilIdle()
            val trackingFinishedAt = System.currentTimeMillis()

            assertEquals(1_000L, amplitude.sessionId)
            assertEquals(
                listOf(
                    Amplitude.START_SESSION_EVENT,
                    EventTypes.APPLICATION_OPENED,
                    EventTypes.APPLICATION_BACKGROUNDED,
                    EventTypes.APPLICATION_OPENED,
                ),
                events.events.map { it.eventType },
            )
            assertEquals(1_000L, events.events.first().timestamp)
            // Existing lifecycle helpers timestamp events when tracked; session transitions
            // still use the clock captured in the process callback.
            assertTrue(events.events.drop(1).all { it.timestamp!! in trackingStartedAt..trackingFinishedAt })
            assertTrue(events.events.all { it.sessionId == 1_000L })
            val opened = events.events.filter { it.eventType == EventTypes.APPLICATION_OPENED }
            assertEquals(listOf(false, true), opened.map { it.eventProperties!![EventProperties.FROM_BACKGROUND] })
            assertTrue(
                opened.all {
                    it.eventProperties!!.containsKey(EventProperties.VERSION) && it.eventProperties!!.containsKey(EventProperties.BUILD)
                },
            )
            assertEquals(1, events.flushes.size)
            assertTrue(events.flushes.single().contains(EventTypes.APPLICATION_BACKGROUNDED))
        }

    @Test
    fun `timeout and 22 hour background gaps start a new session only on return`() =
        runTest {
            for (gap in listOf(100L, 22 * 60 * 60 * 1_000L)) {
                val amplitude = create(name = "gap-$gap")
                val events = record(amplitude)
                val observer = observe(amplitude)
                start(1_000)
                stop(1_050)
                advanceUntilIdle()
                assertEquals(1_000L, amplitude.sessionId)
                assertFalse(events.events.any { it.eventType == Amplitude.END_SESSION_EVENT })
                start(1_050 + gap)
                advanceUntilIdle()
                assertEquals(1_050 + gap, amplitude.sessionId)
                assertEquals(
                    listOf(
                        Amplitude.START_SESSION_EVENT,
                        EventTypes.APPLICATION_OPENED,
                        EventTypes.APPLICATION_BACKGROUNDED,
                        Amplitude.END_SESSION_EVENT,
                        Amplitude.START_SESSION_EVENT,
                        EventTypes.APPLICATION_OPENED,
                    ),
                    events.events.map { it.eventType },
                )
                assertEquals(1_000L, events.events[2].sessionId)
                assertEquals(1_000L, events.events[3].sessionId)
                assertEquals(1_050L, events.events[3].timestamp)
                assertEquals(1_050 + gap, events.events.last().sessionId)
                observer.stop()
                stop(1_051 + gap)
            }
        }

    @Test
    fun `long foreground inactivity and activity callbacks do not expire the session`() =
        runTest {
            val amplitude = create()
            val events = record(amplitude)
            observe(amplitude)
            start(1_000)
            advanceUntilIdle()
            val activity = Activity()
            val plugin = amplitude.findPlugin<AndroidLifecyclePlugin>()!!
            plugin.onActivityStarted(activity)
            plugin.onActivityStopped(activity)
            plugin.onActivityDestroyed(activity)
            plugin.onActivityStarted(Activity())
            amplitude.track(
                BaseEvent().apply {
                    eventType = "after 22 hours"
                    timestamp = 1_000 + 22 * 60 * 60 * 1_000L
                },
            )
            advanceUntilIdle()
            assertEquals(1_000L, amplitude.sessionId)
            assertEquals(1, events.events.count { it.eventType == EventTypes.APPLICATION_OPENED })
            assertFalse(events.events.any { it.eventType == EventTypes.APPLICATION_BACKGROUNDED })
        }

    @Test
    fun `background without an Activity updates sessions and flushes with all autocapture disabled`() =
        runTest {
            for (flush in listOf(false, true)) {
                val amplitude = create(name = "disabled-$flush", autocapture = emptySet(), flush = flush)
                val events = record(amplitude)
                val observer = observe(amplitude)
                start(1_000)
                stop(1_050)
                advanceUntilIdle()
                assertEquals(1_050L, (amplitude.timeline as Timeline).lastEventTime)
                assertEquals(if (flush) 1 else 0, events.flushes.size)
                start(2_000)
                advanceUntilIdle()
                assertEquals(2_000L, amplitude.sessionId)
                assertTrue(events.events.isEmpty())
                observer.stop()
                stop(2_050)
            }
        }

    @Test
    fun `setup synchronizes once to the current foreground state after delayed initialization`() =
        runTest {
            start(1_000)
            val buildGate = CompletableDeferred<Unit>()
            val amplitude = create(buildGate = buildGate)
            val events = Events()
            amplitude.add(events)
            observe(amplitude)
            assertEquals(0, owner.lifecycle.observerCount)
            assertFalse(amplitude.isBuilt.isCompleted)
            stop(1_050)
            start(2_000)
            assertEquals(-1L, amplitude.sessionId)
            val trackingStartedAt = System.currentTimeMillis()
            buildGate.complete(Unit)
            amplitude.isBuilt.await()
            advanceUntilIdle()
            val trackingFinishedAt = System.currentTimeMillis()
            assertEquals(2_000L, amplitude.sessionId)
            val opened = events.events.filter { it.eventType == EventTypes.APPLICATION_OPENED }
            assertEquals(1, owner.lifecycle.observerCount)
            assertEquals(1, opened.size)
            assertTrue(opened.all { it.timestamp!! in trackingStartedAt..trackingFinishedAt })
            assertEquals(listOf(false), opened.map { it.eventProperties!![EventProperties.FROM_BACKGROUND] })
            assertFalse(events.events.any { it.eventType == EventTypes.APPLICATION_BACKGROUNDED })
        }

    @Test
    fun `setup in the background waits for the next foreground transition`() =
        runTest {
            start(1_000)
            val buildGate = CompletableDeferred<Unit>()
            val amplitude = create(buildGate = buildGate)
            val events = Events()
            amplitude.add(events)
            observe(amplitude)
            stop(1_050)

            buildGate.complete(Unit)
            amplitude.isBuilt.await()
            advanceUntilIdle()
            assertEquals(1, owner.lifecycle.observerCount)
            assertEquals(-1L, amplitude.sessionId)
            assertFalse(events.events.any { it.eventType == EventTypes.APPLICATION_OPENED })
            assertFalse(events.events.any { it.eventType == EventTypes.APPLICATION_BACKGROUNDED })

            start(2_000)
            advanceUntilIdle()
            assertEquals(2_000L, amplitude.sessionId)
            val opened = events.events.single { it.eventType == EventTypes.APPLICATION_OPENED }
            assertEquals(false, opened.eventProperties!![EventProperties.FROM_BACKGROUND])
        }

    @Test
    fun `stopped observer removes itself and ignores transitions until restarted`() =
        runTest {
            val amplitude = create(autocapture = emptySet())
            record(amplitude)
            val observer = observe(amplitude)
            start(1_000)
            advanceUntilIdle()
            observer.stop()
            stop(1_050)
            advanceUntilIdle()
            assertEquals(0, owner.lifecycle.observerCount)
            assertEquals(1_000L, (amplitude.timeline as Timeline).lastEventTime)
            observer.stop()
            assertEquals(0, owner.lifecycle.observerCount)
        }

    @Test
    fun `registration and removal dispatch to main and complete before returning`() =
        runTest {
            val observer = ProcessLifecycleObserver(owner.lifecycle, time)
            observers.add(observer)
            val workerDispatcher = StandardTestDispatcher(testScheduler, name = "worker")
            val registration =
                backgroundScope.launch(workerDispatcher, start = CoroutineStart.UNDISPATCHED) {
                    observer.start()
                }
            assertEquals(0, owner.lifecycle.observerCount)
            assertFalse(registration.isCompleted)
            runCurrent()
            assertEquals(1, owner.lifecycle.observerCount)
            assertTrue(registration.isCompleted)

            val removal =
                backgroundScope.launch(workerDispatcher, start = CoroutineStart.UNDISPATCHED) {
                    observer.stop()
                }
            assertEquals(1, owner.lifecycle.observerCount)
            assertFalse(removal.isCompleted)
            runCurrent()
            assertEquals(0, owner.lifecycle.observerCount)
            assertTrue(removal.isCompleted)

            val pending =
                backgroundScope.launch(workerDispatcher, start = CoroutineStart.UNDISPATCHED) {
                    observer.start()
                }
            pending.cancel()
            runCurrent()
            assertEquals(0, owner.lifecycle.observerCount)
        }

    @Test
    fun `observer cleanup completes after the SDK coroutine scope is cancelled`() =
        runTest {
            val amplitude = create(autocapture = emptySet())
            record(amplitude)
            val observer = observe(amplitude)
            assertEquals(1, owner.lifecycle.observerCount)

            amplitude.amplitudeScope.cancel()
            runCurrent()

            assertEquals(0, owner.lifecycle.observerCount)
            observer.stop()
            runCurrent()
            assertEquals(0, owner.lifecycle.observerCount)
        }

    @Test
    fun `multiple instances receive independent first open and background session transitions`() =
        runTest {
            val first = create(name = "first")
            val firstEvents = record(first)
            val second = create(name = "second", autocapture = setOf(AutocaptureOption.APP_LIFECYCLES))
            val secondEvents = record(second)
            val firstObserver = observe(first)
            observe(second)
            start(1_000)
            stop(1_050)
            start(2_000)
            advanceUntilIdle()
            assertEquals(2_000L, first.sessionId)
            assertEquals(2_000L, second.sessionId)
            for (events in listOf(firstEvents, secondEvents)) {
                assertEquals(
                    listOf(false, true),
                    events.events.filter { it.eventType == EventTypes.APPLICATION_OPENED }
                        .map { it.eventProperties!![EventProperties.FROM_BACKGROUND] },
                )
                assertEquals(1, events.events.count { it.eventType == EventTypes.APPLICATION_BACKGROUNDED })
            }
            assertFalse(
                secondEvents.events.any { it.eventType == Amplitude.START_SESSION_EVENT || it.eventType == Amplitude.END_SESSION_EVENT },
            )
            firstObserver.stop()
            stop(2_050)
            start(3_000)
            advanceUntilIdle()
            assertEquals(2_000L, first.sessionId)
            assertEquals(3_000L, second.sessionId)
        }

    @Test
    fun `replacement before setup prevents observation when the pending build completes`() =
        runTest {
            val lifecycle = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry
            val baseline = lifecycle.observerCount
            val buildGate = CompletableDeferred<Unit>()
            val first = create(name = "pending-replacement", buildGate = buildGate)
            runCurrent()
            assertFalse(first.isBuilt.isCompleted)
            assertEquals(baseline, lifecycle.observerCount)

            val replacement = create(name = "pending-replacement")
            record(replacement)
            runCurrent()
            assertFalse(first.isActive)
            assertTrue(replacement.isActive)
            assertEquals(baseline + 1, lifecycle.observerCount)

            buildGate.complete(Unit)
            first.isBuilt.await()
            advanceUntilIdle()
            assertEquals(baseline + 1, lifecycle.observerCount)
            assertEquals(null, first.findPlugin<AndroidLifecyclePlugin>())
        }

    @Test
    fun `replacement and plugin teardown remove real process observers while distinct instances coexist`() =
        runTest {
            val lifecycle = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry
            val baseline = lifecycle.observerCount
            val first = create(name = "shared")
            record(first)
            val other = create(name = "other")
            record(other)
            assertEquals(baseline + 2, lifecycle.observerCount)
            val replacement = create(name = "shared")
            record(replacement)
            advanceUntilIdle()
            assertFalse(first.isActive)
            assertTrue(other.isActive)
            assertTrue(replacement.isActive)
            assertEquals(baseline + 2, lifecycle.observerCount)
            replacement.remove(replacement.findPlugin<AndroidLifecyclePlugin>()!!)
            runCurrent()
            assertEquals(baseline + 1, lifecycle.observerCount)
            other.remove(other.findPlugin<AndroidLifecyclePlugin>()!!)
            runCurrent()
            assertEquals(baseline, lifecycle.observerCount)
        }

    @Test
    fun `AndroidX startup and activity recreation produce one process open and delayed background`() =
        runTest {
            val application = ApplicationProvider.getApplicationContext<Application>()
            // Library tests do not merge dependency manifests. Supply the dependency's provider
            // metadata, then exercise the real eager AndroidX Startup initialization path.
            val providerInfo =
                ProviderInfo().apply {
                    name = InitializationProvider::class.java.name
                    packageName = application.packageName
                    authority = "${application.packageName}.androidx-startup"
                    metaData =
                        Bundle().apply {
                            putString(ProcessLifecycleInitializer::class.java.name, "androidx.startup")
                        }
                }
            shadowOf(application.packageManager).addOrUpdateProvider(providerInfo)
            InitializationProvider().attachInfo(application, providerInfo)
            val amplitude = create()
            val events = record(amplitude)
            // The dependency's manifest must eagerly initialize the real AndroidX owner.
            assertEquals(Lifecycle.State.CREATED, ProcessLifecycleOwner.get().lifecycle.currentState)
            val first = Robolectric.buildActivity(Activity::class.java).setup()
            advanceUntilIdle()
            assertEquals(1, events.events.count { it.eventType == EventTypes.APPLICATION_OPENED })
            first.pause().stop().destroy()
            val next = Robolectric.buildActivity(Activity::class.java).setup()
            advanceUntilIdle()
            assertFalse(events.events.any { it.eventType == EventTypes.APPLICATION_BACKGROUNDED })
            assertEquals(1, events.events.count { it.eventType == EventTypes.APPLICATION_OPENED })
            next.pause().stop().destroy()
            advanceUntilIdle()
            assertFalse(events.events.any { it.eventType == EventTypes.APPLICATION_BACKGROUNDED })
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            advanceUntilIdle()
            assertEquals(1, events.events.count { it.eventType == EventTypes.APPLICATION_BACKGROUNDED })
        }
}
