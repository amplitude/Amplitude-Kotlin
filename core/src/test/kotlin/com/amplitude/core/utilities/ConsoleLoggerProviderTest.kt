package com.amplitude.core.utilities

import com.amplitude.core.utils.FakeAmplitude
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ConsoleLoggerProviderTest {

    @ExperimentalCoroutinesApi
    @Test
    fun `test singleton instance`() {
        val amplitude = FakeAmplitude()
        val loggerProvider = ConsoleLoggerProvider()
        val logger1 = loggerProvider.getLogger(amplitude)
        val logger2 = loggerProvider.getLogger(amplitude)
        Assertions.assertEquals(logger1, logger2)
    }
}
