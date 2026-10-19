package com.amplitude.android.anr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnrStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `should isolate ANR reports by instance name across launches`() =
        runTest {
            val first = AnrStorage(context, "first")
            val second = AnrStorage(context, "second")
            first.saveAnrReport(1_000)

            assertNull(second.consumePreviousAnr())
            second.saveAnrReport(2_000)

            val firstReport = AnrStorage(context, "first").consumePreviousAnr()!!
            assertTrue(firstReport.contains("Timeout: 1000ms"))
            val secondReport = AnrStorage(context, "second").consumePreviousAnr()!!
            assertTrue(secondReport.contains("Timeout: 2000ms"))
            assertNull(first.consumePreviousAnr())
            assertNull(second.consumePreviousAnr())
        }

    @Test
    fun `should isolate exit history cursors by instance name across launches`() =
        runTest {
            val first = AnrStorage(context, "first")
            val second = AnrStorage(context, "second")
            first.saveLastAeiTimestamp(1_000L)

            assertNull(second.loadLastAeiTimestamp())
            second.saveLastAeiTimestamp(2_000L)

            assertEquals(1_000L, AnrStorage(context, "first").loadLastAeiTimestamp())
            assertEquals(2_000L, AnrStorage(context, "second").loadLastAeiTimestamp())
        }

    @Test
    fun `should keep instance names with path separators in separate directories`() =
        runTest {
            val first = AnrStorage(context, "first/../second")
            val second = AnrStorage(context, "second")
            assertEquals(first.directory!!.parentFile, second.directory!!.parentFile)
            first.saveAnrReport(1_000)
            second.saveAnrReport(2_000)

            assertTrue(first.consumePreviousAnr()!!.contains("Timeout: 1000ms"))
            assertTrue(second.consumePreviousAnr()!!.contains("Timeout: 2000ms"))
        }

    @Test
    fun `directory is created without recursing`() =
        runTest {
            val storage =
                AnrStorage(
                    appContext = context,
                    instanceName = "test-instance",
                )
            assertTrue(storage.directory!!.isDirectory)
            storage.saveAnrReport(5_000)
            val report = storage.consumePreviousAnr()
            assertTrue(report!!.contains("ANR detected"))
            assertTrue(report.contains("Timeout: 5000ms"))
            assertNull(storage.consumePreviousAnr())
        }

    @Test
    fun `anr report includes the main thread`() =
        runTest {
            val storage =
                AnrStorage(
                    appContext = context,
                    instanceName = "test-instance",
                )
            storage.saveAnrReport(5_000)
            val report = storage.consumePreviousAnr()!!
            assertTrue(report.contains("Thread: "))
            assertTrue(report.contains("\tat "))
        }

    @Test
    fun `storage is skipped when directory creation fails`() =
        runTest {
            val failingContext = mockk<Context>()
            every { failingContext.getDir(any(), any()) } throws SecurityException("denied")
            val storage =
                AnrStorage(
                    appContext = failingContext,
                    instanceName = "test-instance",
                )

            assertNull(storage.directory)
            storage.saveAnrReport(5_000)
            storage.saveLastAeiTimestamp(1_234L)
            assertNull(storage.consumePreviousAnr())
            assertNull(storage.loadLastAeiTimestamp())
        }

    @Test
    fun `a failed timestamp write does not throw`() =
        runTest {
            val storage =
                AnrStorage(
                    appContext = context,
                    instanceName = "test-instance",
                )
            File(storage.directory!!, "com.amplitude.anr_aei_timestamp").apply {
                delete()
                mkdirs()
            }

            storage.saveLastAeiTimestamp(1_234L)

            assertNull(storage.loadLastAeiTimestamp())
        }
}
