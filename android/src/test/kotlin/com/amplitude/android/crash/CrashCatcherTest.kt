package com.amplitude.android.crash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, RestrictedAmplitudeFeature::class)
@RunWith(RobolectricTestRunner::class)
class CrashCatcherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After
    fun tearDown() =
        runTest {
            unmockkConstructor(CrashStorage::class)
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            CrashStorage(
                appContext = context,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            ).consumePreviousCrash()
        }

    @Test
    fun `multiple catchers invoke the original handler once`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            var chainedCount = 0
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> chainedCount++ }

            createCrashCatcher(testDispatcher)
            val firstHandler = Thread.getDefaultUncaughtExceptionHandler()

            createCrashCatcher(testDispatcher)
            val latestHandler = Thread.getDefaultUncaughtExceptionHandler()

            assertNotSame(firstHandler, latestHandler)

            latestHandler!!.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertEquals(1, chainedCount)
            val report =
                CrashStorage(
                    appContext = context,
                    ioDispatcher = testDispatcher,
                ).consumePreviousCrash()
            assertTrue(report!!.contains("boom"))
        }

    @Test
    fun `wrapped Amplitude handler persists a throwable only once`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            var saveCount = 0
            mockkConstructor(CrashStorage::class)
            every { anyConstructed<CrashStorage>().saveCrashReport(any()) } answers {
                saveCount++
                callOriginal()
            }

            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            createCrashCatcher(testDispatcher)
            val firstHandler = Thread.getDefaultUncaughtExceptionHandler()!!
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                firstHandler.uncaughtException(thread, throwable)
            }

            createCrashCatcher(testDispatcher)
            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertEquals(1, saveCount)
        }

    @Test
    fun `consumePreviousCrash returns the persisted report`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            CrashStorage(
                appContext = context,
                ioDispatcher = testDispatcher,
            ).saveCrashReport(RuntimeException("previous"))
            val report = createCrashCatcher(testDispatcher).consumePreviousCrash()
            assertTrue(report!!.contains("previous"))
            assertNull(
                CrashStorage(
                    appContext = context,
                    ioDispatcher = testDispatcher,
                ).consumePreviousCrash(),
            )
        }

    private fun createCrashCatcher(
        dispatcher: CoroutineDispatcher,
        shouldTrack: Boolean = true,
    ): CrashCatcher {
        val diagnosticsClient = mockk<DiagnosticsClient>(relaxed = true)
        every { diagnosticsClient.shouldTrack } returns shouldTrack
        return CrashCatcher(
            context = context,
            ioDispatcher = dispatcher,
            diagnosticsClientLazy = lazy { diagnosticsClient },
        )
    }
}
