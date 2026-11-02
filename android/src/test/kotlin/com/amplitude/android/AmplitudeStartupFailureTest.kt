package com.amplitude.android

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.crash.CrashStorage
import com.amplitude.android.crash.CrashTrackingEnabledStore
import com.amplitude.core.State
import com.amplitude.core.diagnostics.DiagnosticsContextProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AmplitudeStartupFailureTest {
    @Test
    fun `failed deferred diagnostics initialization retires the instance and stops crash capture`() = runTest {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val name = "failed-diagnostics-initialization"
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val pausedNetwork = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
        }
        CrashTrackingEnabledStore(application, name).setEnabled(true)
        CrashStorage(application, name).consumePreviousCrash()
        val amplitude = object : Amplitude(
            configuration = Configuration(
                apiKey = "test-api-key",
                context = application,
                instanceName = name,
                storageProvider = InMemoryStorageProvider(),
                identifyInterceptStorageProvider = InMemoryStorageProvider(),
                identityStorageProvider = IMIdentityStorageProvider(),
            ),
            state = State(),
            amplitudeScope = scope,
            amplitudeDispatcher = dispatcher,
            storageIODispatcher = dispatcher,
            networkIODispatcher = pausedNetwork,
        ) {
            override fun diagnosticsContextProvider(): DiagnosticsContextProvider =
                throw IllegalStateException("diagnostics initialization failed")
        }
        try {
            runCurrent()
            assertIs<IllegalStateException>(runCatching { amplitude.isBuilt.await() }.exceptionOrNull())
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(
                Thread.currentThread(), IllegalArgumentException("crash after failed build"),
            )
            val report = CrashStorage(application, name).consumePreviousCrash()
            assertNull(report, "A failed initialization must detach crash capture")
            assertFalse(amplitude.isActive, "A failed initialization must retire its registry owner")
        } finally {
            amplitude.markRetired()
            amplitude.detachWatchers()
            scope.cancel()
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        }
    }
}
