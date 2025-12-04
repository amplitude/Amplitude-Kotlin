package com.amplitude.android.plugins.privacylayer

import com.amplitude.android.plugins.privacylayer.models.RedactionStrategy
import com.amplitude.android.plugins.privacylayer.models.ScanField
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PrivacyLayerPluginTest {
    private lateinit var plugin: PrivacyLayerPlugin
    private lateinit var mockAmplitude: Amplitude

    @BeforeEach
    fun setup() {
        mockAmplitude = mockk(relaxed = true)
        plugin = PrivacyLayerPlugin()
        plugin.setup(mockAmplitude)
    }

    @Test
    fun `plugin type should be Before`() {
        assertEquals(Plugin.Type.Before, plugin.type)
    }

    @Test
    fun `plugin should process event without error`() {
        val event =
            BaseEvent().apply {
                eventType = "test_event"
                eventProperties = mutableMapOf("key" to "value")
            }

        val result = plugin.execute(event)

        assertNotNull(result)
        assertEquals("test_event", result?.eventType)
    }

    @Test
    fun `plugin should handle null properties`() {
        val event =
            BaseEvent().apply {
                eventType = "test_event"
                eventProperties = null
                userProperties = null
            }

        val result = plugin.execute(event)

        assertNotNull(result)
    }

    @Test
    fun `plugin should process nested maps`() {
        val event =
            BaseEvent().apply {
                eventType = "test_event"
                eventProperties =
                    mutableMapOf(
                        "level1" to
                            mapOf(
                                "level2" to "value",
                            ),
                    )
            }

        val result = plugin.execute(event)

        assertNotNull(result)
        assertNotNull(result?.eventProperties)
    }

    @Test
    fun `plugin should process arrays`() {
        val event =
            BaseEvent().apply {
                eventType = "test_event"
                eventProperties =
                    mutableMapOf(
                        "array" to listOf("item1", "item2", "item3"),
                    )
            }

        val result = plugin.execute(event)

        assertNotNull(result)
        val props = result?.eventProperties
        assertNotNull(props)
        assertEquals(listOf("item1", "item2", "item3"), props?.get("array"))
    }

    @Test
    fun `plugin should respect scanFields configuration`() {
        val config =
            PrivacyLayerConfig(
                scanFields = setOf(ScanField.EVENT_PROPERTIES),
            )
        plugin = PrivacyLayerPlugin(config)
        plugin.setup(mockAmplitude)

        val event =
            BaseEvent().apply {
                eventType = "test_event"
                eventProperties = mutableMapOf("key" to "value")
                userProperties = mutableMapOf("user_key" to "user_value")
            }

        val result = plugin.execute(event)

        assertNotNull(result)
        // Both should still be present (no PII detected in Phase 1)
        assertNotNull(result?.eventProperties)
        assertNotNull(result?.userProperties)
    }

    @Test
    fun `plugin should handle different redaction strategies`() {
        // TYPE_SPECIFIC
        val plugin1 =
            PrivacyLayerPlugin(
                PrivacyLayerConfig(redactionStrategy = RedactionStrategy.TYPE_SPECIFIC),
            )
        plugin1.setup(mockAmplitude)

        // HASH
        val plugin2 =
            PrivacyLayerPlugin(
                PrivacyLayerConfig(redactionStrategy = RedactionStrategy.HASH),
            )
        plugin2.setup(mockAmplitude)

        // REMOVE
        val plugin3 =
            PrivacyLayerPlugin(
                PrivacyLayerConfig(redactionStrategy = RedactionStrategy.REMOVE),
            )
        plugin3.setup(mockAmplitude)

        val event =
            BaseEvent().apply {
                eventType = "test"
                eventProperties = mutableMapOf("key" to "value")
            }

        // All should process without error (no PII detected in Phase 1)
        assertNotNull(plugin1.execute(event))
        assertNotNull(plugin2.execute(event))
        assertNotNull(plugin3.execute(event))
    }

    @Test
    fun `plugin should handle empty strings`() {
        val event =
            BaseEvent().apply {
                eventType = "test"
                eventProperties = mutableMapOf("key" to "")
            }

        val result = plugin.execute(event)

        assertNotNull(result)
        assertEquals("", result?.eventProperties?.get("key"))
    }

    @Test
    fun `plugin should handle very large strings`() {
        val largeString = "x".repeat(20000) // Exceeds default maxTextLength
        val event =
            BaseEvent().apply {
                eventType = "test"
                eventProperties = mutableMapOf("large" to largeString)
            }

        val result = plugin.execute(event)

        assertNotNull(result)
        assertEquals(largeString, result?.eventProperties?.get("large"))
    }

    @Test
    fun `plugin should handle mixed types in properties`() {
        val event =
            BaseEvent().apply {
                eventType = "test"
                eventProperties =
                    mutableMapOf(
                        "string" to "text",
                        "number" to 42,
                        "boolean" to true,
                        "null" to null,
                        "map" to mapOf("nested" to "value"),
                        "list" to listOf(1, 2, 3),
                    )
            }

        val result = plugin.execute(event)

        assertNotNull(result)
        val props = result?.eventProperties
        assertEquals("text", props?.get("string"))
        assertEquals(42, props?.get("number"))
        assertEquals(true, props?.get("boolean"))
        assertEquals(null, props?.get("null"))
        assertNotNull(props?.get("map"))
        assertEquals(listOf(1, 2, 3), props?.get("list"))
    }
}
