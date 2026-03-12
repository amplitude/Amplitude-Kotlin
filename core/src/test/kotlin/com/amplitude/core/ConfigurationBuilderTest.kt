package com.amplitude.core

import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

internal class ConfigurationBuilderTest {
    @Nested
    inner class ExistingConstructorCompatibility {
        @Test
        fun `minimal constructor with only apiKey`() {
            val config = Configuration("test-key")
            assertTrue(config.isValid())
            assertEquals("test-key", config.apiKey)
            assertEquals(Configuration.FLUSH_QUEUE_SIZE, config.flushQueueSize)
            assertEquals(Configuration.FLUSH_INTERVAL_MILLIS, config.flushIntervalMillis)
        }

        @Test
        fun `constructor with single named param`() {
            val config = Configuration("test-key", flushQueueSize = 50)
            assertEquals(50, config.flushQueueSize)
            assertEquals(Configuration.FLUSH_INTERVAL_MILLIS, config.flushIntervalMillis)
        }

        @Test
        fun `constructor with multiple named params`() {
            val config =
                Configuration(
                    "test-key",
                    serverZone = ServerZone.EU,
                    useBatch = true,
                    optOut = true,
                )
            assertEquals(ServerZone.EU, config.serverZone)
            assertTrue(config.useBatch)
            assertTrue(config.optOut)
        }

        @Test
        fun `constructor with all core params set`() {
            val plan = Plan(branch = "main", source = "test")
            val ingestion = IngestionMetadata(sourceName = "test-source")
            val config =
                Configuration(
                    apiKey = "test-key",
                    flushQueueSize = 10,
                    flushIntervalMillis = 5000,
                    instanceName = "my-instance",
                    optOut = true,
                    storageProvider = InMemoryStorageProvider(),
                    loggerProvider = ConsoleLoggerProvider(),
                    minIdLength = 5,
                    partnerId = "partner-123",
                    callback = { _, _, _ -> },
                    flushMaxRetries = 3,
                    useBatch = true,
                    serverZone = ServerZone.EU,
                    serverUrl = "https://custom.example.com",
                    plan = plan,
                    ingestionMetadata = ingestion,
                    identifyBatchIntervalMillis = 10000L,
                    identifyInterceptStorageProvider = InMemoryStorageProvider(),
                    identityStorageProvider = IMIdentityStorageProvider(),
                    offline = true,
                    deviceId = "device-abc",
                    sessionId = 12345L,
                    enableDiagnostics = false,
                    enableRequestBodyCompression = true,
                )
            assertTrue(config.isValid())
            assertEquals(10, config.flushQueueSize)
            assertEquals(5000, config.flushIntervalMillis)
            assertEquals("my-instance", config.instanceName)
            assertTrue(config.optOut)
            assertEquals(5, config.minIdLength)
            assertEquals("partner-123", config.partnerId)
            assertEquals(3, config.flushMaxRetries)
            assertTrue(config.useBatch)
            assertEquals(ServerZone.EU, config.serverZone)
            assertEquals("https://custom.example.com", config.serverUrl)
            assertEquals(plan, config.plan)
            assertEquals(ingestion, config.ingestionMetadata)
            assertEquals(10000L, config.identifyBatchIntervalMillis)
            assertEquals(true, config.offline)
            assertEquals("device-abc", config.deviceId)
            assertEquals(12345L, config.sessionId)
            assertFalse(config.enableDiagnostics)
            assertTrue(config.enableRequestBodyCompression)
        }

        @Test
        fun `constructor with serverUrl and serverZone combination`() {
            val config =
                Configuration(
                    "test-key",
                    serverUrl = "https://custom.example.com",
                    serverZone = ServerZone.EU,
                )
            // Custom serverUrl takes precedence
            assertEquals("https://custom.example.com", config.getApiHost())
        }
    }

