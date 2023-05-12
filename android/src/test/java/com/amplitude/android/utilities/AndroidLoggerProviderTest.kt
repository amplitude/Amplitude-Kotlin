package com.amplitude.android.utilities

import android.app.Application
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.core.utilities.InMemoryStorageProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class AndroidLoggerProviderTest {
    @JvmField
    @TempDir
    var tempDir: Path? = null

    @Test
    fun androidLoggerProvider_getLogger_returnsSingletonInstance() {
        val testApiKey = "test-123"
        val context = mockk<Application>(relaxed = true)
        every { context.getDir(any(), any()) } returns File(tempDir!!.absolutePathString())

        val amplitude = Amplitude(
            Configuration(
                testApiKey,
                context = context,
                identifyInterceptStorageProvider = InMemoryStorageProvider(),
            )
        )
        val loggerProvider = AndroidLoggerProvider()
        val logger1 = loggerProvider.getLogger(amplitude)
        val logger2 = loggerProvider.getLogger(amplitude)
        Assertions.assertEquals(logger1, logger2)
    }
}
