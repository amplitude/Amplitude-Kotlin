package com.amplitude.android

import android.app.Application
import android.content.Context
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.core.ServerZone
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import io.mockk.mockk
import io.mockk.mockkStatic
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

class ConfigurationBuilderTest {
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        context = mockk<Application>(relaxed = true)
        mockkStatic(AndroidLifecyclePlugin::class)
    }

    @Nested
    inner class ExistingConstructorCompatibility {
        @Test
        fun `minimal constructor with apiKey and context`() {
            val config = Configuration("test-key", context)
            assertTrue(config.isValid())
            assertEquals("test-key", config.apiKey)
        }

        @Test
        fun `constructor with autocapture options`() {
            val config =
                Configuration(
                    "test-key",
                    context,
                    autocapture =
                        autocaptureOptions {
                            +sessions
                            +appLifecycles
                            +screenViews
                        },
                )
            assertTrue(AutocaptureOption.SESSIONS in config.autocapture)
            assertTrue(AutocaptureOption.APP_LIFECYCLES in config.autocapture)
            assertTrue(AutocaptureOption.SCREEN_VIEWS in config.autocapture)
            assertFalse(AutocaptureOption.DEEP_LINKS in config.autocapture)
        }

        @Test
        fun `constructor with named core params`() {
            val config =
                Configuration(
                    "test-key",
                    context,
                    flushQueueSize = 50,
                    serverZone = ServerZone.EU,
                    useBatch = true,
                )
            assertEquals(50, config.flushQueueSize)
            assertEquals(ServerZone.EU, config.serverZone)
            assertTrue(config.useBatch)
        }

        @Test
        fun `constructor with android-specific params`() {
            val config =
                Configuration(
                    "test-key",
                    context,
                    minTimeBetweenSessionsMillis = 10000,
                    flushEventsOnClose = false,
                    locationListening = true,
                    enableCoppaControl = true,
                )
            assertEquals(10000, config.minTimeBetweenSessionsMillis)
            assertFalse(config.flushEventsOnClose)
            assertTrue(config.locationListening)
            assertTrue(config.enableCoppaControl)
        }

        @Suppress("DEPRECATION")
        @Test
        fun `deprecated constructor with trackingSessionEvents`() {
            val config =
                Configuration(
                    "test-key",
                    context,
                    trackingSessionEvents = false,
                )
            assertFalse(AutocaptureOption.SESSIONS in config.autocapture)
        }

        @Suppress("DEPRECATION")
        @Test
        fun `deprecated constructor with defaultTracking`() {
            val config =
                Configuration(
                    "test-key",
                    context,
                    defaultTracking =
                        DefaultTrackingOptions(
                            sessions = false,
                            appLifecycles = true,
                            deepLinks = true,
                            screenViews = false,
                        ),
                )
            assertFalse(AutocaptureOption.SESSIONS in config.autocapture)
            assertTrue(AutocaptureOption.APP_LIFECYCLES in config.autocapture)
            assertTrue(AutocaptureOption.DEEP_LINKS in config.autocapture)
            assertFalse(AutocaptureOption.SCREEN_VIEWS in config.autocapture)
        }

        @Test
        fun `constructor with serverUrl and httpClient`() {
            val config =
                Configuration(
                    apiKey = "test-key",
                    context = context,
                    serverUrl = "https://custom.example.com",
                    enableRequestBodyCompression = true,
                )
            assertEquals("https://custom.example.com", config.getApiHost())
            assertTrue(config.shouldCompressUploadBody())
        }

        @Test
        fun `constructor with all non-deprecated params`() {
            val plan = Plan(branch = "main", source = "test")
            val ingestion = IngestionMetadata(sourceName = "test-source")
            val config =
                Configuration(
                    apiKey = "test-key",
                    context = context,
                    flushQueueSize = 10,
                    flushIntervalMillis = 5000,
                    instanceName = "custom",
                    optOut = true,
                    minIdLength = 5,
                    partnerId = "partner-123",
                    flushMaxRetries = 3,
                    useBatch = true,
                    serverZone = ServerZone.EU,
                    serverUrl = "https://custom.example.com",
                    plan = plan,
                    ingestionMetadata = ingestion,
                    useAdvertisingIdForDeviceId = true,
                    useAppSetIdForDeviceId = true,
                    newDeviceIdPerInstall = true,
                    enableCoppaControl = true,
                    locationListening = true,
                    flushEventsOnClose = false,
                    minTimeBetweenSessionsMillis = 10000,
                    autocapture = setOf(AutocaptureOption.SESSIONS, AutocaptureOption.APP_LIFECYCLES),
                    identifyBatchIntervalMillis = 10000L,
                    migrateLegacyData = false,
                    offline = true,
                    deviceId = "device-abc",
                    sessionId = 12345L,
                    enableDiagnostics = false,
                    enableRequestBodyCompression = true,
                    enableAutocaptureRemoteConfig = false,
                )

            assertTrue(config.isValid())
            assertEquals(10, config.flushQueueSize)
            assertEquals(5000, config.flushIntervalMillis)
            assertEquals("custom", config.instanceName)
            assertTrue(config.optOut)
            assertEquals(5, config.minIdLength)
            assertEquals("partner-123", config.partnerId)
            assertEquals(3, config.flushMaxRetries)
            assertTrue(config.useBatch)
            assertEquals(ServerZone.EU, config.serverZone)
            assertEquals("https://custom.example.com", config.serverUrl)
            assertEquals(plan, config.plan)
            assertEquals(ingestion, config.ingestionMetadata)
            assertTrue(config.useAdvertisingIdForDeviceId)
            assertTrue(config.useAppSetIdForDeviceId)
            assertTrue(config.newDeviceIdPerInstall)
            assertTrue(config.enableCoppaControl)
            assertTrue(config.locationListening)
            assertFalse(config.flushEventsOnClose)
            assertEquals(10000, config.minTimeBetweenSessionsMillis)
            assertTrue(AutocaptureOption.SESSIONS in config.autocapture)
            assertTrue(AutocaptureOption.APP_LIFECYCLES in config.autocapture)
            assertEquals(10000L, config.identifyBatchIntervalMillis)
            assertFalse(config.migrateLegacyData)
            assertEquals(true, config.offline)
            assertEquals("device-abc", config.deviceId)
            assertEquals(12345L, config.sessionId)
            assertFalse(config.enableDiagnostics)
            assertTrue(config.enableRequestBodyCompression)
            assertFalse(config.enableAutocaptureRemoteConfig)
        }
    }

    @Nested
    inner class BuilderPattern {
        @Test
        fun `builder with defaults matches constructor defaults`() {
            val fromConstructor = Configuration("test-key", context)
            val fromBuilder = configuration("test-key", context)

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

            // Android-specific defaults
            assertEquals(fromConstructor.useAdvertisingIdForDeviceId, fromBuilder.useAdvertisingIdForDeviceId)
            assertEquals(fromConstructor.useAppSetIdForDeviceId, fromBuilder.useAppSetIdForDeviceId)
            assertEquals(fromConstructor.newDeviceIdPerInstall, fromBuilder.newDeviceIdPerInstall)
            assertEquals(fromConstructor.enableCoppaControl, fromBuilder.enableCoppaControl)
            assertEquals(fromConstructor.locationListening, fromBuilder.locationListening)
            assertEquals(fromConstructor.flushEventsOnClose, fromBuilder.flushEventsOnClose)
            assertEquals(fromConstructor.minTimeBetweenSessionsMillis, fromBuilder.minTimeBetweenSessionsMillis)
            assertEquals(fromConstructor.autocapture, fromBuilder.autocapture)
            assertEquals(fromConstructor.migrateLegacyData, fromBuilder.migrateLegacyData)
            assertEquals(fromConstructor.enableAutocaptureRemoteConfig, fromBuilder.enableAutocaptureRemoteConfig)
        }

        @Test
        fun `builder DSL sets properties correctly`() {
            val config =
                configuration("test-key", context) {
                    flushQueueSize = 50
                    serverZone = ServerZone.EU
                    autocapture = setOf(AutocaptureOption.SESSIONS, AutocaptureOption.APP_LIFECYCLES)
                    minTimeBetweenSessionsMillis = 10000
                }

            assertTrue(config.isValid())
            assertEquals(50, config.flushQueueSize)
            assertEquals(ServerZone.EU, config.serverZone)
            assertTrue(AutocaptureOption.SESSIONS in config.autocapture)
            assertTrue(AutocaptureOption.APP_LIFECYCLES in config.autocapture)
            assertEquals(10000, config.minTimeBetweenSessionsMillis)
        }

        @Test
        fun `builder explicit build call works`() {
            val builder = ConfigurationBuilder("test-key", context)
            builder.autocapture = setOf(AutocaptureOption.SESSIONS)
            builder.serverUrl = "https://custom.example.com"
            builder.enableRequestBodyCompression = true

            val config = builder.build()

            assertTrue(config.isValid())
            assertTrue(AutocaptureOption.SESSIONS in config.autocapture)
            assertEquals("https://custom.example.com", config.getApiHost())
            assertTrue(config.shouldCompressUploadBody())
        }

        @Test
        fun `builder with all properties`() {
            val plan = Plan(branch = "main", source = "test")
            val ingestion = IngestionMetadata(sourceName = "test-source")
            val config =
                configuration("test-key", context) {
                    flushQueueSize = 10
                    flushIntervalMillis = 5000
                    instanceName = "custom"
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
                    useAdvertisingIdForDeviceId = true
                    useAppSetIdForDeviceId = true
                    newDeviceIdPerInstall = true
                    enableCoppaControl = true
                    locationListening = true
                    flushEventsOnClose = false
                    minTimeBetweenSessionsMillis = 10000
                    autocapture = setOf(AutocaptureOption.SESSIONS, AutocaptureOption.APP_LIFECYCLES)
                    migrateLegacyData = false
                    enableAutocaptureRemoteConfig = false
                }

            assertEquals(10, config.flushQueueSize)
            assertEquals(5000, config.flushIntervalMillis)
            assertEquals("custom", config.instanceName)
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
            assertTrue(config.useAdvertisingIdForDeviceId)
            assertTrue(config.useAppSetIdForDeviceId)
            assertTrue(config.newDeviceIdPerInstall)
            assertTrue(config.enableCoppaControl)
            assertTrue(config.locationListening)
            assertFalse(config.flushEventsOnClose)
            assertEquals(10000, config.minTimeBetweenSessionsMillis)
            assertTrue(AutocaptureOption.SESSIONS in config.autocapture)
            assertTrue(AutocaptureOption.APP_LIFECYCLES in config.autocapture)
            assertFalse(config.migrateLegacyData)
            assertFalse(config.enableAutocaptureRemoteConfig)
        }

        @Test
        fun `builder produces valid configuration`() {
            val config = configuration("test-key", context)
            assertTrue(config.isValid())
        }

        @Test
        fun `builder with empty apiKey produces invalid configuration`() {
            val config = configuration("", context)
            assertFalse(config.isValid())
        }

        @Test
        fun `builder with empty autocapture`() {
            val config =
                configuration("test-key", context) {
                    autocapture = emptySet()
                }
            assertTrue(config.autocapture.isEmpty())
        }

        @Test
        fun `builder result is an Android Configuration`() {
            val config = configuration("test-key", context)
            // The returned type should be the Android Configuration, not just core
            assertTrue(config is com.amplitude.android.Configuration)
        }
    }

    @Nested
    inner class ReadOnlyBehavior {
        private val originalOut = System.out
        private lateinit var outputCapture: ByteArrayOutputStream

        @BeforeEach
        fun setUpCapture() {
            outputCapture = ByteArrayOutputStream()
            System.setOut(PrintStream(outputCapture))
        }

        @AfterEach
        fun tearDownCapture() {
            System.setOut(originalOut)
        }

        @Test
        fun `mutating read-only core property logs warning but applies value`() {
            val config = configuration("test-key", context) { flushQueueSize = 10 }

            config.flushQueueSize = 99

            assertEquals(99, config.flushQueueSize)
            val output = outputCapture.toString()
            assertTrue(output.contains("flushQueueSize"))
            assertTrue(output.contains("should not be modified"))
        }

        @Test
        fun `mutating read-only android property logs warning but applies value`() {
            val config = configuration("test-key", context)

            config.minTimeBetweenSessionsMillis = 99999

            assertEquals(99999, config.minTimeBetweenSessionsMillis)
            val output = outputCapture.toString()
            assertTrue(output.contains("minTimeBetweenSessionsMillis"))
            assertTrue(output.contains("should not be modified"))
        }

        @Test
        fun `mutating offline does not log warning`() {
            val config = configuration("test-key", context)

            config.offline = true

            assertEquals(true, config.offline)
            val output = outputCapture.toString()
            assertFalse(output.contains("offline"))
        }

        @Test
        fun `mutating optOut does not log warning`() {
            val config = configuration("test-key", context)

            config.optOut = true

            assertTrue(config.optOut)
            val output = outputCapture.toString()
            assertFalse(output.contains("optOut"))
        }

        @Test
        fun `constructor-created config does not log warning on mutation`() {
            val config = Configuration("test-key", context)

            config.flushQueueSize = 99
            config.minTimeBetweenSessionsMillis = 99999

            assertEquals(99, config.flushQueueSize)
            assertEquals(99999, config.minTimeBetweenSessionsMillis)
            val output = outputCapture.toString()
            assertFalse(output.contains("should not be modified"))
        }

        @Test
        fun `multiple read-only properties warn independently`() {
            val config = configuration("test-key", context)

            config.serverZone = ServerZone.EU
            config.enableCoppaControl = true
            config.flushEventsOnClose = false

            assertEquals(ServerZone.EU, config.serverZone)
            assertTrue(config.enableCoppaControl)
            assertFalse(config.flushEventsOnClose)

            val output = outputCapture.toString()
            assertTrue(output.contains("serverZone"))
            assertTrue(output.contains("enableCoppaControl"))
            assertTrue(output.contains("flushEventsOnClose"))
        }
    }
}
