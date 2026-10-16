package com.amplitude.android.crash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.core.RestrictedAmplitudeFeature
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
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
            CrashTrackingEnabledStore(context, "test-instance").setEnabled(false)
            CrashStorage(
                appContext = context,
                instanceName = "test-instance",
            ).consumePreviousCrash()
        }

    @Test
    fun `should persist the same crash for each enabled instance`() =
        runTest {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val first = createCrashCatcher(instanceName = "first")
            val second = createCrashCatcher(instanceName = "second")

            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("shared crash"))

            assertTrue(first.consumePreviousCrash()!!.contains("shared crash"))
            assertTrue(second.consumePreviousCrash()!!.contains("shared crash"))
            assertNull(first.consumePreviousCrash())
            assertNull(second.consumePreviousCrash())
        }

    @Test
    fun `should not let a disabled instance consume another instance's crash`() =
        runTest {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val enabled = createCrashCatcher(instanceName = "enabled")
            val disabled = createCrashCatcher(crashTrackingRemoteConfig(false), "disabled")

            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("enabled crash"))

            assertNull(disabled.consumePreviousCrash())
            assertTrue(enabled.consumePreviousCrash()!!.contains("enabled crash"))
        }

    @Test
    fun `does not install the handler when crash tracking is off at startup`() =
        runTest {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val before = Thread.getDefaultUncaughtExceptionHandler()
            createCrashCatcher(crashTrackingRemoteConfig(false))
            assertSame(before, Thread.getDefaultUncaughtExceptionHandler())
        }

    @Test
    fun `installs the handler during init when crash tracking is on at startup`() =
        runTest {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val before = Thread.getDefaultUncaughtExceptionHandler()
            createCrashCatcher()
            assertNotSame(before, Thread.getDefaultUncaughtExceptionHandler())
        }

    @Test
    fun `persists a startup crash before remote config arrives when the previous run enabled tracking`() =
        runTest {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val store = CrashTrackingEnabledStore(context, "test-instance")
            store.setEnabled(true)
            val remoteConfig =
                CrashTrackingRemoteConfig(
                    remoteConfigClient = mockk(relaxed = true),
                    sdkVersion = "1.8.0",
                    crashTrackingEnabledStore = store,
                )
            createCrashCatcher(remoteConfig)

            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("startup"))

            val report =
                CrashStorage(
                    appContext = context,
                    instanceName = "test-instance",
                ).consumePreviousCrash()
            assertTrue(report!!.contains("startup"))
        }

    @Test
    fun `multiple catchers invoke the original handler once`() =
        runTest {
            var chainedCount = 0
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> chainedCount++ }

            createCrashCatcher()
            val firstHandler = Thread.getDefaultUncaughtExceptionHandler()

            createCrashCatcher()
            val latestHandler = Thread.getDefaultUncaughtExceptionHandler()

            assertNotSame(firstHandler, latestHandler)

            latestHandler!!.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertEquals(1, chainedCount)
            val report =
                CrashStorage(
                    appContext = context,
                    instanceName = "test-instance",
                ).consumePreviousCrash()
            assertTrue(report!!.contains("boom"))
        }

    @Test
    fun `concurrent catchers each wrap the previous handler`() =
        runTest {
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
                        createCrashCatcher(remoteConfig)
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
            var saveCount = 0
            mockkConstructor(CrashStorage::class)
            every { anyConstructed<CrashStorage>().saveCrashReport(any()) } answers {
                saveCount++
                callOriginal()
            }

            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            createCrashCatcher()
            val firstHandler = Thread.getDefaultUncaughtExceptionHandler()!!
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                firstHandler.uncaughtException(thread, throwable)
            }

            createCrashCatcher()
            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertEquals(1, saveCount)
        }

    @Test
    fun `should retry a failed crash write in the next same-name handler`() =
        runTest {
            var saveCount = 0
            var chainedCount = 0
            mockkConstructor(CrashStorage::class)
            every { anyConstructed<CrashStorage>().saveCrashReport(any()) } answers {
                saveCount++
                if (saveCount == 1) throw IOException("first write failed")
                callOriginal()
            }

            Thread.setDefaultUncaughtExceptionHandler { _, _ -> chainedCount++ }
            val catcher = createCrashCatcher()
            repeat(2) { createCrashCatcher() }

            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("retry crash"))

            assertEquals(2, saveCount)
            assertEquals(1, chainedCount)
            assertTrue(catcher.consumePreviousCrash()!!.contains("retry crash"))
            assertNull(catcher.consumePreviousCrash())
        }

    @Test
    fun `consumePreviousCrash returns the persisted report`() =
        runTest {
            CrashStorage(
                appContext = context,
                instanceName = "test-instance",
            ).saveCrashReport(RuntimeException("previous"))
            val report =
                createCrashCatcher(crashTrackingRemoteConfig(false))
                    .consumePreviousCrash()
            assertTrue(report!!.contains("previous"))
            assertNull(
                CrashStorage(
                    appContext = context,
                    instanceName = "test-instance",
                ).consumePreviousCrash(),
            )
        }

    @Test
    fun `does not persist when remote config turns crash tracking off after startup`() =
        runTest {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            createCrashCatcher(crashTrackingRemoteConfig(true, false))
            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertNull(
                CrashStorage(
                    appContext = context,
                    instanceName = "test-instance",
                ).consumePreviousCrash(),
            )
        }

    @Test
    fun `does not persist after detach`() =
        runTest {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val remoteConfig = crashTrackingRemoteConfig(true)
            createCrashCatcher(remoteConfig).detach()
            verify(exactly = 1) { remoteConfig.detach() }
            Thread.getDefaultUncaughtExceptionHandler()!!
                .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

            assertNull(
                CrashStorage(
                    appContext = context,
                    instanceName = "test-instance",
                ).consumePreviousCrash(),
            )
        }

    private fun crashTrackingRemoteConfig(vararg enabled: Boolean): CrashTrackingRemoteConfig =
        mockk<CrashTrackingRemoteConfig>(relaxUnitFun = true).also {
            every { it.isCrashTrackingEnabled } returnsMany enabled.toList()
        }

    private fun createCrashCatcher(
        crashTrackingRemoteConfig: CrashTrackingRemoteConfig = crashTrackingRemoteConfig(true),
        instanceName: String = "test-instance",
    ): CrashCatcher =
        CrashCatcher(
            context = context,
            instanceName = instanceName,
            crashTrackingRemoteConfig = crashTrackingRemoteConfig,
        )
}
