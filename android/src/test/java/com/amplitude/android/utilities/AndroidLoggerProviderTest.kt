package com.amplitude.android.utilities

import android.app.Application
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AndroidLoggerProviderTest {
    @Test
    fun androidLoggerProvider_getLogger_returnsSingletonInstance() {
        val testApiKey = "test-123"
        val context = mockk<Application>(relaxed = true)

        val amplitude = Amplitude(Configuration(testApiKey, context = context!!))
        val loggerProvider = AndroidLoggerProvider()
        val logger1 = loggerProvider.getLogger(amplitude)
        val logger2 = loggerProvider.getLogger(amplitude)
        Assertions.assertEquals(logger1, logger2)
    }
}
