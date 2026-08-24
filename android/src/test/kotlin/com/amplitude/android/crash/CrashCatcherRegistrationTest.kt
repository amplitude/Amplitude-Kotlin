package com.amplitude.android.crash

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.MainDispatcherRule
import com.amplitude.android.Configuration
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.android.utilities.setupMockAndroidContext
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkConstructor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CrashCatcherRegistrationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val application = mockApplication()

    @Test
    fun `crash handler is registered while the instance is still being constructed`() =
        runTest {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            try {
                val amplitude =
                    createFakeAmplitude(
                        scheduler = testScheduler,
                        configuration = configuration("crash-registration"),
                    )

                assertNotSame(originalHandler, Thread.getDefaultUncaughtExceptionHandler())
                assertFalse(amplitude.isBuilt.isCompleted)

                advanceUntilIdle()
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            }
        }

    @Test
    fun `failed build before activate detaches watchers`() =
        runTest {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            mockkConstructor(CrashTrackingRemoteConfig::class)
            every { anyConstructed<CrashTrackingRemoteConfig>().initialize() } throws
                IllegalStateException("remote config failed")
            try {
                assertFailsWith<IllegalStateException> {
                    createFakeAmplitude(
                        scheduler = testScheduler,
                        configuration = configuration("crash-build-failure"),
                    )
                }
                Thread.getDefaultUncaughtExceptionHandler()!!
                    .uncaughtException(
                        Thread.currentThread(),
                        RuntimeException("boom").apply {
                            stackTrace =
                                arrayOf(
                                    StackTraceElement(
                                        "com.amplitude.core.platform.EventPipeline",
                                        "method",
                                        "File.kt",
                                        1,
                                    ),
                                )
                        },
                    )
                assertNull(
                    CrashStorage(
                        appContext = application,
                        ioDispatcher = StandardTestDispatcher(testScheduler),
                    ).consumePreviousCrash(),
                )
            } finally {
                unmockkConstructor(CrashTrackingRemoteConfig::class)
                Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            }
        }

    private fun configuration(instanceName: String) =
        Configuration(
            apiKey = "api-key",
            context = application,
            instanceName = instanceName,
            autocapture = setOf(),
            loggerProvider = ConsoleLoggerProvider(),
            storageProvider = InMemoryStorageProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
        )

    private fun mockApplication(): Application {
        setupMockAndroidContext()
        val context = mockk<Application>(relaxed = true)
        every { context.applicationContext } returns context
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        val dirNameSlot = slot<String>()
        every { context.getDir(capture(dirNameSlot), any()) } answers {
            File("/tmp/amplitude-kotlin/${dirNameSlot.captured}")
        }
        return context
    }
}
