package com.amplitude.id.utilities

import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.io.InputStream
import java.util.Properties

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertiesFileTest {
    private lateinit var logger: Logger
    private lateinit var properties: Properties

    @BeforeEach
    fun setUp() {
        logger = mockk<Logger>(relaxed = true)
        properties = mockk<Properties>(relaxed = true)
    }

    @Test
    fun `test loading properties file with exception`() {
        val propertiesFile = PropertiesFile(File("/tmp"), "key", "prefix", logger)
        propertiesFile.underlyingProperties = properties

        every { properties.load(any<InputStream>()) } throws IllegalArgumentException()
        propertiesFile.load()
        verify(exactly = 1) { logger.error(any()) }
    }
}
