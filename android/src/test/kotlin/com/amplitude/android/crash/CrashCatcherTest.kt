package com.amplitude.android.crash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.core.RestrictedAmplitudeFeature
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
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
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
            CrashTrackingEnabledStore(context).setEnabled(false)
            CrashStorage(
                appContext = context,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            ).consumePreviousCrash()
        }

    @Test
    fun `does not install the handler when crash tracking is off at startup`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val before = Thread.getDefaultUncaughtExceptionHandler()
            createCrashCatcher(testDispatcher, crashTrackingRemoteConfig(false))
            assertSame(before, Thread.getDefaultUncaughtExceptionHandler())
        }

    @Test
    fun `installs the handler during init when crash tracking is on at startup`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val before = Thread.getDefaultUncaughtExceptionHandler()
            createCrashCatcher(testDispatcher)
            assertNotSame(before, Thread.getDefaultUncaughtExceptionHandler())
        }

    @Test
    fun `persists a startup crash before remote config arrives when the previous run enabled tracking`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val store = CrashTrackingEnabledStore(context)
            store.setEnabled(true)
            val remoteConfig =
                CrashTrackingRemoteConfig(
                    remoteConfigClient = mockk(relaxed = true),
                    sdkVersion = "1.8.0",
                    crashTrackingEnabledStore = store,
                )
            createCrashCatcher(testDispatcher, remoteConfig)

            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("startup"))

            val report =
                CrashStorage(
                    appContext = context,
                    ioDispatcher = testDispatcher,
                ).consumePreviousCrash()
            assertTrue(report!!.contains("startup"))
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
    fun `concurrent catchers each wrap the previous handler`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            var chainedCount = 0
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> chainedCount++ }

            val gateReads = AtomicInteger(0)
            val start = CyclicBarrier(2)
            val workers =
                List(2) {
                    thread {
                        val remoteConfig = mockk<CrashTrackingRemoteConfig>()
                        every { remoteConfig.isCrashTrackingEnabled } answers {
                            gateReads.incrementAndGet()
                            true
                        }
                        start.await()
                        createCrashCatcher(testDispatcher, remoteConfig)
                    }
                }
            workers.forEach { it.join() }
            gateReads.set(0)

            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertEquals(1, chainedCount)
            assertEquals(2, gateReads.get())
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
            val report =
                createCrashCatcher(testDispatcher, crashTrackingRemoteConfig(false))
                    .consumePreviousCrash()
            assertTrue(report!!.contains("previous"))
            assertNull(
                CrashStorage(
                    appContext = context,
                    ioDispatcher = testDispatcher,
                ).consumePreviousCrash(),
            )
        }

    @Test
    fun `does not persist when remote config turns crash tracking off after startup`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            createCrashCatcher(testDispatcher, crashTrackingRemoteConfig(true, false))
            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertNull(
                CrashStorage(
                    appContext = context,
                    ioDispatcher = testDispatcher,
                ).consumePreviousCrash(),
            )
        }

    @Test
    fun `does not persist after detach`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            createCrashCatcher(testDispatcher).detach()
            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertNull(
                CrashStorage(
                    appContext = context,
                    ioDispatcher = testDispatcher,
                ).consumePreviousCrash(),
            )
        }

    private fun crashTrackingRemoteConfig(vararg enabled: Boolean): CrashTrackingRemoteConfig =
        mockk<CrashTrackingRemoteConfig>().also {
            every { it.isCrashTrackingEnabled } returnsMany enabled.toList()
        }

    private fun createCrashCatcher(
        dispatcher: CoroutineDispatcher,
        crashTrackingRemoteConfig: CrashTrackingRemoteConfig = crashTrackingRemoteConfig(true),
    ): CrashCatcher =
        CrashCatcher(
            context = context,
            ioDispatcher = dispatcher,
            crashTrackingRemoteConfig = crashTrackingRemoteConfig,
        )
}