    @Nested
    inner class BuilderPattern {
        @Test
        fun `builder with defaults matches constructor defaults`() {
            val fromConstructor = Configuration("test-key")
            val fromBuilder = configuration("test-key")

            assertEquals(fromConstructor.apiKey, fromBuilder.apiKey)
            assertEquals(fromConstructor.flushQueueSize, fromBuilder.flushQueueSize)
            assertEquals(fromConstructor.flushIntervalMillis, fromBuilder.flushIntervalMillis)
            assertEquals(fromConstructor.instanceName, fromBuilder.instanceName)
            assertEquals(fromConstructor.optOut, fromBuilder.optOut)
            assertEquals(fromConstructor.minIdLength, fromBuilder.minIdLength)
            assertEquals(fromConstructor.partnerId, fromBuilder.partnerId)
            assertNull(fromBuilder.callback)
            assertEquals(fromConstructor.flushMaxRetries, fromBuilder.flushMaxRetries)
            assertEquals(fromConstructor.useBatch, fromBuilder.useBatch)
            assertEquals(fromConstructor.serverZone, fromBuilder.serverZone)
            assertEquals(fromConstructor.serverUrl, fromBuilder.serverUrl)
            assertEquals(fromConstructor.plan, fromBuilder.plan)
            assertEquals(fromConstructor.ingestionMetadata, fromBuilder.ingestionMetadata)
            assertEquals(fromConstructor.identifyBatchIntervalMillis, fromBuilder.identifyBatchIntervalMillis)
            assertEquals(fromConstructor.offline, fromBuilder.offline)
            assertEquals(fromConstructor.deviceId, fromBuilder.deviceId)
            assertEquals(fromConstructor.sessionId, fromBuilder.sessionId)
            assertEquals(fromConstructor.enableDiagnostics, fromBuilder.enableDiagnostics)
            assertEquals(fromConstructor.enableRequestBodyCompression, fromBuilder.enableRequestBodyCompression)
        }

        @Test
        fun `builder DSL sets properties correctly`() {
            val config =
                configuration("test-key") {
                    flushQueueSize = 50
                    serverZone = ServerZone.EU
                    useBatch = true
                    optOut = true
                    deviceId = "device-123"
                }

            assertTrue(config.isValid())
            assertEquals(50, config.flushQueueSize)
            assertEquals(ServerZone.EU, config.serverZone)
            assertTrue(config.useBatch)
            assertTrue(config.optOut)
            assertEquals("device-123", config.deviceId)
        }

        @Test
        fun `builder explicit build call works`() {
            val builder = ConfigurationBuilder("test-key")
            builder.flushQueueSize = 10
            builder.serverUrl = "https://custom.example.com"

            val config = builder.build()

            assertTrue(config.isValid())
            assertEquals(10, config.flushQueueSize)
            assertEquals("https://custom.example.com", config.serverUrl)
            assertEquals("https://custom.example.com", config.getApiHost())
        }

        @Test
        fun `builder with all properties`() {
            val plan = Plan(branch = "main", source = "test")
            val ingestion = IngestionMetadata(sourceName = "test-source")
            val config =
                configuration("test-key") {
                    flushQueueSize = 10
                    flushIntervalMillis = 5000
                    instanceName = "my-instance"
                    optOut = true
                    minIdLength = 5
                    partnerId = "partner-123"
                    flushMaxRetries = 3
                    useBatch = true
                    serverZone = ServerZone.EU
                    serverUrl = "https://custom.example.com"
                    this.plan = plan
                    this.ingestionMetadata = ingestion
                    identifyBatchIntervalMillis = 10000L
                    offline = true
                    deviceId = "device-abc"
                    sessionId = 12345L
                    enableDiagnostics = false
                    enableRequestBodyCompression = true
                }

            assertEquals(10, config.flushQueueSize)
            assertEquals(5000, config.flushIntervalMillis)
            assertEquals("my-instance", config.instanceName)
            assertTrue(config.optOut)
            assertEquals(5, config.minIdLength)
            assertEquals("partner-123", config.partnerId)
            assertEquals(3, config.flushMaxRetries)
            assertTrue(config.useBatch)
            assertEquals(ServerZone.EU, config.serverZone)
            assertEquals("https://custom.example.com", config.serverUrl)
            assertEquals(plan, config.plan)
            assertEquals(ingestion, config.ingestionMetadata)
            assertEquals(10000L, config.identifyBatchIntervalMillis)
            assertEquals(true, config.offline)
            assertEquals("device-abc", config.deviceId)
            assertEquals(12345L, config.sessionId)
            assertFalse(config.enableDiagnostics)
            assertTrue(config.enableRequestBodyCompression)
        }

        @Test
        fun `builder produces valid configuration`() {
            val config = configuration("test-key")
            assertTrue(config.isValid())
        }

        @Test
        fun `builder with empty apiKey produces invalid configuration`() {
            val config = configuration("")
            assertFalse(config.isValid())
        }

        @Test
        fun `builder with zero flushQueueSize produces invalid configuration`() {
            val config =
                configuration("test-key") {
                    flushQueueSize = 0
                }
            assertFalse(config.isValid())
        }
    }

    @Nested
    inner class FrozenBehavior {
        private val originalOut = System.out
        private lateinit var outputCapture: ByteArrayOutputStream

        @BeforeEach
        fun setUp() {
            outputCapture = ByteArrayOutputStream()
            System.setOut(PrintStream(outputCapture))
        }

        @AfterEach
        fun tearDown() {
            System.setOut(originalOut)
        }

        @Test
        fun `mutating frozen property logs warning and ignores new value`() {
            val config = configuration("test-key") { flushQueueSize = 10 }

            config.flushQueueSize = 99

            assertEquals(10, config.flushQueueSize)
            val output = outputCapture.toString()
            assertTrue(output.contains("flushQueueSize"))
            assertTrue(output.contains("frozen"))
        }

        @Test
        fun `mutating offline does not log warning`() {
            val config = configuration("test-key")

            config.offline = true

            assertEquals(true, config.offline)
            val output = outputCapture.toString()
            assertFalse(output.contains("offline"))
        }

        @Test
        fun `mutating optOut does not log warning`() {
            val config = configuration("test-key")

            config.optOut = true

            assertTrue(config.optOut)
            val output = outputCapture.toString()
            assertFalse(output.contains("optOut"))
        }

        @Test
        fun `constructor-created config does not log warning on mutation`() {
            val config = Configuration("test-key")

            config.flushQueueSize = 99

            assertEquals(99, config.flushQueueSize)
            val output = outputCapture.toString()
            assertFalse(output.contains("frozen"))
        }

        @Test
        fun `multiple frozen properties warn independently`() {
            val config = configuration("test-key")

            config.serverZone = ServerZone.EU
            config.useBatch = true
            config.instanceName = "changed"

            // Values should remain unchanged
            assertEquals(ServerZone.US, config.serverZone)
            assertFalse(config.useBatch)
            assertEquals(Configuration.DEFAULT_INSTANCE, config.instanceName)

            val output = outputCapture.toString()
            assertTrue(output.contains("serverZone"))
            assertTrue(output.contains("useBatch"))
            assertTrue(output.contains("instanceName"))
        }
    }
}
