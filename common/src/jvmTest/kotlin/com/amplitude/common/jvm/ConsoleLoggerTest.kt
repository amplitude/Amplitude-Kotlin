package com.amplitude.common.jvm

import com.amplitude.common.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsoleLoggerTest {
    private var outContent: ByteArrayOutputStream? = null
    private val originalOut: PrintStream = System.out

    @BeforeEach
    fun setUpStreams() {
        outContent = ByteArrayOutputStream()
        System.setOut(PrintStream(outContent))
    }

    @AfterEach
    @Throws(IOException::class)
    fun restoreStreams() {
        outContent!!.close()
        System.setOut(originalOut)
    }

    @ParameterizedTest
    @MethodSource("logArguments")
    fun `test log with various modes`(
        logMode: Logger.LogMode,
        expectedErrorLog: String,
        expectedWarnLog: String,
        expectedDebugLog: String,
        expectedInfoLog: String
    ) {
        val logger = ConsoleLogger.logger
        logger.logMode = logMode
        logger.error("error message")
        assertEquals(expectedErrorLog, outContent.toString().trim())
        logger.warn("warn message")
        assertEquals(expectedWarnLog, outContent.toString().trim())
        logger.debug("debug message")
        assertEquals(expectedDebugLog, outContent.toString().trim())
        logger.info("info message")
        assertEquals(expectedInfoLog, outContent.toString().trim())
    }

    fun logArguments(): Stream<Arguments?>? {
        return Stream.of(
            arguments(Logger.LogMode.ERROR, "error message", "error message", "error message", "error message"),
            arguments(
                Logger.LogMode.WARN,
                "error message",
                "error message\nwarn message",
                "error message\nwarn message",
                "error message\nwarn message"
            ),
            arguments(
                Logger.LogMode.DEBUG,
                "error message",
                "error message\nwarn message",
                "error message\nwarn message\ndebug message",
                "error message\nwarn message\ndebug message\ninfo message"
            ),
            arguments(
                Logger.LogMode.INFO,
                "error message",
                "error message\nwarn message",
                "error message\nwarn message",
                "error message\nwarn message\ninfo message"
            ),
            arguments(Logger.LogMode.OFF, "", "", "", "")
        )
    }
}
