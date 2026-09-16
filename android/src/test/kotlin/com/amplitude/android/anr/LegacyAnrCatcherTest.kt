package com.amplitude.android.anr

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun `watchdog persists an ANR when the main thread does not respond`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            shadowOf(Looper.getMainLooper()).pause()
            val catcher =
                LegacyAnrCatcher(
                    context = context,
                    ioDispatcher = testDispatcher,
                )

            val deadline = System.currentTimeMillis() + 7_000
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
    fun `detach stops the watchdog if this catcher still owns it`() {
        val catcher =
            LegacyAnrCatcher(
                context = context,
                ioDispatcher = StandardTestDispatcher(),
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
            )
        LegacyAnrCatcher(
            context = context,
            ioDispatcher = StandardTestDispatcher(),
        )
        first.detach()
        assertTrue(watchdogThreads().isNotEmpty())
    }

    private fun watchdogThreads(): List<Thread> =
        Thread.getAllStackTraces().keys.filter { it.name == "amplitude-anr-watchdog" && it.isAlive }
}
