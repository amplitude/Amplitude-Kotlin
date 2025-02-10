package com.amplitude.id.utilities

import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Properties

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertiesFileTest {
    @TempDir
    @JvmField
    var tempFolder: File? = null

    private lateinit var mockedLogger: Logger
    private lateinit var mockedProperties: Properties

    @BeforeEach
    fun setUp() {
        mockedLogger = mockk<Logger>(relaxed = true)
        mockedProperties = mockk<Properties>(relaxed = true)
    }

    @Test
    fun `test loading properties file with exception`() {
        val tempFile = File(tempFolder, "prefix-key.properties")
        tempFile.createNewFile()

        val propertiesFile = PropertiesFile(tempFolder!!, "key", mockedLogger)
        propertiesFile.underlyingProperties = mockedProperties
        every { mockedProperties.load(any<InputStream>()) } throws Exception()

        propertiesFile.createPropertiesFile()
        propertiesFile.load()
        verify(exactly = 1) { mockedLogger.error(any()) }
    }

    @Test
    fun `test saving properties file with exception`() {
        val tempFile = File(tempFolder, "prefix-key.properties")
        tempFile.createNewFile()

        val propertiesFile = PropertiesFile(tempFolder!!, "key", mockedLogger)
        propertiesFile.underlyingProperties = mockedProperties
        every { mockedProperties.store(any<FileOutputStream>(), null) } throws Exception()

        propertiesFile.putLong("test", 1L)
        verify(exactly = 1) { mockedLogger.error(any()) }
    }

    @Test
    fun `test loading properties file successfully`() {
        val tempFile = File(tempFolder, "prefix-key.properties")
        tempFile.createNewFile()

        val propertiesFile = PropertiesFile(tempFolder!!, "key", mockedLogger)
        propertiesFile.underlyingProperties = mockedProperties
        every { mockedProperties.load(any<InputStream>()) } returns Unit

        propertiesFile.load()
        verify(exactly = 0) { mockedLogger.error(any()) }
    }
}
