@file:OptIn(RestrictedAmplitudeFeature::class)

package com.amplitude.android.plugins

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureManager
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.AutocaptureState
import com.amplitude.android.Configuration
import com.amplitude.android.Constants.EventTypes
import com.amplitude.android.GuardedAmplitudeFeature
import com.amplitude.android.InteractionsOptions
import com.amplitude.android.internal.fragments.FragmentActivityHandler
import com.amplitude.android.internal.fragments.FragmentActivityHandler.registerFragmentLifecycleCallbacks
import com.amplitude.android.internal.fragments.FragmentActivityHandler.unregisterFragmentLifecycleCallbacks
import com.amplitude.android.utilities.ActivityLifecycleObserver
import com.amplitude.android.utilities.ProcessLifecycleObserver
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.Storage
import com.amplitude.core.utilities.InMemoryStorage
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AndroidLifecyclePluginTest {
    private val mockedContext = mockk<Application>(relaxed = true)
    private val mockedAmplitude = mockk<Amplitude>(relaxed = true)
    private val mockedConfig = mockk<Configuration>(relaxed = true)

    private lateinit var spiedStorage: InMemoryStorage

    private val mockedPackageManager = mockk<PackageManager>()
    private val packageInfo =
        PackageInfo().apply {
            versionCode = 666
            versionName = "6.6.6"
        }

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var observer: ActivityLifecycleObserver
    private lateinit var plugin: AndroidLifecyclePlugin

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { mockedAmplitude.configuration } returns mockedConfig
        every { mockedConfig.context } returns mockedContext
        every { mockedContext.packageManager } returns mockedPackageManager
        every { mockedPackageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo

        spiedStorage = spyk(InMemoryStorage())
        every { mockedAmplitude.storage } returns spiedStorage
        every { mockedAmplitude.storageIODispatcher } returns testDispatcher

        // Default autocapture: empty (matching relaxed mock default)
        mockAutocapture(emptySet())

        val processObserver = mockk<ProcessLifecycleObserver>(relaxed = true)
        every { processObserver.events } returns MutableSharedFlow()
        every { mockedAmplitude.isActive } returns true
        observer = ActivityLifecycleObserver()
        plugin = AndroidLifecyclePlugin(observer, processObserver)

        mockkObject(FragmentActivityHandler)
    }

    @OptIn(GuardedAmplitudeFeature::class)
    @Test
    fun `collectors stop on plugin teardown and SDK scope cancellation`() =
        runTest {
            for (cancelSdk in listOf(false, true)) {
                val sdkScope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
                every { mockedAmplitude.amplitudeScope } returns sdkScope
                val transitions = MutableSharedFlow<ProcessLifecycleObserver.Transition>()
                val processObserver = mockk<ProcessLifecycleObserver>(relaxed = true)
                every { processObserver.events } returns transitions
                val states = MutableStateFlow(AutocaptureState.from(emptySet(), InteractionsOptions()))
                val manager = mockk<AutocaptureManager>()
                every { manager.state } returns states
                every { mockedAmplitude.autocaptureManager } returns manager
                observer = ActivityLifecycleObserver()
                plugin = AndroidLifecyclePlugin(observer, processObserver)
                plugin.setup(mockedAmplitude)
                advanceUntilIdle()
                assertEquals(1, transitions.subscriptionCount.value)
                assertEquals(1, states.subscriptionCount.value)

                if (cancelSdk) {
                    sdkScope.cancel()
                } else {
                    plugin.teardown()
                    assertTrue(sdkScope.isActive)
                }
                advanceUntilIdle()
                assertEquals(0, transitions.subscriptionCount.value)
                assertEquals(0, states.subscriptionCount.value)

                // Late events and remote config must not reactivate a removed plugin.
                transitions.tryEmit(ProcessLifecycleObserver.Transition(1_000, true))
                states.value = states.value.copy(appLifecycles = true)
                observer.onActivityStarted(mockk<Activity>(relaxed = true))
                advanceUntilIdle()
                verify(exactly = 0) { mockedAmplitude.onEnterForeground(any()) }
                verify(exactly = 0) { mockedAmplitude.track(EventTypes.APPLICATION_INSTALLED, any(), any()) }
                close()
                sdkScope.cancel()
            }
        }

    @OptIn(GuardedAmplitudeFeature::class)
    @Test
    fun `one process subscription handles only visibility changes with their timestamps`() =
        runTest {
            every { mockedAmplitude.amplitudeScope } returns this
            val transitions = MutableSharedFlow<ProcessLifecycleObserver.Transition>()
            val processObserver = mockk<ProcessLifecycleObserver>(relaxed = true)
            every { processObserver.events } returns transitions
            plugin = AndroidLifecyclePlugin(observer, processObserver)
            plugin.setup(mockedAmplitude)
            advanceUntilIdle()
            assertEquals(1, transitions.subscriptionCount.value)

            for (transition in listOf(
                ProcessLifecycleObserver.Transition(100, false),
                ProcessLifecycleObserver.Transition(200, false),
                ProcessLifecycleObserver.Transition(300, true),
                ProcessLifecycleObserver.Transition(400, true),
                ProcessLifecycleObserver.Transition(500, false),
            )) {
                transitions.emit(transition)
                runCurrent()
            }

            verify(exactly = 1) { mockedAmplitude.onEnterForeground(300) }
            verify(exactly = 1) { mockedAmplitude.onEnterForeground(any()) }
            verify(exactly = 1) { mockedAmplitude.onExitForeground(100) }
            verify(exactly = 1) { mockedAmplitude.onExitForeground(500) }
            verify(exactly = 2) { mockedAmplitude.onExitForeground(any()) }
            close()
        }

    @Test
    fun `plugin starts and stops the injected process observer`() =
        runTest {
            every { mockedAmplitude.amplitudeScope } returns this
            val processObserver = mockk<ProcessLifecycleObserver>(relaxed = true)
            every { processObserver.events } returns MutableSharedFlow()
            plugin = AndroidLifecyclePlugin(observer, processObserver)
            coVerify(exactly = 0) { processObserver.start() }

            plugin.setup(mockedAmplitude)
            advanceUntilIdle()
            close()

            coVerify(exactly = 1) { processObserver.stop() }
            coVerify(exactly = 1) { processObserver.start() }
        }

    @Test
    fun `teardown before setup leaves the injected observer unstarted`() {
        val processObserver = mockk<ProcessLifecycleObserver>(relaxed = true)
        plugin = AndroidLifecyclePlugin(observer, processObserver)
        plugin.teardown()
        coVerify(exactly = 0) { processObserver.start() }
        coVerify(exactly = 0) { processObserver.stop() }
    }

    @Test
    fun `public constructor owns process observation through teardown`() =
        runTest {
            every { mockedAmplitude.amplitudeScope } returns this
            val lifecycle = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry
            val baseline = lifecycle.observerCount
            plugin = AndroidLifecyclePlugin(observer)
            runCurrent()
            assertEquals(baseline, lifecycle.observerCount)

            plugin.setup(mockedAmplitude)
            advanceUntilIdle()
            assertEquals(baseline + 1, lifecycle.observerCount)
            close()
            runCurrent()
            assertEquals(baseline, lifecycle.observerCount)
        }

    @Test
    fun `activity callbacks are collected when application lifecycle autocapture is disabled`() =
        runTest {
            mockAutocapture(setOf(AutocaptureOption.SCREEN_VIEWS))
            every { mockedAmplitude.amplitudeScope } returns this

            plugin.setup(mockedAmplitude)
            val activity = mockk<Activity>(relaxed = true)
            observer.onActivityCreated(activity, null)
            observer.onActivityStarted(activity)

            advanceUntilIdle()

            verify(exactly = 1) { mockedAmplitude.track(EventTypes.SCREEN_VIEWED, any()) }

            close()
        }

    @Test
    fun `test application installed event is tracked`() =
        runTest {
            mockAutocapture(setOf(AutocaptureOption.APP_LIFECYCLES))
            every { mockedAmplitude.amplitudeScope } returns this

            plugin.setup(mockedAmplitude)

            advanceUntilIdle()

            verify {
                mockedAmplitude.track(
                    EventTypes.APPLICATION_INSTALLED,
                    any(),
                    any(),
                )
            }

            coVerify(exactly = 1) { spiedStorage.write(eq(Storage.Constants.APP_VERSION), any()) }
            coVerify(exactly = 1) { spiedStorage.write(eq(Storage.Constants.APP_BUILD), any()) }

            close()
        }

    @Test
    fun `test application installed event is not tracked when disabled but storage is still persisted`() =
        runTest {
            every { mockedAmplitude.amplitudeScope } returns this

            plugin.setup(mockedAmplitude)

            advanceUntilIdle()

            verify(exactly = 0) {
                mockedAmplitude.track(
                    EventTypes.APPLICATION_INSTALLED,
                    any(),
                    any(),
                )
            }

            coVerify(exactly = 1) { spiedStorage.write(eq(Storage.Constants.APP_VERSION), any()) }
            coVerify(exactly = 1) { spiedStorage.write(eq(Storage.Constants.APP_BUILD), any()) }

            close()
        }

    @Test
    fun `test no spurious installed event when APP_LIFECYCLES is enabled later for an existing device`() =
        runTest {
            mockAutocapture(emptySet())
            every { mockedAmplitude.amplitudeScope } returns this

            plugin.setup(mockedAmplitude)
            advanceUntilIdle()

            verify(exactly = 0) { mockedAmplitude.track(EventTypes.APPLICATION_INSTALLED, any(), any()) }
            coVerify(exactly = 1) { spiedStorage.write(eq(Storage.Constants.APP_BUILD), any()) }

            close()

            mockAutocapture(setOf(AutocaptureOption.APP_LIFECYCLES))
            observer = ActivityLifecycleObserver()
            plugin = AndroidLifecyclePlugin(observer)
            plugin.setup(mockedAmplitude)
            advanceUntilIdle()

            verify(exactly = 0) { mockedAmplitude.track(EventTypes.APPLICATION_INSTALLED, any(), any()) }
            verify(exactly = 0) { mockedAmplitude.track(EventTypes.APPLICATION_UPDATED, any(), any()) }

            close()
        }

    @Test
    fun `test updated event fires when APP_LIFECYCLES is enabled later and build changed`() =
        runTest {
            mockAutocapture(emptySet())
            every { mockedAmplitude.amplitudeScope } returns this
            plugin.setup(mockedAmplitude)
            advanceUntilIdle()
            close()

            val updatedPackageInfo =
                PackageInfo().apply {
                    versionCode = 777
                    versionName = "7.0.0"
                }
            every { mockedPackageManager.getPackageInfo(any<String>(), any<Int>()) } returns updatedPackageInfo

            mockAutocapture(setOf(AutocaptureOption.APP_LIFECYCLES))
            observer = ActivityLifecycleObserver()
            plugin = AndroidLifecyclePlugin(observer)
            plugin.setup(mockedAmplitude)
            advanceUntilIdle()

            verify(exactly = 0) { mockedAmplitude.track(EventTypes.APPLICATION_INSTALLED, any(), any()) }
            verify(exactly = 1) { mockedAmplitude.track(EventTypes.APPLICATION_UPDATED, any(), any()) }

            close()
        }

    @Test
    fun `test application updated event is tracked`() =
        runTest {
            mockAutocapture(setOf(AutocaptureOption.APP_LIFECYCLES))
            every { mockedAmplitude.amplitudeScope } returns this

            // Stored previous version/build
            spiedStorage.write(Storage.Constants.APP_BUILD, "55")
            spiedStorage.write(Storage.Constants.APP_VERSION, "5.0.0")

            plugin.setup(mockedAmplitude)

            advanceUntilIdle()

            verify {
                mockedAmplitude.track(
                    EventTypes.APPLICATION_UPDATED,
                    any(),
                    any(),
                )
            }

            coVerify(exactly = 2) { spiedStorage.write(eq(Storage.Constants.APP_VERSION), any()) }
            coVerify(exactly = 2) { spiedStorage.write(eq(Storage.Constants.APP_BUILD), any()) }

            close()
        }

    @Test
    fun `test application updated event is not tracked when disabled but storage is still persisted`() =
        runTest {
            every { mockedAmplitude.amplitudeScope } returns this

            // Stored previous version/build
            spiedStorage.write(Storage.Constants.APP_BUILD, "55")
            spiedStorage.write(Storage.Constants.APP_VERSION, "5.0.0")

            plugin.setup(mockedAmplitude)

            advanceUntilIdle()

            verify(exactly = 0) {
                mockedAmplitude.track(
                    EventTypes.APPLICATION_UPDATED,
                    any(),
                )
            }

            coVerify(exactly = 2) { spiedStorage.write(eq(Storage.Constants.APP_VERSION), any()) }
            coVerify(exactly = 2) { spiedStorage.write(eq(Storage.Constants.APP_BUILD), any()) }

            close()
        }

    @Test
    fun `test fragment activity is tracked if enabled`() =
        runTest {
            mockAutocapture(
                setOf(
                    AutocaptureOption.APP_LIFECYCLES,
                    AutocaptureOption.SCREEN_VIEWS,
                ),
            )
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>()
            every { activity.intent } returns Intent()

            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit
            every { activity.unregisterFragmentLifecycleCallbacks(any()) } returns Unit

            observer.onActivityCreated(activity, mockk())
            observer.onActivityDestroyed(activity)

            advanceUntilIdle()

            verify(exactly = 1) {
                activity.registerFragmentLifecycleCallbacks(any(), any(), any())
            }

            verify(exactly = 1) {
                activity.unregisterFragmentLifecycleCallbacks(any())
            }

            close()
        }

    @Test
    fun `test fragment activity is not tracked if disabled`() =
        runTest {
            mockAutocapture(
                setOf(
                    AutocaptureOption.APP_LIFECYCLES,
                ),
            )
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>()
            every { activity.intent } returns Intent()

            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit
            every { activity.unregisterFragmentLifecycleCallbacks(any()) } returns Unit

            observer.onActivityCreated(activity, mockk())
            observer.onActivityDestroyed(activity)

            advanceUntilIdle()

            verify(exactly = 0) {
                activity.registerFragmentLifecycleCallbacks(any(), any(), any())
            }
            // unregister is always called in onActivityDestroyed for safe cleanup,
            // even when screen views are disabled (it's a no-op with no registered callbacks).
            verify(exactly = 1) {
                activity.unregisterFragmentLifecycleCallbacks(any())
            }

            close()
        }

    @Test
    fun `test fragment activity is tracked with screen views only`() =
        runTest {
            mockAutocapture(setOf(AutocaptureOption.SCREEN_VIEWS))
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>()
            every { activity.intent } returns Intent()

            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit
            every { activity.unregisterFragmentLifecycleCallbacks(any()) } returns Unit

            observer.onActivityCreated(activity, mockk())
            observer.onActivityDestroyed(activity)

            advanceUntilIdle()

            // Fragment callbacks should be registered when SCREEN_VIEWS is enabled
            verify(exactly = 1) {
                activity.registerFragmentLifecycleCallbacks(any(), any(), any())
            }

            verify(exactly = 1) {
                activity.unregisterFragmentLifecycleCallbacks(any())
            }

            close()
        }

    @Test
    fun `activity callbacks alone do not track application lifecycle events`() =
        runTest {
            mockAutocapture(setOf(AutocaptureOption.APP_LIFECYCLES))
            every { mockedAmplitude.amplitudeScope } returns this
            val activity = mockk<Activity>(relaxed = true)
            plugin.setup(mockedAmplitude)
            observer.onActivityCreated(activity, null)
            observer.onActivityStarted(activity)
            observer.onActivityStopped(activity)
            observer.onActivityDestroyed(activity)
            advanceUntilIdle()
            verify(exactly = 0) { mockedAmplitude.track(EventTypes.APPLICATION_OPENED, any(), any()) }
            verify(exactly = 0) { mockedAmplitude.track(EventTypes.APPLICATION_BACKGROUNDED, any(), any()) }
            close()
        }

    @Test
    fun `test application opened event is not tracked when disabled`() =
        runTest {
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>(relaxed = true)
            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit
            observer.onActivityCreated(activity, mockk())

            observer.onActivityStarted(activity)
            advanceUntilIdle()
            verify(exactly = 0) {
                mockedAmplitude.track(
                    eq(EventTypes.APPLICATION_OPENED),
                    match { param -> param.values.first() == false },
                )
            }

            observer.onActivityStopped(activity)
            advanceUntilIdle()
            verify(exactly = 0) {
                mockedAmplitude.track(
                    eq(EventTypes.APPLICATION_BACKGROUNDED),
                    any(),
                    any(),
                )
            }

            close()
        }

    @Test
    fun `test screen viewed event is tracked`() =
        runTest {
            mockAutocapture(
                setOf(
                    AutocaptureOption.APP_LIFECYCLES,
                    AutocaptureOption.SCREEN_VIEWS,
                ),
            )
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>(relaxed = true)
            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit
            observer.onActivityCreated(activity, mockk())
            observer.onActivityStarted(activity)

            advanceUntilIdle()

            verify(exactly = 1) {
                mockedAmplitude.track(
                    eq(EventTypes.SCREEN_VIEWED),
                    any(),
                )
            }

            close()
        }

    @Test
    fun `test screen viewed event is not tracked when disabled`() =
        runTest {
            mockAutocapture(
                setOf(
                    AutocaptureOption.APP_LIFECYCLES,
                ),
            )
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>(relaxed = true)
            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit
            observer.onActivityCreated(activity, mockk())
            observer.onActivityStarted(activity)

            advanceUntilIdle()

            verify(exactly = 0) {
                mockedAmplitude.track(
                    eq(EventTypes.SCREEN_VIEWED),
                    any(),
                )
            }

            close()
        }

    @Test
    fun `test deep link opened event is tracked`() =
        runTest {
            mockAutocapture(
                setOf(
                    AutocaptureOption.APP_LIFECYCLES,
                    AutocaptureOption.DEEP_LINKS,
                ),
            )
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>(relaxed = true)
            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit
            observer.onActivityCreated(activity, mockk())

            every { activity.intent } returns
                Intent().apply {
                    data = Uri.parse("android-app://com.android.unit-test")
                }
            observer.onActivityStarted(activity)

            advanceUntilIdle()

            verify(exactly = 1) {
                mockedAmplitude.track(
                    eq(EventTypes.DEEP_LINK_OPENED),
                    any(),
                )
            }

            close()
        }

    @Test
    fun `test deep link opened event is not tracked when disabled`() =
        runTest {
            mockAutocapture(
                setOf(
                    AutocaptureOption.APP_LIFECYCLES,
                ),
            )
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>(relaxed = true)
            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit
            observer.onActivityCreated(activity, mockk())

            every { activity.intent } returns
                Intent().apply {
                    data = Uri.parse("android-app://com.android.unit-test")
                }
            observer.onActivityStarted(activity)

            advanceUntilIdle()

            verify(exactly = 0) {
                mockedAmplitude.track(
                    eq(EventTypes.DEEP_LINK_OPENED),
                    any(),
                )
            }

            close()
        }

    @Test
    fun `test deep link opened event is not duplicated when resuming from background`() =
        runTest {
            mockAutocapture(
                setOf(
                    AutocaptureOption.DEEP_LINKS,
                ),
            )
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>(relaxed = true)
            val deepLinkIntent =
                Intent().apply {
                    data = Uri.parse("android-app://com.android.unit-test")
                }

            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit
            every { activity.intent } returns deepLinkIntent

            // First launch with deeplink
            observer.onActivityCreated(activity, mockk())
            observer.onActivityStarted(activity)
            advanceUntilIdle()

            // Background the app
            observer.onActivityStopped(activity)
            advanceUntilIdle()

            // Resume from background (same intent object)
            observer.onActivityStarted(activity)
            advanceUntilIdle()

            // Deep link should only be tracked once
            verify(exactly = 1) {
                mockedAmplitude.track(
                    eq(EventTypes.DEEP_LINK_OPENED),
                    any(),
                )
            }

            close()
        }

    @Test
    fun `test deep link opened event is tracked for new intent on same activity`() =
        runTest {
            mockAutocapture(
                setOf(
                    AutocaptureOption.DEEP_LINKS,
                ),
            )
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>(relaxed = true)
            val firstDeepLinkIntent =
                Intent().apply {
                    data = Uri.parse("android-app://com.android.unit-test/first")
                }
            val secondDeepLinkIntent =
                Intent().apply {
                    data = Uri.parse("android-app://com.android.unit-test/second")
                }

            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit

            // First launch with first deeplink
            every { activity.intent } returns firstDeepLinkIntent
            observer.onActivityCreated(activity, mockk())
            observer.onActivityStarted(activity)
            advanceUntilIdle()

            // Background the app
            observer.onActivityStopped(activity)
            advanceUntilIdle()

            // Resume with a NEW intent (e.g., user clicked another deeplink)
            every { activity.intent } returns secondDeepLinkIntent
            observer.onActivityStarted(activity)
            advanceUntilIdle()

            // Both deep links should be tracked
            verify(exactly = 2) {
                mockedAmplitude.track(
                    eq(EventTypes.DEEP_LINK_OPENED),
                    any(),
                )
            }

            close()
        }

    @Test
    fun `test deep link opened event is tracked via onResume after setIntent (onNewIntent scenario)`() =
        runTest {
            mockAutocapture(
                setOf(
                    AutocaptureOption.DEEP_LINKS,
                ),
            )
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>(relaxed = true)
            val firstDeepLinkIntent =
                Intent().apply {
                    data = Uri.parse("android-app://com.android.unit-test/first")
                }
            val secondDeepLinkIntent =
                Intent().apply {
                    data = Uri.parse("android-app://com.android.unit-test/second")
                }

            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit

            // First launch with first deeplink
            every { activity.intent } returns firstDeepLinkIntent
            observer.onActivityCreated(activity, mockk())
            observer.onActivityStarted(activity)
            observer.onActivityResumed(activity)
            advanceUntilIdle()

            // Verify first deep link tracked
            verify(exactly = 1) {
                mockedAmplitude.track(
                    eq(EventTypes.DEEP_LINK_OPENED),
                    any(),
                )
            }

            // Activity is paused (but NOT stopped) - e.g., a dialog appears or partial occlusion
            observer.onActivityPaused(activity)
            advanceUntilIdle()

            // Simulate onNewIntent() scenario:
            // Developer receives new intent and calls setIntent() in onNewIntent()
            // Then onResume() is called (but NOT onStart() since activity wasn't stopped)
            every { activity.intent } returns secondDeepLinkIntent
            observer.onActivityResumed(activity)
            advanceUntilIdle()

            // Both deep links should be tracked - the second one via onResume
            verify(exactly = 2) {
                mockedAmplitude.track(
                    eq(EventTypes.DEEP_LINK_OPENED),
                    any(),
                )
            }

            close()
        }

    @Test
    fun `test deep link is not duplicated when resuming without new intent`() =
        runTest {
            mockAutocapture(
                setOf(
                    AutocaptureOption.DEEP_LINKS,
                ),
            )
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>(relaxed = true)
            val deepLinkIntent =
                Intent().apply {
                    data = Uri.parse("android-app://com.android.unit-test")
                }

            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit
            every { activity.intent } returns deepLinkIntent

            // Full lifecycle: create -> start -> resume
            observer.onActivityCreated(activity, mockk())
            observer.onActivityStarted(activity)
            observer.onActivityResumed(activity)
            advanceUntilIdle()

            // Pause and resume (same intent, no onNewIntent called)
            observer.onActivityPaused(activity)
            observer.onActivityResumed(activity)
            advanceUntilIdle()

            // Deep link should only be tracked once despite being checked in both onStart and onResume
            verify(exactly = 1) {
                mockedAmplitude.track(
                    eq(EventTypes.DEEP_LINK_OPENED),
                    any(),
                )
            }

            close()
        }

    @Test
    fun `test complete screen views functionality works independently`() =
        runTest {
            mockAutocapture(setOf(AutocaptureOption.SCREEN_VIEWS))
            every { mockedAmplitude.amplitudeScope } returns this

            val activity = mockk<Activity>(relaxed = true)
            plugin.setup(mockedAmplitude)

            every { activity.registerFragmentLifecycleCallbacks(any(), any(), any()) } returns Unit
            every { activity.unregisterFragmentLifecycleCallbacks(any()) } returns Unit

            // Simulate activity lifecycle
            observer.onActivityCreated(activity, mockk())
            observer.onActivityStarted(activity)

            advanceUntilIdle()

            // 1. Activity screen view should be tracked
            verify(exactly = 1) {
                mockedAmplitude.track(
                    eq(EventTypes.SCREEN_VIEWED),
                    any(),
                )
            }

            // 2. Fragment lifecycle callbacks should be registered
            verify(exactly = 1) {
                activity.registerFragmentLifecycleCallbacks(any(), any(), any())
            }

            close()
        }

    private fun mockAutocapture(options: Set<AutocaptureOption>) {
        every { mockedConfig.autocapture } returns options
        val manager =
            AutocaptureManager(
                initialAutocapture = options,
                initialInteractionsOptions = InteractionsOptions(),
                remoteConfigClient = null,
                logger = mockk(relaxed = true),
            )
        every { mockedAmplitude.autocaptureManager } returns manager
    }

    // TODO Replace with Turbine
    private fun close() {
        plugin.teardown()
        observer.eventChannel.close()
        testDispatcher.scheduler.runCurrent()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkObject(FragmentActivityHandler)
    }
}
