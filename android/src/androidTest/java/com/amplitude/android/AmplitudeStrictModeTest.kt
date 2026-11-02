package com.amplitude.android

import android.app.Application
import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.amplitude.android.crash.CrashStorage
import com.amplitude.android.crash.CrashTrackingEnabledStore
import com.amplitude.core.State
import com.amplitude.core.utilities.InMemoryStorageProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class AmplitudeStrictModeTest {
    @Test
    fun `construction_does_not_read_disk_on_main_thread`() = assertNoDiskReads(inMemoryStorage = false)

    @Test
    fun `crash_tracking_initialization_does_not_read_preferences_on_main_thread`() = assertNoDiskReads(inMemoryStorage = true)

    @Test
    fun `crash_handler_uses_persisted_decision_before_build`() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        val scope = CoroutineScope(SupervisorJob())
        val storageDispatcher = Dispatchers.IO.limitedParallelism(1)
        // Hold the build so only the handler install runs.
        val pausedDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
        }
        try {
            for (enabled in listOf(true, false)) {
                val instanceName = "startup-crash-$enabled"
                val storage = CrashStorage(application, instanceName)
                storage.consumePreviousCrash()
                CrashTrackingEnabledStore(application, instanceName).setEnabled(enabled)
                lateinit var amplitude: Amplitude
                instrumentation.runOnMainSync {
                    Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
                    amplitude = Amplitude(
                        configuration = Configuration(
                            apiKey = "strict-mode-test",
                            context = application,
                            instanceName = instanceName,
                        ),
                        state = State(),
                        amplitudeScope = scope,
                        amplitudeDispatcher = pausedDispatcher,
                        networkIODispatcher = pausedDispatcher,
                        storageIODispatcher = storageDispatcher,
                    )
                }
                // The serial storage dispatcher has run the install once this returns.
                runBlocking(storageDispatcher) {}
                assertFalse(amplitude.isBuilt.isCompleted)
                Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(
                    Thread.currentThread(), IllegalStateException("early startup crash"),
                )
                val report = storage.consumePreviousCrash()
                if (enabled) {
                    assertTrue(report?.contains("early startup crash") == true)
                } else {
                    assertNull(report)
                }
            }
        } finally {
            scope.cancel()
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    private fun assertNoDiskReads(inMemoryStorage: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as Application
        val violations = CopyOnWriteArrayList<Throwable>()
        var previous: StrictMode.ThreadPolicy? = null
        try {
            instrumentation.runOnMainSync {
                previous = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder()
                        .detectDiskReads()
                        .penaltyLog()
                        .penaltyListener({ it.run() }) { violations.add(it) }
                        .build(),
                )
                val configuration =
                    Configuration(
                        apiKey = "strict-mode-test",
                        context = application,
                        autocapture = setOf(AutocaptureOption.SESSIONS),
                    )
                if (inMemoryStorage) configuration.storageProvider = InMemoryStorageProvider()
                Amplitude(configuration)
            }
            instrumentation.waitForIdleSync()
        } finally {
            instrumentation.runOnMainSync { previous?.let { StrictMode.setThreadPolicy(it) } }
        }
        assertTrue(violations.joinToString("\n") { it.stackTraceToString() }, violations.isEmpty())
    }
}
