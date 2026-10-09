package com.amplitude.core.utilities

import com.amplitude.core.events.BaseEvent
import org.json.JSONArray
import org.json.JSONException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * [forEachEvent] steps a [org.json.JSONTokener] by hand so events can be released one at a time,
 * so it has to stay equivalent to `JSONArray(eventsString).toEvents()` on every input.
 */
class ForEachEventTest {
    @Test
    fun `should visit every event in order and return the count`() {
        val eventsString = eventsStringOf("test1", "test2", "test3")

        val visited = mutableListOf<String?>()
        val count = forEachEvent(eventsString) { visited.add(it.eventType) }

        assertEquals(3, count)
        assertEquals(listOf("test1", "test2", "test3"), visited)
    }

    @Test
    fun `should return zero for an empty array`() {
        val visited = mutableListOf<String?>()

        assertEquals(0, forEachEvent("[]") { visited.add(it.eventType) })
        assertEquals(emptyList<String?>(), visited)
    }

    @Test
    fun `should tolerate surrounding and interleaved whitespace`() {
        val eventsString = """  [ {"event_type":"test1"} , {"event_type":"test2"} ]  """

        assertEquals(2, forEachEvent(eventsString) {})
    }

    @Test
    fun `should tolerate a trailing comma like JSONArray does`() {
        val eventsString = """[{"event_type":"test1"},{"event_type":"test2"},]"""

        // JSONArray accepts this, so the streaming walk must too
        assertEquals(2, JSONArray(eventsString).length())
        assertEquals(2, forEachEvent(eventsString) {})
    }

    @Test
    fun `should produce the same events as JSONArray toEvents`() {
        // exercise nested properties and multi-byte characters
        val eventsString =
            JSONUtil.eventsToString(
                listOf(
                    BaseEvent().apply {
                        eventType = "世界 😀"
                        eventProperties = mutableMapOf("a" to listOf(1, 2), "b" to mapOf("c" to "d"))
                    },
                    BaseEvent().apply { eventType = "test2" },
                ),
            )

        val streamed = mutableListOf<BaseEvent>()
        val count = forEachEvent(eventsString) { streamed.add(it) }
        val expected = JSONArray(eventsString).toEvents()

        assertEquals(expected.size, count)
        assertEquals(expected.map { it.eventType }, streamed.map { it.eventType })
        assertEquals(
            expected.map { it.eventProperties?.toString() },
            streamed.map { it.eventProperties?.toString() },
        )
    }

    @Test
    fun `should throw when the payload is not an array`() {
        assertThrows(JSONException::class.java) {
            forEachEvent("""{"event_type":"test1"}""") {}
        }
    }

    @Test
    fun `should throw when the array is truncated`() {
        assertThrows(JSONException::class.java) {
            forEachEvent("""[{"event_type":"test1"},{"event_type":"test2"}""") {}
        }
    }

    @Test
    fun `should throw when an element is not an object`() {
        assertThrows(JSONException::class.java) {
            forEachEvent("""[{"event_type":"test1"},7]""") {}
        }
    }

    @Test
    fun `should throw on an empty payload`() {
        assertThrows(JSONException::class.java) {
            forEachEvent("") {}
        }
    }

    @Test
    fun `should surface events visited before a malformed tail`() {
        val visited = mutableListOf<String?>()

        assertThrows(JSONException::class.java) {
            forEachEvent("""[{"event_type":"test1"} {"event_type":"test2"}]""") { visited.add(it.eventType) }
        }
        assertEquals(listOf("test1"), visited)
    }

    private fun eventsStringOf(vararg eventTypes: String): String =
        JSONUtil.eventsToString(eventTypes.map { BaseEvent().apply { eventType = it } })
}
