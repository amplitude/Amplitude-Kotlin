package com.amplitude.android.utilities

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.Configuration
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.Configuration.Companion.FLUSH_MAX_RETRIES
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import com.amplitude.android.Amplitude as AndroidAmplitude

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAndroidAmplitude(
    configuration: Configuration,
    val androidTestDispatcher: TestDispatcher = StandardTestDispatcher(),
    amplitudeScope: CoroutineScope = TestScope(androidTestDispatcher),
    amplitudeDispatcher: CoroutineDispatcher = androidTestDispatcher,
    networkIODispatcher: CoroutineDispatcher = androidTestDispatcher,
    storageIODispatcher: CoroutineDispatcher = androidTestDispatcher,
) : AndroidAmplitude(
        configuration = configuration,
        amplitudeScope = amplitudeScope,
        amplitudeDispatcher = amplitudeDispatcher,
        networkIODispatcher = networkIODispatcher,
        storageIODispatcher = storageIODispatcher,
    )

fun setupMockAndroidContext() {
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

    val context = mockk<Application>(relaxed = true)
    val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
    every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
    val dirNameSlot = slot<String>()
    every { context.getDir(capture(dirNameSlot), any()) } answers {
        File("/tmp/amplitude-kotlin/${dirNameSlot.captured}")
    }
}

/**
 * Creates a fake Amplitude instance for testing coroutines using the default [TestCoroutineScheduler] on [TestScope].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.createFakeAmplitude(server: MockWebServer) = createFakeAmplitude(server, testScheduler)

@OptIn(ExperimentalCoroutinesApi::class)
fun createFakeAmplitude(
    server: MockWebServer? = null,
    scheduler: TestCoroutineScheduler? = null,
    configuration: Configuration? = null,
) = FakeAndroidAmplitude(
    configuration =
        configuration ?: Configuration(
            apiKey = "test-api-key",
            context =
                ApplicationProvider.getApplicationContext<Context?>()
                    .also { setupMockAndroidContext() },
            serverUrl = server?.url("/")?.toString(),
            autocapture = setOf(),
            flushIntervalMillis = 150,
            identifyBatchIntervalMillis = 1000,
            flushMaxRetries = FLUSH_MAX_RETRIES,
            identityStorageProvider = IMIdentityStorageProvider(),
            storageProvider = InMemoryStorageProvider(),
        ),
    androidTestDispatcher = StandardTestDispatcher(scheduler),
)
