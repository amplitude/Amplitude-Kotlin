package com.amplitude.android.anr

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.crash.CrashTrackingRemoteConfig
import com.amplitude.core.RestrictedAmplitudeFeature
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
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, RestrictedAmplitudeFeature::class)
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
                instanceName = "test-instance",
                ioDispatcher = testDispatcher,
            ).saveLastAeiTimestamp(0L)
            AnrStorage(
                appContext = context,
                instanceName = "test-instance",
                ioDispatcher = testDispatcher,
            ).consumePreviousAnr()
        }

    @Test
    fun `should preserve collection start when the first exit history lookup fails`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val instanceName = "failed-initial-lookup"
            val storage = AnrStorage(context, instanceName, dispatcher)
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } throws IllegalStateException("binder unavailable")

            assertTrue(
                AndroidRAnrCatcher(context, instanceName, dispatcher, trackingConfig(enabled = true))
                    .consumePreviousAnrs().isEmpty(),
            )
            val collectionStart = assertNotNull(storage.loadLastAeiTimestamp())
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } returns
                listOf(
                    mockExit(collectionStart - 1, "before collection", "historical trace"),
                    mockExit(collectionStart + 1, "after collection", "new trace"),
                )

            // A later launch must reuse the original baseline instead of skipping the new ANR.
            val nextLaunch = AndroidRAnrCatcher(context, instanceName, dispatcher, trackingConfig(enabled = true))
            val report = nextLaunch.consumePreviousAnrs().single()
            assertTrue(report.contains("after collection"))
            assertTrue(report.contains("new trace"))
            assertEquals(collectionStart + 1, storage.loadLastAeiTimestamp())
            assertTrue(nextLaunch.consumePreviousAnrs().isEmpty())
        }

    @Test
    fun `should retain an existing cursor when exit history lookup fails`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val storage = AnrStorage(context, "test-instance", dispatcher)
            storage.saveLastAeiTimestamp(1_234L)
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } throws IllegalStateException("binder unavailable")

            assertTrue(
                AndroidRAnrCatcher(context, "test-instance", dispatcher, trackingConfig(enabled = true))
                    .consumePreviousAnrs().isEmpty(),
            )
            assertEquals(1_234L, storage.loadLastAeiTimestamp())
        }

    @Test
    fun `should persist collection start before a cancelled first history lookup`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val instanceName = "cancelled-initial-lookup"
            val storage = AnrStorage(context, instanceName, dispatcher)
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } throws CancellationException()

            assertFailsWith<CancellationException> {
                AndroidRAnrCatcher(context, instanceName, dispatcher, trackingConfig(enabled = true))
                    .consumePreviousAnrs()
            }
            val collectionStart = assertNotNull(storage.loadLastAeiTimestamp())
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } returns listOf(mockExit(collectionStart + 1, "after cancellation", "new trace"))

            val reports =
                AndroidRAnrCatcher(context, instanceName, dispatcher, trackingConfig(enabled = true))
                    .consumePreviousAnrs()
            assertTrue(reports.single().contains("after cancellation"))
        }

    @Test
    fun `should consume exit history independently for each instance name`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            AnrStorage(context, "first", dispatcher).saveLastAeiTimestamp(1_000L)
            AnrStorage(context, "second", dispatcher).saveLastAeiTimestamp(2_000L)
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } returns
                listOf(
                    mockExit(timestamp = 1_500L, description = "earlier anr", trace = "earlier trace"),
                    mockExit(timestamp = 2_500L, description = "later anr", trace = "later trace"),
                )

            val first = createAnrCatcher(context, "first", dispatcher, trackingConfig(enabled = true))
            val second = createAnrCatcher(context, "second", dispatcher, trackingConfig(enabled = true))
            val firstReports = first.consumePreviousAnrs()
            assertEquals(2, firstReports.size)
            assertTrue(firstReports[0].contains("earlier anr"))
            assertTrue(firstReports[1].contains("later anr"))
            val secondReports = second.consumePreviousAnrs()
            assertEquals(1, secondReports.size)
            assertTrue(secondReports.single().contains("later anr"))

            assertTrue(
                createAnrCatcher(context, "first", dispatcher, trackingConfig(enabled = true))
                    .consumePreviousAnrs().isEmpty(),
            )
            assertTrue(second.consumePreviousAnrs().isEmpty())
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
                    instanceName = "test-instance",
                    ioDispatcher = testDispatcher,
                    crashTrackingRemoteConfig = trackingConfig(enabled = true),
                ).consumePreviousAnrs()
            assertEquals(2, reports.size)
            assertTrue(reports[0].contains("first anr"))
            assertTrue(reports[0].contains("trace-one"))
            assertTrue(reports[0].contains("Source: ApplicationExitInfo"))
            assertTrue(reports[1].contains("second anr"))

            val again =
                AndroidRAnrCatcher(
                    context = context,
                    instanceName = "test-instance",
                    ioDispatcher = testDispatcher,
                    crashTrackingRemoteConfig = trackingConfig(enabled = true),
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
                    instanceName = "test-instance",
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                    crashTrackingRemoteConfig = trackingConfig(enabled = true),
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
                            instanceName = "test-instance",
                            ioDispatcher = testDispatcher,
                            crashTrackingRemoteConfig = trackingConfig(enabled = true),
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
                    instanceName = "test-instance",
                    ioDispatcher = testDispatcher,
                    crashTrackingRemoteConfig = trackingConfig(enabled = true),
                ).consumePreviousAnrs(),
            )
        }

    @Test
    fun `first consume skips historical ANRs and reports only later ones`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val storage =
                AnrStorage(
                    appContext = context,
                    instanceName = "test-instance",
                    ioDispatcher = testDispatcher,
                )
            File(storage.directory!!, "com.amplitude.anr_aei_timestamp").delete()
            val historical = mockExit(timestamp = 2_000L, description = "historical anr", trace = "trace")
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } returns listOf(historical)

            val catcher =
                AndroidRAnrCatcher(
                    context = context,
                    instanceName = "test-instance",
                    ioDispatcher = testDispatcher,
                    crashTrackingRemoteConfig = trackingConfig(enabled = true),
                )
            assertEquals(emptyList(), catcher.consumePreviousAnrs())

            val cursor = storage.loadLastAeiTimestamp()
            assertTrue(cursor != null && cursor >= 2_000L)

            val later =
                mockExit(
                    timestamp = cursor!! + 1,
                    description = "later anr",
                    trace = "later-trace",
                )
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } returns listOf(historical, later)
            val reports = catcher.consumePreviousAnrs()
            assertEquals(1, reports.size)
            assertTrue(reports.first().contains("later anr"))
            assertTrue(reports.first().contains("later-trace"))
        }

    @Test
    fun `first consume records a cursor when exit history is empty`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val storage =
                AnrStorage(
                    appContext = context,
                    instanceName = "test-instance",
                    ioDispatcher = testDispatcher,
                )
            File(storage.directory!!, "com.amplitude.anr_aei_timestamp").delete()
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } returns emptyList()

            assertEquals(
                emptyList(),
                AndroidRAnrCatcher(
                    context = context,
                    instanceName = "test-instance",
                    ioDispatcher = testDispatcher,
                    crashTrackingRemoteConfig = trackingConfig(enabled = true),
                ).consumePreviousAnrs(),
            )
            val cursor = storage.loadLastAeiTimestamp()
            assertTrue(cursor != null && cursor > 0L)
        }

    @Test
    fun `disabled consume does not baseline a missing cursor`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val storage =
                AnrStorage(
                    appContext = context,
                    instanceName = "test-instance",
                    ioDispatcher = testDispatcher,
                )
            File(storage.directory!!, "com.amplitude.anr_aei_timestamp").delete()
            every {
                activityManager.getHistoricalProcessExitReasons(any(), 0, 0)
            } returns listOf(mockExit(timestamp = 2_000L, description = "historical anr", trace = "trace"))

            assertEquals(
                emptyList(),
                AndroidRAnrCatcher(
                    context = context,
                    instanceName = "test-instance",
                    ioDispatcher = testDispatcher,
                    crashTrackingRemoteConfig = trackingConfig(enabled = false),
                ).consumePreviousAnrs(),
            )
            assertNull(storage.loadLastAeiTimestamp())
        }

    @Test
    fun `reports nothing when crash tracking is off`() =
        runTest {
            assertEquals(
                emptyList(),
                AndroidRAnrCatcher(
                    context = context,
                    instanceName = "test-instance",
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                    crashTrackingRemoteConfig = trackingConfig(enabled = false),
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

@OptIn(ExperimentalCoroutinesApi::class, RestrictedAmplitudeFeature::class)
@RunWith(RobolectricTestRunner::class)
class AnrCatcherFactoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() =
        runTest {
            LegacyAnrCatcher.stopWatchdog()
            AnrStorage(
                appContext = context,
                instanceName = "test-instance",
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
                    instanceName = "test-instance",
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                    crashTrackingRemoteConfig = trackingConfig(enabled = false),
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
                    instanceName = "test-instance",
                    ioDispatcher = StandardTestDispatcher(testScheduler),
                    crashTrackingRemoteConfig = trackingConfig(enabled = false),
                ),
            )
        }
}

@OptIn(RestrictedAmplitudeFeature::class)
private fun trackingConfig(enabled: Boolean): CrashTrackingRemoteConfig =
    mockk<CrashTrackingRemoteConfig>().also {
        every { it.isCrashTrackingEnabled } returns enabled
    }
