package com.amplitude.core.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
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
}
