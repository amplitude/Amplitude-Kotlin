package com.amplitude.android.plugins

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.Configuration
import com.amplitude.android.internal.fragments.FragmentActivityHandler
import com.amplitude.android.internal.fragments.FragmentActivityHandler.registerFragmentLifecycleCallbacks
import com.amplitude.android.internal.fragments.FragmentActivityHandler.unregisterFragmentLifecycleCallbacks
import com.amplitude.android.utilities.ActivityLifecycleObserver
import com.amplitude.android.utilities.DefaultEventUtils
import com.amplitude.core.Storage
import com.amplitude.core.utilities.InMemoryStorage
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
    private val packageInfo = PackageInfo().apply {
        versionCode = 66
        longVersionCode = 66
        versionName = "6.0.0"
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

        observer = ActivityLifecycleObserver()
        plugin = AndroidLifecyclePlugin(observer)

        mockkObject(FragmentActivityHandler)
    }

    @Test
    fun `test application installed event is tracked`() = runTest {
        every { mockedConfig.autocapture } returns setOf(AutocaptureOption.APP_LIFECYCLES)
        every { mockedAmplitude.amplitudeScope } returns this

        plugin.setup(mockedAmplitude)

        advanceUntilIdle()

        verify {
            mockedAmplitude.track(
                DefaultEventUtils.EventTypes.APPLICATION_INSTALLED,
                any(),
                any()
            )
        }

        coVerify(exactly = 1) { spiedStorage.write(eq(Storage.Constants.APP_VERSION), any()) }
        coVerify(exactly = 1) { spiedStorage.write(eq(Storage.Constants.APP_BUILD), any()) }

        close()
    }

    @Test
    fun `test application installed event is not tracked when disabled`() = runTest {
        every { mockedAmplitude.amplitudeScope } returns this

        plugin.setup(mockedAmplitude)

        advanceUntilIdle()

        verify(exactly = 0) {
            mockedAmplitude.track(
                DefaultEventUtils.EventTypes.APPLICATION_INSTALLED,
                any(),
                any()
            )
        }

        coVerify(exactly = 0) { spiedStorage.write(eq(Storage.Constants.APP_VERSION), any()) }
        coVerify(exactly = 0) { spiedStorage.write(eq(Storage.Constants.APP_BUILD), any()) }

        close()
    }

    @Test
    fun `test application updated event is tracked`() = runTest {
        every { mockedConfig.autocapture } returns setOf(AutocaptureOption.APP_LIFECYCLES)
        every { mockedAmplitude.amplitudeScope } returns this

        // Stored previous version/build
        spiedStorage.write(Storage.Constants.APP_BUILD, "55")
        spiedStorage.write(Storage.Constants.APP_VERSION, "5.0.0")

        plugin.setup(mockedAmplitude)

        advanceUntilIdle()

        verify {
            mockedAmplitude.track(
                DefaultEventUtils.EventTypes.APPLICATION_UPDATED,
                any(),
                any()
            )
        }

        coVerify(exactly = 2) { spiedStorage.write(eq(Storage.Constants.APP_VERSION), any()) }
        coVerify(exactly = 2) { spiedStorage.write(eq(Storage.Constants.APP_BUILD), any()) }

        close()
    }

    @Test
    fun `test application updated event is not tracked when disabled`() = runTest {
        every { mockedAmplitude.amplitudeScope } returns this

        // Stored previous version/build
        spiedStorage.write(Storage.Constants.APP_BUILD, "55")
        spiedStorage.write(Storage.Constants.APP_VERSION, "5.0.0")

        plugin.setup(mockedAmplitude)

        advanceUntilIdle()

        verify(exactly = 0) {
            mockedAmplitude.track(
                DefaultEventUtils.EventTypes.APPLICATION_UPDATED,
                any(),
            )
        }

        coVerify(exactly = 1) { spiedStorage.write(eq(Storage.Constants.APP_VERSION), any()) }
        coVerify(exactly = 1) { spiedStorage.write(eq(Storage.Constants.APP_BUILD), any()) }

        close()
    }

    @Test
    fun `test fragment activity is tracked if enabled`() = runTest {
        every { mockedConfig.autocapture } returns setOf(
            AutocaptureOption.APP_LIFECYCLES,
            AutocaptureOption.SCREEN_VIEWS
        )
        every { mockedAmplitude.amplitudeScope } returns this

        val activity = mockk<Activity>()
        every { activity.intent } returns Intent()

        plugin.setup(mockedAmplitude)

        every { activity.registerFragmentLifecycleCallbacks(any(), any()) } returns Unit
        every { activity.unregisterFragmentLifecycleCallbacks(any()) } returns Unit

        observer.onActivityCreated(activity, mockk())
        observer.onActivityDestroyed(activity)

        advanceUntilIdle()

        verify(exactly = 1) {
            activity.registerFragmentLifecycleCallbacks(any(), any())
        }

        verify(exactly = 1) {
            activity.unregisterFragmentLifecycleCallbacks(any())
        }

        close()
    }

    @Test
    fun `test fragment activity is not tracked if disabled`() = runTest {
        every { mockedConfig.autocapture } returns setOf(
            AutocaptureOption.APP_LIFECYCLES
        )
        every { mockedAmplitude.amplitudeScope } returns this

        val activity = mockk<Activity>()
        every { activity.intent } returns Intent()

        plugin.setup(mockedAmplitude)

        every { activity.registerFragmentLifecycleCallbacks(any(), any()) } returns Unit
        every { activity.unregisterFragmentLifecycleCallbacks(any()) } returns Unit

        observer.onActivityCreated(activity, mockk())
        observer.onActivityDestroyed(activity)

        advanceUntilIdle()

        verify(exactly = 0) {
            activity.registerFragmentLifecycleCallbacks(any(), any())
        }

        verify(exactly = 0) {
            activity.unregisterFragmentLifecycleCallbacks(any())
        }

        close()
    }

    @Test
    fun `test application opened event is tracked not from background`() = runTest {
        every { mockedConfig.autocapture } returns setOf(AutocaptureOption.APP_LIFECYCLES)
        every { mockedAmplitude.amplitudeScope } returns this

        val activity = mockk<Activity>(relaxed = true)
        plugin.setup(mockedAmplitude)

        every { activity.registerFragmentLifecycleCallbacks(any(), any()) } returns Unit
        observer.onActivityCreated(activity, mockk())
        observer.onActivityStarted(activity)

        advanceUntilIdle()

        verify(exactly = 1) {
            mockedAmplitude.track(
                eq(DefaultEventUtils.EventTypes.APPLICATION_OPENED),
                match { param -> param.values.first() == false },
            )
        }

        close()
    }

    @Test
    fun `test application opened event is tracked from background`() = runTest {
        every { mockedConfig.autocapture } returns setOf(AutocaptureOption.APP_LIFECYCLES)
        every { mockedAmplitude.amplitudeScope } returns this

        val activity = mockk<Activity>(relaxed = true)
        plugin.setup(mockedAmplitude)

        every { activity.registerFragmentLifecycleCallbacks(any(), any()) } returns Unit
        observer.onActivityCreated(activity, mockk())

        observer.onActivityStarted(activity)
        advanceUntilIdle()
        verify(exactly = 1) {
            mockedAmplitude.track(
                eq(DefaultEventUtils.EventTypes.APPLICATION_OPENED),
                match { param -> param.values.first() == false },
                any()
            )
        }

        observer.onActivityStopped(activity)
        advanceUntilIdle()
        verify(exactly = 1) {
            mockedAmplitude.track(
                eq(DefaultEventUtils.EventTypes.APPLICATION_BACKGROUNDED),
                any(),
                any()
            )
        }

        observer.onActivityStarted(activity)
        advanceUntilIdle()
        verify(exactly = 1) {
            mockedAmplitude.track(
                DefaultEventUtils.EventTypes.APPLICATION_OPENED,
                match { param -> param.values.first() == false },
                any()
            )
        }
        close()
    }

    @Test
    fun `test application opened event is not tracked when disabled`() = runTest {
        every { mockedAmplitude.amplitudeScope } returns this

        val activity = mockk<Activity>(relaxed = true)
        plugin.setup(mockedAmplitude)

        every { activity.registerFragmentLifecycleCallbacks(any(), any()) } returns Unit
        observer.onActivityCreated(activity, mockk())

        observer.onActivityStarted(activity)
        advanceUntilIdle()
        verify(exactly = 0) {
            mockedAmplitude.track(
                eq(DefaultEventUtils.EventTypes.APPLICATION_OPENED),
                match { param -> param.values.first() == false },
            )
        }

        observer.onActivityStopped(activity)
        advanceUntilIdle()
        verify(exactly = 0) {
            mockedAmplitude.track(
                eq(DefaultEventUtils.EventTypes.APPLICATION_BACKGROUNDED),
                any(),
                any()
            )
        }

        close()
    }

    @Test
    fun `test screen viewed event is tracked`() = runTest {
        every { mockedConfig.autocapture } returns setOf(
            AutocaptureOption.APP_LIFECYCLES,
            AutocaptureOption.SCREEN_VIEWS
        )
        every { mockedAmplitude.amplitudeScope } returns this

        val activity = mockk<Activity>(relaxed = true)
        plugin.setup(mockedAmplitude)

        every { activity.registerFragmentLifecycleCallbacks(any(), any()) } returns Unit
        observer.onActivityCreated(activity, mockk())
        observer.onActivityStarted(activity)

        advanceUntilIdle()

        verify(exactly = 1) {
            mockedAmplitude.track(
                eq(DefaultEventUtils.EventTypes.SCREEN_VIEWED),
                any(),
            )
        }

        close()
    }

    @Test
    fun `test screen viewed event is not tracked when disabled`() = runTest {
        every { mockedConfig.autocapture } returns setOf(
            AutocaptureOption.APP_LIFECYCLES,
        )
        every { mockedAmplitude.amplitudeScope } returns this

        val activity = mockk<Activity>(relaxed = true)
        plugin.setup(mockedAmplitude)

        every { activity.registerFragmentLifecycleCallbacks(any(), any()) } returns Unit
        observer.onActivityCreated(activity, mockk())
        observer.onActivityStarted(activity)

        advanceUntilIdle()

        verify(exactly = 0) {
            mockedAmplitude.track(
                eq(DefaultEventUtils.EventTypes.SCREEN_VIEWED),
                any(),
            )
        }

        close()
    }

    @Test
    fun `test deep link opened event is tracked`() = runTest {
        every { mockedConfig.autocapture } returns setOf(
            AutocaptureOption.APP_LIFECYCLES,
            AutocaptureOption.DEEP_LINKS
        )
        every { mockedAmplitude.amplitudeScope } returns this

        val activity = mockk<Activity>(relaxed = true)
        plugin.setup(mockedAmplitude)

        every { activity.registerFragmentLifecycleCallbacks(any(), any()) } returns Unit
        observer.onActivityCreated(activity, mockk())

        every { activity.intent } returns Intent().apply {
            data = Uri.parse("android-app://com.android.unit-test")
        }
        observer.onActivityStarted(activity)

        advanceUntilIdle()

        verify(exactly = 1) {
            mockedAmplitude.track(
                eq(DefaultEventUtils.EventTypes.DEEP_LINK_OPENED),
                any(),
            )
        }

        close()
    }

    @Test
    fun `test deep link opened event is not tracked when disabled`() = runTest {
        every { mockedConfig.autocapture } returns setOf(
            AutocaptureOption.APP_LIFECYCLES,
        )
        every { mockedAmplitude.amplitudeScope } returns this

        val activity = mockk<Activity>(relaxed = true)
        plugin.setup(mockedAmplitude)

        every { activity.registerFragmentLifecycleCallbacks(any(), any()) } returns Unit
        observer.onActivityCreated(activity, mockk())

        every { activity.intent } returns Intent().apply {
            data = Uri.parse("android-app://com.android.unit-test")
        }
        observer.onActivityStarted(activity)

        advanceUntilIdle()

        verify(exactly = 0) {
            mockedAmplitude.track(
                eq(DefaultEventUtils.EventTypes.DEEP_LINK_OPENED),
                any(),
            )
        }

        close()
    }

    // TODO Replace with Turbine
    private suspend fun close() {
        observer.eventChannel.close()
        plugin.eventJob?.join()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkObject(FragmentActivityHandler)
    }
}
