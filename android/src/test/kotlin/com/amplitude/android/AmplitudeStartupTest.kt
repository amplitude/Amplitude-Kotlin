package com.amplitude.android

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Build
import com.amplitude.MainDispatcherRule
import com.amplitude.android.anr.AnrStorage
import com.amplitude.android.anr.LegacyAnrCatcher
import com.amplitude.android.anr.recordAnr
import com.amplitude.android.crash.CrashStorage
import com.amplitude.android.crash.CrashTrackingEnabledStore
import com.amplitude.android.crash.recordCrash
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.android.utilities.FakeAndroidAmplitude
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.android.utilities.setupMockAndroidContext
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @After
    fun tearDown() {
        unmockkConstructor(AndroidContextProvider::class)
        unmockkStatic(AndroidLifecyclePlugin::class)
    }

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
    fun `crash handler startup uses the decision for its instance name`() =
        runTest {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            val application = mockApplication(crashTrackingEnabled = false)
            CrashTrackingEnabledStore(application, "crash-enabled").setEnabled(true)
            CrashTrackingEnabledStore(application, "crash-disabled").setEnabled(false)
            try {
                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = configuration("crash-enabled", application),
                )
                val enabledHandler = Thread.getDefaultUncaughtExceptionHandler()
                assertNotSame(originalHandler, enabledHandler)

                createFakeAmplitude(
                    scheduler = testScheduler,
                    configuration = configuration("crash-disabled", application),
                )
                assertSame(enabledHandler, Thread.getDefaultUncaughtExceptionHandler())
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

                CrashTrackingEnabledStore(application, "crash-registration-later").setEnabled(true)

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

                // Read synchronously before the pending build can consume the report.
                val report =
                    CrashStorage(
                        appContext = application,
                        instanceName = "crash-startup",
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
                        instanceName = "crash-activation-fails",
                    ).consumePreviousCrash(),
                )
            } finally {
                Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            }
        }

    @Test
    fun `a replaced build must leave the previous crash for its active replacement`() =
        runTest {
            val application = mockApplication(crashTrackingEnabled = false)
            val instanceName = "crash-replacement-during-read"
            val dispatcher = StandardTestDispatcher(testScheduler)
            val storageDispatcher = PausedStorageDispatcher(dispatcher)
            val recordedBy = mutableListOf<DiagnosticsClient>()
            mockkStatic("com.amplitude.android.crash.CrashDiagnosticsKt")
            every { any<DiagnosticsClient>().recordCrash(any()) } answers {
                recordedBy.add(firstArg())
                Unit
            }
            try {
                CrashStorage(application, instanceName)
                    .saveCrashReport(RuntimeException("previous launch"))
                val first =
                    FakeAndroidAmplitude(
                        configuration = configuration(instanceName, application),
                        androidTestDispatcher = dispatcher,
                        storageIODispatcher = storageDispatcher,
                    )
                runCurrent()
                assertFalse(first.isBuilt.isCompleted)
                val second =
                    FakeAndroidAmplitude(
                        configuration = configuration(instanceName, application),
                        androidTestDispatcher = dispatcher,
                        storageIODispatcher = storageDispatcher,
                    )
                runCurrent()
                assertFalse(first.isActive)
                assertTrue(second.isActive)

                storageDispatcher.resume()
                advanceUntilIdle()

                assertEquals(listOf(second.diagnosticsClient), recordedBy)
                assertTrue(first.isBuilt.isCompleted)
                assertTrue(second.isBuilt.isCompleted)
            } finally {
                unmockkStatic("com.amplitude.android.crash.CrashDiagnosticsKt")
            }
        }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q, Build.VERSION_CODES.R])
    fun `a replaced build must leave the previous ANR for its active replacement`() =
        runTest {
            val application = mockApplication(crashTrackingEnabled = true)
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            val instanceName = "anr-replacement-during-read"
            val dispatcher = StandardTestDispatcher(testScheduler)
            val storageDispatcher = PausedStorageDispatcher(dispatcher)
            val recordedBy = mutableListOf<DiagnosticsClient>()
            mockkStatic("com.amplitude.android.anr.AnrDiagnosticsKt")
            every { any<DiagnosticsClient>().recordAnr(any()) } answers {
                recordedBy.add(firstArg())
                Unit
            }
            try {
                val storage = AnrStorage(application, instanceName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    storage.saveLastAeiTimestamp(0L)
                    val activityManager = mockk<ActivityManager>()
                    val exit = mockk<ApplicationExitInfo>()
                    every { exit.reason } returns ApplicationExitInfo.REASON_ANR
                    every { exit.timestamp } returns 1_000L
                    every { exit.pid } returns 42
                    every { exit.description } returns "previous launch"
                    every { exit.traceInputStream } answers { "previous trace".byteInputStream() }
                    every { application.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
                    every { activityManager.getHistoricalProcessExitReasons(any(), 0, 0) } returns listOf(exit)
                } else {
                    storage.saveAnrReport(5_000)
                }
                val first =
                    FakeAndroidAmplitude(
                        configuration = configuration(instanceName, application),
                        androidTestDispatcher = dispatcher,
                        storageIODispatcher = storageDispatcher,
                    )
                runCurrent()
                assertFalse(first.isBuilt.isCompleted)
                val second =
                    FakeAndroidAmplitude(
                        configuration = configuration(instanceName, application),
                        androidTestDispatcher = dispatcher,
                        storageIODispatcher = storageDispatcher,
                    )
                runCurrent()
                assertFalse(first.isActive)
                assertTrue(second.isActive)

                storageDispatcher.resume()
                advanceUntilIdle()

                assertEquals(listOf(second.diagnosticsClient), recordedBy)
                assertTrue(first.isBuilt.isCompleted)
                assertTrue(second.isBuilt.isCompleted)
            } finally {
                unmockkStatic("com.amplitude.android.anr.AnrDiagnosticsKt")
                LegacyAnrCatcher.stopWatchdog()
                Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            }
        }

    @Test
    fun `the previous launch's crash is reported and then gone`() =
        runTest {
            val application = mockApplication(crashTrackingEnabled = false)
            CrashStorage(
                appContext = application,
                instanceName = "crash-report-previous",
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
                    instanceName = "crash-report-previous",
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
        val enabled = mutableMapOf<String, Boolean>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putBoolean(any(), any()) } answers {
            enabled[firstArg<String>()] = secondArg<Boolean>()
            editor
        }
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getBoolean(any(), any()) } answers { enabled[firstArg<String>()] ?: crashTrackingEnabled }
        every { prefs.edit() } returns editor
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }
}

private class PausedStorageDispatcher(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    private val pending = mutableListOf<() -> Unit>()
    private var paused = true

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        if (paused) {
            pending.add { delegate.dispatch(context, block) }
        } else {
            delegate.dispatch(context, block)
        }
    }

    fun resume() {
        paused = false
        pending.forEach { it() }
        pending.clear()
    }
}
