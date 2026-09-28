package com.amplitude.android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import com.amplitude.MainDispatcherRule
import com.amplitude.android.crash.CrashStorage
import com.amplitude.android.crash.CrashTrackingEnabledStore
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.android.utilities.setupMockAndroidContext
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, RestrictedAmplitudeFeature::class)
@RunWith(RobolectricTestRunner::class)
class AmplitudeStartupTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `crash handler is not registered when the previous launch left crash tracking off`() =
        runTest {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            val application = mockApplication(crashTrackingEnabled = false)
            try {
                val amplitude =
                    createFakeAmplitude(
                        scheduler = testScheduler,
                        configuration = configuration("crash-registration-off", application),
                    )

                assertSame(originalHandler, Thread.getDefaultUncaughtExceptionHandler())
                assertFalse(amplitude.isBuilt.isCompleted)

                advanceUntilIdle()
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            }
        }

    @Test
    fun `crash handler is registered during construction when the previous launch left crash tracking on`() =
        runTest {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            val application = mockApplication(crashTrackingEnabled = true)
            try {
                val amplitude =
                    createFakeAmplitude(
                        scheduler = testScheduler,
                        configuration = configuration("crash-registration-on", application),
                    )

                assertNotSame(originalHandler, Thread.getDefaultUncaughtExceptionHandler())
                assertFalse(amplitude.isBuilt.isCompleted)

                advanceUntilIdle()
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            }
        }

    @Test
    fun `crash handler is registered when local diagnostics is disabled but the previous run enabled tracking`() =
        runTest {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            val application = mockApplication(crashTrackingEnabled = true)
            try {
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration =
                        configuration(
                            instanceName = "crash-registration-diagnostics-off",
                            application = application,
                            enableDiagnostics = false,
                        ),
                )

                assertNotSame(originalHandler, Thread.getDefaultUncaughtExceptionHandler())
                advanceUntilIdle()
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            }
        }

    @Test
    fun `crash tracking turning on after construction does not register the handler`() =
        runTest {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            val application = mockApplication(crashTrackingEnabled = false)
            try {
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = configuration("crash-registration-later", application),
                )
                assertSame(originalHandler, Thread.getDefaultUncaughtExceptionHandler())

                CrashTrackingEnabledStore(application).setEnabled(true)

                assertSame(originalHandler, Thread.getDefaultUncaughtExceptionHandler())
                advanceUntilIdle()
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            }
        }

    @Test
    fun `a crash during startup is saved before the build finishes`() =
        runTest {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val application = mockApplication(crashTrackingEnabled = true)
            try {
                val amplitude =
                    createFakeAmplitude(
                        scheduler = testScheduler,
                        configuration = configuration("crash-startup", application),
                    )
                assertFalse(amplitude.isBuilt.isCompleted)

                Thread.getDefaultUncaughtExceptionHandler()!!
                    .uncaughtException(Thread.currentThread(), RuntimeException("startup"))

                // Unconfined, so the pending build does not consume the report before this read.
                val report =
                    CrashStorage(
                        appContext = application,
                        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                    ).consumePreviousCrash()
                assertTrue(report!!.contains("startup"))
                advanceUntilIdle()
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            }
        }

    @Test
    fun `a failed activation stops the crash handler`() =
        runTest {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val application = mockApplication(crashTrackingEnabled = true)
            every { application.registerActivityLifecycleCallbacks(any()) } throws
                IllegalStateException("cannot register")
            try {
                assertFailsWith<IllegalStateException> {
                    createFakeAmplitude(
                        scheduler = testScheduler,
                        configuration = configuration("crash-activation-fails", application),
                    )
                }
                Thread.getDefaultUncaughtExceptionHandler()!!
                    .uncaughtException(Thread.currentThread(), RuntimeException("after failure"))
                assertNull(
                    CrashStorage(
                        appContext = application,
                        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                    ).consumePreviousCrash(),
                )
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            }
        }

    @Test
    fun `the previous launch's crash is reported and then gone`() =
        runTest {
            val application = mockApplication(crashTrackingEnabled = false)
            CrashStorage(
                appContext = application,
                ioDispatcher = UnconfinedTestDispatcher(testScheduler),
            ).saveCrashReport(RuntimeException("previous launch"))

            val amplitude =
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = configuration("crash-report-previous", application),
                )
            advanceUntilIdle()
            assertTrue(amplitude.isBuilt.isCompleted)

            assertNull(
                CrashStorage(
                    appContext = application,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                ).consumePreviousCrash(),
            )
        }

    private fun configuration(
        instanceName: String,
        application: Application,
        enableDiagnostics: Boolean = true,
    ) = Configuration(
        apiKey = "api-key",
        context = application,
        instanceName = instanceName,
        autocapture = setOf(),
        loggerProvider = ConsoleLoggerProvider(),
        storageProvider = InMemoryStorageProvider(),
        identifyInterceptStorageProvider = InMemoryStorageProvider(),
        identityStorageProvider = IMIdentityStorageProvider(),
        enableDiagnostics = enableDiagnostics,
    )

    private fun mockApplication(crashTrackingEnabled: Boolean): Application {
        setupMockAndroidContext()
        val context = mockk<Application>(relaxed = true)
        every { context.applicationContext } returns context
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        val dirNameSlot = slot<String>()
        every { context.getDir(capture(dirNameSlot), any()) } answers {
            File("/tmp/amplitude-kotlin/${dirNameSlot.captured}")
        }
        val enabled = booleanArrayOf(crashTrackingEnabled)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putBoolean(any(), any()) } answers {
            enabled[0] = secondArg<Boolean>()
            editor
        }
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getBoolean(any(), any()) } answers { enabled[0] }
        every { prefs.edit() } returns editor
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }
}
