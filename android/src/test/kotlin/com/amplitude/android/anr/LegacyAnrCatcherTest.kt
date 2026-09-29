package com.amplitude.android.anr

import android.app.ActivityManager
import android.content.Context
import android.os.Looper
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.crash.CrashTrackingRemoteConfig
import com.amplitude.core.RestrictedAmplitudeFeature
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, RestrictedAmplitudeFeature::class)
@RunWith(RobolectricTestRunner::class)
class LegacyAnrCatcherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() =
        runTest {
            LegacyAnrCatcher.stopWatchdog()
            AnrStorage(
                appContext = context,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            ).consumePreviousAnr()
        }

    @After
    fun tearDown() =
        runTest {
            LegacyAnrCatcher.stopWatchdog()
            shadowOf(Looper.getMainLooper()).idle()
            AnrStorage(
                appContext = context,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            ).consumePreviousAnr()
        }

    @Test
    fun `watchdog persists an ANR only after the system marks this process not responding`() =
        runTest {
            val activityManager = mockk<ActivityManager>()
            val errorStates =
                mutableListOf(
                    errorState(
                        pid = Process.myPid() + 1,
                        condition = ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING,
                    ),
                )
            every { activityManager.getProcessesInErrorState() } answers { errorStates.toList() }
            val spiedContext = spyk(context)
            every { spiedContext.applicationContext } returns spiedContext
            every { spiedContext.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager

            val testDispatcher = StandardTestDispatcher(testScheduler)
            shadowOf(Looper.getMainLooper()).pause()
            val catcher =
                LegacyAnrCatcher(
                    context = spiedContext,
                    ioDispatcher = testDispatcher,
                    crashTrackingRemoteConfig = trackingConfig(enabled = true),
                )

            Thread.sleep(5_500)
            assertTrue(catcher.consumePreviousAnrs().isEmpty())

            errorStates +=
                errorState(
                    pid = Process.myPid(),
                    condition = ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING,
                )
            val deadline = System.currentTimeMillis() + 3_000
            var reports: List<String> = emptyList()
            while (reports.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
                reports = catcher.consumePreviousAnrs()
            }

            assertTrue(reports.isNotEmpty())
            assertTrue(reports.first().contains("ANR detected"))
            assertTrue(reports.first().contains("Timeout: 5000ms"))
        }

    @Test
    fun `system anr is false without an activity manager`() {
        val spiedContext = spyk(context)
        every { spiedContext.getSystemService(Context.ACTIVITY_SERVICE) } returns null

        assertFalse(isProcessNotResponding(spiedContext))
    }

    @Test
    fun `system anr is false when the error-state lookup fails`() {
        val activityManager = mockk<ActivityManager>()
        every { activityManager.getProcessesInErrorState() } throws RuntimeException("binder")
        val spiedContext = spyk(context)
        every { spiedContext.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager

        assertFalse(isProcessNotResponding(spiedContext))
    }

    @Test
    fun `system anr requires this process to be not responding`() {
        val activityManager = mockk<ActivityManager>()
        val spiedContext = spyk(context)
        every { spiedContext.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager

        every { activityManager.getProcessesInErrorState() } returns null
        assertFalse(isProcessNotResponding(spiedContext))

        every { activityManager.getProcessesInErrorState() } returns
            listOf(
                errorState(
                    pid = Process.myPid(),
                    condition = ActivityManager.ProcessErrorStateInfo.CRASHED,
                ),
            )
        assertFalse(isProcessNotResponding(spiedContext))

        every { activityManager.getProcessesInErrorState() } returns
            listOf(
                errorState(
                    pid = Process.myPid() + 1,
                    condition = ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING,
                ),
            )
        assertFalse(isProcessNotResponding(spiedContext))

        every { activityManager.getProcessesInErrorState() } returns
            listOf(
                errorState(
                    pid = Process.myPid(),
                    condition = ActivityManager.ProcessErrorStateInfo.NOT_RESPONDING,
                ),
            )
        assertTrue(isProcessNotResponding(spiedContext))
    }

    @Test
    fun `detach stops the watchdog if this catcher still owns it`() {
        val catcher =
            LegacyAnrCatcher(
                context = context,
                ioDispatcher = StandardTestDispatcher(),
                crashTrackingRemoteConfig = trackingConfig(enabled = true),
            )
        assertTrue(watchdogThreads().isNotEmpty())
        catcher.detach()
        assertTrue(watchdogThreads().isEmpty())
    }

    @Test
    fun `detach leaves a replacement's watchdog running`() {
        val first =
            LegacyAnrCatcher(
                context = context,
                ioDispatcher = StandardTestDispatcher(),
                crashTrackingRemoteConfig = trackingConfig(enabled = true),
            )
        LegacyAnrCatcher(
            context = context,
            ioDispatcher = StandardTestDispatcher(),
            crashTrackingRemoteConfig = trackingConfig(enabled = true),
        )
        first.detach()
        assertTrue(watchdogThreads().isNotEmpty())
    }

    @Test
    fun `does not start the watchdog when crash tracking is off`() {
        LegacyAnrCatcher(
            context = context,
            ioDispatcher = StandardTestDispatcher(),
            crashTrackingRemoteConfig = trackingConfig(enabled = false),
        )
        assertTrue(watchdogThreads().isEmpty())
    }

    private fun trackingConfig(enabled: Boolean): CrashTrackingRemoteConfig =
        mockk<CrashTrackingRemoteConfig>().also {
            every { it.isCrashTrackingEnabled } returns enabled
        }

    private fun watchdogThreads(): List<Thread> =
        Thread.getAllStackTraces().keys.filter { it.name == "amplitude-anr-watchdog" && it.isAlive }

    private fun errorState(
        pid: Int,
        condition: Int,
    ): ActivityManager.ProcessErrorStateInfo =
        ActivityManager.ProcessErrorStateInfo().apply {
            this.pid = pid
            this.condition = condition
        }
}
