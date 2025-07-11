package com.amplitude.android.utilities

import android.app.Application
import com.amplitude.MainDispatcherRule
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.TempDirectory
import java.io.File
import kotlin.io.path.absolutePathString
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AndroidLoggerProviderTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var tempDir: TempDirectory

    @Before
    fun setUp() {
        // Create a temporary directory for the test
        tempDir = TempDirectory()
    }

    @After
    fun tearDown() {
        // Clean up the temporary directory after the test
        tempDir.destroy()
    }

    @Test
    fun androidLoggerProvider_getLogger_returnsSingletonInstance() {
        val testApiKey = "test-123"
        val context = mockk<Application>(relaxed = true)
        every { context.getDir(any(), any()) } returns File(tempDir.create("testDir").absolutePathString())

        val amplitude =
            Amplitude(
                Configuration(
                    testApiKey,
                    context = context,
                    identifyInterceptStorageProvider = InMemoryStorageProvider(),
                    identityStorageProvider = IMIdentityStorageProvider(),
                ),
            )
        val loggerProvider = AndroidLoggerProvider()
        val logger1 = loggerProvider.getLogger(amplitude)
        val logger2 = loggerProvider.getLogger(amplitude)

        assertEquals(logger1, logger2)
    }
}
