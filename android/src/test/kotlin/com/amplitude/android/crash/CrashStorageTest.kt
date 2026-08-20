package com.amplitude.android.crash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CrashStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `directory is created without recursing`() {
        val storage = CrashStorage(context)
        assertTrue(storage.directory.isDirectory)
        storage.saveCrashReport(IllegalStateException("failed"))
        val report = storage.consumePreviousCrash()
        assertTrue(report!!.contains("IllegalStateException"))
        assertTrue(report.contains("failed"))
        assertNull(storage.consumePreviousCrash())
    }

    @Test
    fun `crash report bounds the number of stack frames`() {
        val throwable =
            RuntimeException("large").apply {
                stackTrace =
                    Array(200) { index ->
                        StackTraceElement("example.Class$index", "method", "Source.kt", index)
                    }
            }
        val storage = CrashStorage(context)
        storage.saveCrashReport(throwable)

        val report = storage.consumePreviousCrash()!!
        assertEquals(64, report.lineSequence().count { it.startsWith("\tat ") })
        assertTrue(report.contains("crash report truncated"))
    }
}
