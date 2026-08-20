package com.amplitude.android.crash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CrashCatcherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @After
    fun tearDown() {
        unmockkConstructor(CrashStorage::class)
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        CrashStorage(context).consumePreviousCrash()
    }

    @Test
    fun `multiple catchers invoke the original handler once`() {
        var chainedCount = 0
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> chainedCount++ }

        CrashCatcher(context)
        val firstHandler = Thread.getDefaultUncaughtExceptionHandler()

        CrashCatcher(context)
        val latestHandler = Thread.getDefaultUncaughtExceptionHandler()

        assertNotSame(firstHandler, latestHandler)

        latestHandler!!.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        assertEquals(1, chainedCount)
        val report = CrashStorage(context).consumePreviousCrash()
        assertTrue(report!!.contains("boom"))
    }

    @Test
    fun `wrapped Amplitude handler persists a throwable only once`() {
        var saveCount = 0
        mockkConstructor(CrashStorage::class)
        every { anyConstructed<CrashStorage>().saveCrashReport(any()) } answers {
            saveCount++
            callOriginal()
        }

        Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
        CrashCatcher(context)
        val firstHandler = Thread.getDefaultUncaughtExceptionHandler()!!
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            firstHandler.uncaughtException(thread, throwable)
        }

        CrashCatcher(context)
        Thread.getDefaultUncaughtExceptionHandler()!!
            .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        assertEquals(1, saveCount)
    }
}
