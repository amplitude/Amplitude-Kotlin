package com.amplitude.android.plugins

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.StubPlugin
import com.amplitude.android.utilities.DefaultEventUtils
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AndroidLifecyclePluginTest {
    private val androidLifecyclePlugin = AndroidLifecyclePlugin()
    private lateinit var amplitude: Amplitude
    private lateinit var configuration: Configuration

    private val mockedContext = mockk<Application>(relaxed = true)
    private var mockedPackageManager: PackageManager
    private lateinit var connectivityManager: ConnectivityManager

    init {
        val packageInfo = PackageInfo()
        @Suppress("DEPRECATION")
        packageInfo.versionCode = 66
        packageInfo.versionName = "6.0.0"

        mockedPackageManager = mockk<PackageManager> {
            every { getPackageInfo("com.plugin.test", 0) } returns packageInfo
        }
        every { mockedContext.packageName } returns "com.plugin.test"
        every { mockedContext.packageManager } returns mockedPackageManager
    }

    private fun setDispatcher(testScheduler: TestCoroutineScheduler) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // inject the amplitudeDispatcher field with reflection, as the field is val (read-only)
        val amplitudeDispatcherField = com.amplitude.core.Amplitude::class.java.getDeclaredField("amplitudeDispatcher")
        amplitudeDispatcherField.isAccessible = true
        amplitudeDispatcherField.set(amplitude, dispatcher)
    }

    @Before
    fun setup() {
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

        connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { mockedContext!!.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager

        configuration =
            Configuration(
                apiKey = "api-key",
                context = mockedContext,
                storageProvider = InMemoryStorageProvider(),
                loggerProvider = ConsoleLoggerProvider(),
                identifyInterceptStorageProvider = InMemoryStorageProvider(),
                identityStorageProvider = IMIdentityStorageProvider(),
                trackingSessionEvents = false,
            )
        amplitude = Amplitude(configuration)
    }

    @Test
    fun `test application installed event is tracked`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.appLifecycles = true
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedActivity = mockk<Activity>()
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(1, tracks.count())

        with(tracks[0]) {
            Assertions.assertEquals(DefaultEventUtils.EventTypes.APPLICATION_INSTALLED, eventType)
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.BUILD), "66")
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.VERSION), "6.0.0")
        }
    }

    @Test
    fun `test application installed event is not tracked when disabled`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.appLifecycles = false
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedActivity = mockk<Activity>()
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify(exactly = 0) { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(0, tracks.count())
    }

    @Test
    fun `test application updated event is tracked`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.appLifecycles = true
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        // Stored previous version/build
        amplitude.storage.write(Storage.Constants.APP_BUILD, "55")
        amplitude.storage.write(Storage.Constants.APP_VERSION, "5.0.0")

        val mockedActivity = mockk<Activity>()
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(1, tracks.count())

        with(tracks[0]) {
            Assertions.assertEquals(DefaultEventUtils.EventTypes.APPLICATION_UPDATED, eventType)
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.BUILD), "66")
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.VERSION), "6.0.0")
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.PREVIOUS_BUILD), "55")
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.PREVIOUS_VERSION), "5.0.0")
        }
    }

    @Test
    fun `test application updated event is not tracked when disabled`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.appLifecycles = false
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        // Stored previous version/build
        amplitude.storage.write(Storage.Constants.APP_BUILD, "55")
        amplitude.storage.write(Storage.Constants.APP_VERSION, "5.0.0")

        val mockedActivity = mockk<Activity>()
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify(exactly = 0) { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(0, tracks.count())
    }

    @Test
    fun `test application opened event is tracked`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.appLifecycles = true
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedActivity = mockk<Activity>()
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)
        androidLifecyclePlugin.onActivityStarted(mockedActivity)
        androidLifecyclePlugin.onActivityResumed(mockedActivity)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(2, tracks.count())

        with(tracks[0]) {
            Assertions.assertEquals(DefaultEventUtils.EventTypes.APPLICATION_INSTALLED, eventType)
        }
        with(tracks[1]) {
            Assertions.assertEquals(DefaultEventUtils.EventTypes.APPLICATION_OPENED, eventType)
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.BUILD), "66")
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.VERSION), "6.0.0")
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.FROM_BACKGROUND), false)
        }
    }

    @Test
    fun `test application opened event is not tracked when disabled`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.appLifecycles = false
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedActivity = mockk<Activity>()
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)
        androidLifecyclePlugin.onActivityStarted(mockedActivity)
        androidLifecyclePlugin.onActivityResumed(mockedActivity)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify(exactly = 0) { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(0, tracks.count())
    }

    @Test
    fun `test application backgrounded event is tracked`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.appLifecycles = true
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedActivity = mockk<Activity>()
        androidLifecyclePlugin.onActivityPaused(mockedActivity)
        androidLifecyclePlugin.onActivityStopped(mockedActivity)
        androidLifecyclePlugin.onActivityDestroyed(mockedActivity)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(1, tracks.count())

        with(tracks[0]) {
            Assertions.assertEquals(DefaultEventUtils.EventTypes.APPLICATION_BACKGROUNDED, eventType)
        }
    }

    @Test
    fun `test application backgrounded event is not tracked when disabled`() = runTest {
        setDispatcher(testScheduler)
        (amplitude.configuration as Configuration).defaultTracking.appLifecycles = false
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedActivity = mockk<Activity>()
        androidLifecyclePlugin.onActivityPaused(mockedActivity)
        androidLifecyclePlugin.onActivityStopped(mockedActivity)
        androidLifecyclePlugin.onActivityDestroyed(mockedActivity)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify(exactly = 0) { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(0, tracks.count())
    }

    @Test
    fun `test screen viewed event is tracked`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.screenViews = true
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedActivity = mockk<Activity>()
        every { mockedActivity.packageManager } returns mockedPackageManager
        every { mockedActivity.componentName } returns mockk()
        val mockedActivityInfo = mockk<ActivityInfo>()
        every { mockedPackageManager.getActivityInfo(any(), any()) } returns mockedActivityInfo
        every { mockedActivityInfo.loadLabel(mockedPackageManager) } returns "test-label"
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)
        androidLifecyclePlugin.onActivityStarted(mockedActivity)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(1, tracks.count())

        with(tracks[0]) {
            Assertions.assertEquals(DefaultEventUtils.EventTypes.SCREEN_VIEWED, eventType)
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.SCREEN_NAME), "test-label")
        }
    }

    @Test
    fun `test screen viewed event is not tracked when disabled`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.screenViews = false
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedActivity = mockk<Activity>()
        every { mockedActivity.packageManager } returns mockedPackageManager
        every { mockedActivity.componentName } returns mockk()
        val mockedActivityInfo = mockk<ActivityInfo>()
        every { mockedPackageManager.getActivityInfo(any(), any()) } returns mockedActivityInfo
        every { mockedActivityInfo.loadLabel(mockedPackageManager) } returns "test-label"
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)
        androidLifecyclePlugin.onActivityStarted(mockedActivity)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify(exactly = 0) { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(0, tracks.count())
    }

    @Test
    fun `test deep link opened event is tracked`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.deepLinks = true
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedIntent = mockk<Intent>()
        every { mockedIntent.data } returns Uri.parse("app://url.com/open")
        val mockedActivity = mockk<Activity>()
        every { mockedActivity.intent } returns mockedIntent
        every { mockedActivity.referrer } returns Uri.parse("android-app://com.android.chrome")
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(1, tracks.count())

        with(tracks[0]) {
            Assertions.assertEquals(DefaultEventUtils.EventTypes.DEEP_LINK_OPENED, eventType)
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.LINK_URL), "app://url.com/open")
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.LINK_REFERRER), "android-app://com.android.chrome")
        }
    }

    @Config(sdk = [21])
    @Test
    fun `test deep link opened event is tracked when using sdk is between 17 and 21`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.deepLinks = true
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedIntent = mockk<Intent>()
        every { mockedIntent.data } returns Uri.parse("app://url.com/open")
        every { mockedIntent.getParcelableExtra<Uri>(any()) } returns Uri.parse("android-app://com.android.chrome")
        val mockedActivity = mockk<Activity>()
        every { mockedActivity.intent } returns mockedIntent
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(1, tracks.count())

        with(tracks[0]) {
            Assertions.assertEquals(DefaultEventUtils.EventTypes.DEEP_LINK_OPENED, eventType)
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.LINK_URL), "app://url.com/open")
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.LINK_REFERRER), "android-app://com.android.chrome")
        }
    }

    @Config(sdk = [16])
    @Test
    fun `test deep link opened event is tracked when using sdk is lower than 17`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.deepLinks = true
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedIntent = mockk<Intent>()
        every { mockedIntent.data } returns Uri.parse("app://url.com/open")
        every { mockedIntent.getParcelableExtra<Uri>(any()) } returns Uri.parse("android-app://com.android.chrome")
        val mockedActivity = mockk<Activity>()
        every { mockedActivity.intent } returns mockedIntent
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(1, tracks.count())

        with(tracks[0]) {
            Assertions.assertEquals(DefaultEventUtils.EventTypes.DEEP_LINK_OPENED, eventType)
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.LINK_URL), "app://url.com/open")
            Assertions.assertEquals(eventProperties?.get(DefaultEventUtils.EventProperties.LINK_REFERRER), null)
        }
    }

    @Test
    fun `test deep link opened event is not tracked when disabled`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.deepLinks = false
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedIntent = mockk<Intent>()
        every { mockedIntent.data } returns Uri.parse("app://url.com/open")
        val mockedActivity = mockk<Activity>()
        every { mockedActivity.intent } returns mockedIntent
        every { mockedActivity.referrer } returns Uri.parse("android-app://com.android.chrome")
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify(exactly = 0) { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(0, tracks.count())
    }

    @Test
    fun `test deep link opened event is not tracked when URL is missing`() = runTest {
        setDispatcher(testScheduler)
        configuration.defaultTracking.deepLinks = true
        amplitude.add(androidLifecyclePlugin)

        val mockedPlugin = spyk(StubPlugin())
        amplitude.add(mockedPlugin)
        amplitude.isBuilt.await()

        val mockedIntent = mockk<Intent>()
        every { mockedIntent.data } returns null
        val mockedActivity = mockk<Activity>()
        every { mockedActivity.intent } returns mockedIntent
        every { mockedActivity.referrer } returns Uri.parse("android-app://com.android.unit-test")
        val mockedBundle = mockk<Bundle>()
        androidLifecyclePlugin.onActivityCreated(mockedActivity, mockedBundle)

        advanceUntilIdle()
        Thread.sleep(100)

        val tracks = mutableListOf<BaseEvent>()
        verify(exactly = 0) { mockedPlugin.track(capture(tracks)) }
        Assertions.assertEquals(0, tracks.count())
    }
}
