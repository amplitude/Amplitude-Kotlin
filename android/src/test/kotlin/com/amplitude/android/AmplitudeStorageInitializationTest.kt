package com.amplitude.android

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.MainDispatcherRule
import com.amplitude.android.utilities.FakeAndroidAmplitude
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AmplitudeStorageInitializationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `event storage initialization must not hold the global instance registry monitor`() = runTest {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val provider = object : StorageProvider {
            override fun getStorage(amplitude: com.amplitude.core.Amplitude, prefix: String?): Storage {
                assertFalse(
                    Thread.holdsLock(AmplitudeRegistry),
                    "Slow event storage initialization blocks every concurrent Amplitude constructor",
                )
                return InMemoryStorageProvider().getStorage(amplitude, prefix)
            }
        }
        try {
            val amplitude = createFakeAmplitude(
                scheduler = testScheduler,
                configuration = Configuration(
                    apiKey = "test-api-key",
                    context = ApplicationProvider.getApplicationContext<Application>(),
                    instanceName = "storage-initialization-lock",
                    storageProvider = provider,
                    identifyInterceptStorageProvider = InMemoryStorageProvider(),
                    identityStorageProvider = IMIdentityStorageProvider(),
                    autocapture = setOf(),
                ),
            )
            advanceUntilIdle()
            amplitude.isBuilt.await()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }
    @Test
    @Config(sdk = [29])
    fun `legacy ANR setup must read crash preferences outside the global registry monitor`() = runTest {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        val application = spyk(ApplicationProvider.getApplicationContext<Application>())
        every { application.applicationContext } returns application
        val prefs = spyk(application.getSharedPreferences("amplitude_crash_tracking", Context.MODE_PRIVATE))
        every { application.getSharedPreferences("amplitude_crash_tracking", any()) } answers {
            assertFalse(Thread.holdsLock(AmplitudeRegistry), "Opening crash preferences must not block other constructors")
            prefs
        }
        every { prefs.getBoolean(any(), any()) } answers {
            assertFalse(Thread.holdsLock(AmplitudeRegistry), "Reading crash preferences must not block other constructors")
            false
        }
        // Keep remote config pending so ANR setup must use the persisted decision.
        val pausedNetwork = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
        }
        try {
            val amplitude = FakeAndroidAmplitude(
                configuration = Configuration(
                    apiKey = "test-api-key",
                    context = application,
                    instanceName = "legacy-anr-preferences-lock",
                    storageProvider = InMemoryStorageProvider(),
                    identifyInterceptStorageProvider = InMemoryStorageProvider(),
                    identityStorageProvider = IMIdentityStorageProvider(),
                    autocapture = setOf(),
                ),
                androidTestDispatcher = StandardTestDispatcher(testScheduler),
                networkIODispatcher = pausedNetwork,
            )
            advanceUntilIdle()
            amplitude.isBuilt.await()
            verify(exactly = 1) { prefs.getBoolean("enabled-legacy-anr-preferences-lock", false) }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }

}
