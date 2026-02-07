package com.amplitude.core.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticsEventTest {
    @Test
    fun `round trips nested maps and lists`() {
        val properties: Map<String, Any?> =
            mapOf(
                "string" to "value",
                "int" to 1,
                "bool" to true,
                "nested" to
                    mapOf(
                        "level" to 2,
                        "array" to listOf("a", 3, mapOf("deep" to false)),
                    ),
                "list" to listOf(1, 2, mapOf("k" to "v")),
            )

        val event =
            DiagnosticsEvent(
                eventName = "test",
                time = 123.0,
                eventProperties = properties,
            )

        val decoded = DiagnosticsEvent.fromJsonString(event.toJsonString())

        assertEquals(properties, decoded?.eventProperties)
    }

    @Test
    fun `decodes nested JSON objects and arrays into maps and lists`() {
        val jsonObject =
            JSONObject().apply {
                put("a", 1)
                put("b", JSONObject().put("c", "d"))
            }
        val jsonArray = JSONArray().apply { put(1).put(JSONObject().put("x", true)) }

        val properties: Map<String, Any?> =
            mapOf(
                "obj" to jsonObject,
                "arr" to jsonArray,
            )

        val event =
            DiagnosticsEvent(
                eventName = "json",
                time = 456.0,
                eventProperties = properties,
            )

        val decoded = DiagnosticsEvent.fromJsonString(event.toJsonString())

        val expected: Map<String, Any?> =
            mapOf(
                "obj" to mapOf("a" to 1, "b" to mapOf("c" to "d")),
                "arr" to listOf(1, mapOf("x" to true)),
            )

        assertEquals(expected, decoded?.eventProperties)
    }

    @Test
    fun `event without properties serializes correctly`() {
        val event =
            DiagnosticsEvent(
                eventName = "simple_event",
                time = 789.0,
                eventProperties = null,
            )

        val json = event.toJSONObject()

        assertEquals("simple_event", json.getString("event_name"))
        assertEquals(789.0, json.getDouble("time"))
        assertEquals(false, json.has("event_properties"))
    }

    @Test
    fun `event with empty properties serializes correctly`() {
        val event =
            DiagnosticsEvent(
                eventName = "empty_props",
                time = 100.0,
                eventProperties = emptyMap(),
            )

        val json = event.toJSONObject()

        assertEquals("empty_props", json.getString("event_name"))
        assertTrue(json.has("event_properties"))
        assertEquals(0, json.getJSONObject("event_properties").length())
    }

    @Test
    fun `fromJsonString returns null for blank event name`() {
        val json =
            JSONObject().apply {
                put("event_name", "")
                put("time", 123.0)
            }

        val event = DiagnosticsEvent.fromJSONObject(json)

        assertEquals(null, event)
    }

    @Test
    fun `fromJsonString returns null for missing time`() {
        val json =
            JSONObject().apply {
                put("event_name", "test")
            }

        val event = DiagnosticsEvent.fromJSONObject(json)

        assertEquals(null, event)
    }

    @Test
    fun `fromJsonString handles null values in properties`() {
        val json =
            JSONObject().apply {
                put("event_name", "test")
                put("time", 123.0)
                put(
                    "event_properties",
                    JSONObject().apply {
                        put("null_value", JSONObject.NULL)
                        put("string_value", "test")
                    },
                )
            }

        val event = DiagnosticsEvent.fromJSONObject(json)

        assertEquals("test", event?.eventName)
        assertEquals(null, event?.eventProperties?.get("null_value"))
        assertEquals("test", event?.eventProperties?.get("string_value"))
    }

    @Test
    fun `toJsonString produces valid parseable JSON`() {
        val event =
            DiagnosticsEvent(
                eventName = "test_event",
                time = 1234567890.123,
                eventProperties = mapOf("key" to "value"),
            )

        val jsonString = event.toJsonString()
        val parsed = JSONObject(jsonString)

        assertEquals("test_event", parsed.getString("event_name"))
        assertEquals(1234567890.123, parsed.getDouble("time"), 0.001)
    }

    @Test
    fun `event handles numeric values in properties`() {
        val properties: Map<String, Any?> =
            mapOf(
                "int" to 42,
                "long" to 9999999999L,
                "double" to 3.14159,
            )

        val event =
            DiagnosticsEvent(
                eventName = "numeric",
                time = 100.0,
                eventProperties = properties,
            )

        val decoded = DiagnosticsEvent.fromJsonString(event.toJsonString())

        // JSON doesn't distinguish between numeric types precisely
        // Just verify the values are numerically equivalent
        assertEquals(42, (decoded?.eventProperties?.get("int") as Number).toInt())
        assertEquals(9999999999L, (decoded?.eventProperties?.get("long") as Number).toLong())
        assertEquals(3.14159, (decoded?.eventProperties?.get("double") as Number).toDouble(), 0.0001)
    }
}
