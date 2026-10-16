package com.amplitude.android.crash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CrashStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `should isolate crash reports by instance name across launches`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val first = CrashStorage(context, "first", dispatcher)
            val second = CrashStorage(context, "second", dispatcher)
            first.saveCrashReport(RuntimeException("first crash"))

            assertNull(second.consumePreviousCrash())
            second.saveCrashReport(RuntimeException("second crash"))

            val firstReport = CrashStorage(context, "first", dispatcher).consumePreviousCrash()!!
            assertTrue(firstReport.contains("first crash"))
            val secondReport = CrashStorage(context, "second", dispatcher).consumePreviousCrash()!!
            assertTrue(secondReport.contains("second crash"))
            assertNull(first.consumePreviousCrash())
            assertNull(second.consumePreviousCrash())
        }

    @Test
    fun `should keep instance names with path separators in separate directories`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val first = CrashStorage(context, "first/../second", dispatcher)
            val second = CrashStorage(context, "second", dispatcher)
            assertEquals(first.directory.parentFile, second.directory.parentFile)
            first.saveCrashReport(RuntimeException("first crash"))
            second.saveCrashReport(RuntimeException("second crash"))

            assertTrue(first.consumePreviousCrash()!!.contains("first crash"))
            assertTrue(second.consumePreviousCrash()!!.contains("second crash"))
        }

    @Test
    fun `directory is created without recursing`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val storage =
                CrashStorage(
                    appContext = context,
                    instanceName = "test-instance",
                    ioDispatcher = testDispatcher,
                )
            assertTrue(storage.directory.isDirectory)
            storage.saveCrashReport(IllegalStateException("failed"))
            val report = storage.consumePreviousCrash()
            assertTrue(report!!.contains("IllegalStateException"))
            assertTrue(report.contains("failed"))
            assertNull(storage.consumePreviousCrash())
        }

    @Test
    fun `only one consumer receives the report`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val storage =
                CrashStorage(
                    appContext = context,
                    instanceName = "test-instance",
                    ioDispatcher = testDispatcher,
                )
            storage.saveCrashReport(IllegalStateException("once"))

            val first = storage.consumePreviousCrash()
            val second = storage.consumePreviousCrash()

            assertTrue(first!!.contains("once"))
            assertNull(second)
        }

    @Test
    fun `crash report bounds the number of stack frames`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val throwable =
                RuntimeException("large").apply {
                    stackTrace =
                        Array(200) { index ->
                            StackTraceElement("example.Class$index", "method", "Source.kt", index)
                        }
                }
            val storage =
                CrashStorage(
                    appContext = context,
                    instanceName = "test-instance",
                    ioDispatcher = testDispatcher,
                )
            storage.saveCrashReport(throwable)

            val report = storage.consumePreviousCrash()!!
            assertEquals(64, report.lineSequence().count { it.startsWith("\tat ") })
            assertTrue(report.contains("crash report truncated"))
        }
}
