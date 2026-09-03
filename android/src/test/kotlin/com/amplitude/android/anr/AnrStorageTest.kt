package com.amplitude.android.anr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
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
            assertTrue(storage.directory.isDirectory)
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
}
