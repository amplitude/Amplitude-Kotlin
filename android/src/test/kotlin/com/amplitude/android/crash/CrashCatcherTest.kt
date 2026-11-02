package com.amplitude.android.crash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.RestrictedAmplitudeFeature
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
    fun `construction neither installs the handler nor reads the persisted decision`() =
        runTest {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val before = Thread.getDefaultUncaughtExceptionHandler()
            val config = crashTrackingRemoteConfig(true)
            CrashCatcher(context = context, instanceName = "test-instance", crashTrackingRemoteConfig = config)
            assertSame(before, Thread.getDefaultUncaughtExceptionHandler())
            verify(exactly = 0) { config.isCrashTrackingEnabled }
        }

    @Test
    fun `install chains the handler when crash tracking is on at startup`() =
        runTest {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val before = Thread.getDefaultUncaughtExceptionHandler()
            createCrashCatcher()
            assertNotSame(before, Thread.getDefaultUncaughtExceptionHandler())
        }

    @Test
    fun `install leaves the handler alone when crash tracking is off at startup`() =
        runTest {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val before = Thread.getDefaultUncaughtExceptionHandler()
            createCrashCatcher(crashTrackingRemoteConfig(false))
            assertSame(before, Thread.getDefaultUncaughtExceptionHandler())
        }

    @Test
    fun `install after detach leaves the handler alone`() =
        runTest {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val before = Thread.getDefaultUncaughtExceptionHandler()
            val catcher =
                CrashCatcher(
                    context = context,
                    instanceName = "test-instance",
                    crashTrackingRemoteConfig = crashTrackingRemoteConfig(true),
                )
            catcher.detach()
            catcher.install()
            assertSame(before, Thread.getDefaultUncaughtExceptionHandler())
        }

    @Test
    fun `persists a startup crash before remote config arrives when the previous run enabled tracking`() =
        runTest {
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
            val store = CrashTrackingEnabledStore(context, "test-instance")
            store.setEnabled(true)
            val remoteConfig =
                CrashTrackingRemoteConfig(
                    sdkVersion = "1.8.0",
                    crashTrackingEnabledStore = store,
                ).also { it.connect(mockk(relaxed = true)) }
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
    fun `remote enablement only captures crashes on the next launch when startup tracking was disabled`() = runTest {
        var forwarded = 0
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> forwarded++ }
        val store = CrashTrackingEnabledStore(context, "test-instance")
        store.setEnabled(false)
        val config = CrashTrackingRemoteConfig("1.8.0", store)
        val catcher = createCrashCatcher(config)
        val callback = slot<RemoteConfigClient.RemoteConfigCallback>()
        val client = mockk<RemoteConfigClient>()
        every { client.subscribe(any(), any(), capture(callback)) } returns Unit
        config.connect(client)
        callback.captured.onUpdate(
            mapOf(
                "enabled" to true,
                "sampleRate" to 1.0,
                "availabilities" to mapOf("CrashTracking" to "1.0.0"),
            ),
            RemoteConfigClient.Source.REMOTE,
            1L,
        )
        assertTrue(store.isEnabled())
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(
            Thread.currentThread(), RuntimeException("current launch"),
        )
        assertNull(catcher.consumePreviousCrash())
        assertEquals(1, forwarded)

        val nextConfig = CrashTrackingRemoteConfig("1.8.0", store)
        val nextCatcher = createCrashCatcher(nextConfig)
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(
            Thread.currentThread(), RuntimeException("next launch"),
        )
        assertTrue(nextCatcher.consumePreviousCrash()!!.contains("next launch"))
        assertEquals(2, forwarded)
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
                        runBlocking { createCrashCatcher(remoteConfig) }
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
            val config = crashTrackingRemoteConfig(true)
            createCrashCatcher(config)
            every { config.isCrashTrackingEnabled } returns false
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

    private suspend fun createCrashCatcher(
        crashTrackingRemoteConfig: CrashTrackingRemoteConfig = crashTrackingRemoteConfig(true),
        instanceName: String = "test-instance",
    ): CrashCatcher =
        CrashCatcher(
            context = context,
            instanceName = instanceName,
            crashTrackingRemoteConfig = crashTrackingRemoteConfig,
        ).also { it.install() }
}
