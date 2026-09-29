package com.amplitude.android.anr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
    fun `directory is created without recursing`() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val storage =
                AnrStorage(
                    appContext = context,
                    ioDispatcher = testDispatcher,
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
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val storage =
                AnrStorage(
                    appContext = context,
                    ioDispatcher = testDispatcher,
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
                    ioDispatcher = StandardTestDispatcher(testScheduler),
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
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val storage =
                AnrStorage(
                    appContext = context,
                    ioDispatcher = testDispatcher,
                )
            File(storage.directory!!, "com.amplitude.anr_aei_timestamp").apply {
                delete()
                mkdirs()
            }

            storage.saveLastAeiTimestamp(1_234L)

            assertNull(storage.loadLastAeiTimestamp())
        }
}
