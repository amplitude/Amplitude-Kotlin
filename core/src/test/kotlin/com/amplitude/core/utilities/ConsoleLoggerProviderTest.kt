package com.amplitude.core.utilities

import com.amplitude.core.Configuration
import com.amplitude.core.utils.testAmplitude
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ConsoleLoggerProviderTest {
    @Test
    fun `test singleton instance`() {
        val testApiKey = "test-123"
        val amplitude = testAmplitude(Configuration(testApiKey))
        val loggerProvider = ConsoleLoggerProvider()
        val logger1 = loggerProvider.getLogger(amplitude)
        val logger2 = loggerProvider.getLogger(amplitude)
        Assertions.assertEquals(logger1, logger2)
    }
}
