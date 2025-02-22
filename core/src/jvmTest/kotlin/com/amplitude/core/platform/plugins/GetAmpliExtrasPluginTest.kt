package com.amplitude.core.platform.plugins

import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utils.testAmplitude
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GetAmpliExtrasPluginTest {
    private lateinit var amplitude: Amplitude

    @ExperimentalCoroutinesApi
    @BeforeEach
    fun setup() {
        val testApiKey = "test-123"
        amplitude = testAmplitude(Configuration(testApiKey))
    }

    @Test
    fun `test missing ampli extra in event`() {
        val getAmpliExtrasPlugin = GetAmpliExtrasPlugin()
        getAmpliExtrasPlugin.setup(amplitude)
        val event = BaseEvent()
        getAmpliExtrasPlugin.execute(event)
        Assertions.assertEquals(event.ingestionMetadata, null)
    }

    @Test
    fun `test ingestion metadata in event`() {
        val getAmpliExtrasPlugin = GetAmpliExtrasPlugin()
        getAmpliExtrasPlugin.setup(amplitude)
        val event = BaseEvent()
        val sourceName = "test-source-name"
        val sourceVersion = "test-source-version"
        event.extra = mapOf(
            "ampli" to mapOf(
                "ingestionMetadata" to mapOf(
                    "sourceName" to sourceName,
                    "sourceVersion" to sourceVersion
                )
            )
        )
        getAmpliExtrasPlugin.execute(event)
        Assertions.assertEquals(event.ingestionMetadata?.sourceName, sourceName)
        Assertions.assertEquals(event.ingestionMetadata?.sourceVersion, sourceVersion)
    }

    @Test
    fun `test ingeston metadata in event with null value`() {
        val getAmpliExtrasPlugin = GetAmpliExtrasPlugin()
        getAmpliExtrasPlugin.setup(amplitude)
        val event = BaseEvent()
        val sourceName = "test-source-name"
        val sourceVersion = null
        event.extra = mapOf(
            "ampli" to mapOf(
                "ingestionMetadata" to mapOf(
                    "sourceName" to sourceName,
                    "sourceVersion" to sourceVersion
                )
            )
        )
        getAmpliExtrasPlugin.execute(event)
        Assertions.assertEquals(event.ingestionMetadata?.sourceName, sourceName)
        Assertions.assertEquals(event.ingestionMetadata?.sourceVersion, sourceVersion)
    }

    @Test
    fun `test empty ampli extra in event`() {
        val getAmpliExtrasPlugin = GetAmpliExtrasPlugin()
        getAmpliExtrasPlugin.setup(amplitude)
        val event = BaseEvent()
        event.extra = mapOf(
            "ampli" to mapOf(
                "ingestionMetadata" to null
            )
        )
        getAmpliExtrasPlugin.execute(event)
        Assertions.assertEquals(event.ingestionMetadata, null)
    }

    @Test
    fun `test wrong ampli extra in event`() {
        val getAmpliExtrasPlugin = GetAmpliExtrasPlugin()
        getAmpliExtrasPlugin.setup(amplitude)
        val event = BaseEvent()
        event.extra = mapOf("ampli" to "string")
        getAmpliExtrasPlugin.execute(event)
        Assertions.assertEquals(event.ingestionMetadata, null)
    }
}
