package com.amplitude.android.anr

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class AndroidRAnrCatcherTest {
    private val context = spyk(ApplicationProvider.getApplicationContext<Context>())
    private val activityManager = mockk<ActivityManager>()

    @Before
    fun setUp() =
        runTest {
            every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
            val testDispatcher = StandardTestDispatcher(testScheduler)
            AnrStorage(
                appContext = context,
                ioDispatcher = testDispatcher,
            ).saveLastAeiTimestamp(0L)
            AnrStorage(
                appContext = context,
                ioDispatcher = testDispatcher,
            ).consumePreviousAnr()
        }

    @Test
    fun `reports unread ANR exits and ignores them on the next consume`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val first = mockExit(timestamp = 1_000L, description = "first anr", trace = "trace-one")
            val second = mockExit(timestamp = 2_000L, description = "second anr", trace = "trace-two")
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } returns listOf(second, first)

            val reports =
                AndroidRAnrCatcher(
                    context = context,
                    ioDispatcher = testDispatcher,
                ).consumePreviousAnrs()
            assertEquals(2, reports.size)
            assertTrue(reports[0].contains("first anr"))
            assertTrue(reports[0].contains("trace-one"))
            assertTrue(reports[0].contains("Source: ApplicationExitInfo"))
            assertTrue(reports[1].contains("second anr"))

            val again =
                AndroidRAnrCatcher(
                    context = context,
                    ioDispatcher = testDispatcher,
                ).consumePreviousAnrs()
            assertEquals(emptyList(), again)
        }

    @Test
    fun `cancellation from historical exits is not swallowed`() =
        runTest {
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } throws CancellationException()

            assertFailsWith<CancellationException> {
                AndroidRAnrCatcher(
                    context = context,
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                ).consumePreviousAnrs()
            }
        }

    @Test
    fun `concurrent catchers claim each ANR once`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val first = mockExit(timestamp = 1_000L, description = "first anr", trace = "trace-one")
            val second = mockExit(timestamp = 2_000L, description = "second anr", trace = "trace-two")
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } returns listOf(second, first)

            val reports =
                List(2) {
                    async {
                        AndroidRAnrCatcher(
                            context = context,
                            ioDispatcher = testDispatcher,
                        ).consumePreviousAnrs()
                    }
                }.awaitAll().flatten()

            assertEquals(2, reports.size)
            assertTrue(reports.any { it.contains("first anr") })
            assertTrue(reports.any { it.contains("second anr") })
        }

    @Test
    fun `skips non-ANR exits`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val crash =
                mockk<ApplicationExitInfo> {
                    every { reason } returns ApplicationExitInfo.REASON_CRASH
                    every { timestamp } returns 3_000L
                }
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } returns listOf(crash)

            assertEquals(
                emptyList(),
                AndroidRAnrCatcher(
                    context = context,
                    ioDispatcher = testDispatcher,
                ).consumePreviousAnrs(),
            )
        }

    private fun mockExit(
        timestamp: Long,
        description: String,
        trace: String,
    ): ApplicationExitInfo {
        return mockk {
            every { reason } returns ApplicationExitInfo.REASON_ANR
            every { this@mockk.timestamp } returns timestamp
            every { pid } returns 42
            every { this@mockk.description } returns description
            every { traceInputStream } returns trace.byteInputStream()
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnrCatcherFactoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() =
        runTest {
            LegacyAnrCatcher.stopWatchdog()
            AnrStorage(
                appContext = context,
                ioDispatcher = StandardTestDispatcher(testScheduler),
            ).consumePreviousAnr()
        }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `uses legacy watchdog below Android 11`() =
        runTest {
            assertIs<LegacyAnrCatcher>(
                createAnrCatcher(
                    context = context,
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                ),
            )
        }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `uses AndroidRAnrCatcher on Android 11 and above`() =
        runTest {
            assertIs<AndroidRAnrCatcher>(
                createAnrCatcher(
                    context = context,
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                ),
            )
        }
}
