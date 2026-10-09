@file:OptIn(RestrictedAmplitudeFeature::class)

package com.amplitude.core.utilities

import com.amplitude.core.Configuration
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.http.SuccessResponse
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileResponseStreamingTest {
    @Test
    fun `should deliver every event in order and record the count`() {
        val eventsString = eventsStringOf("test1", "test2", "test3")

        val visited = mutableListOf<String?>()
        val count = handleEvents(eventsString) { visited.add(it.eventType) }

        assertEquals(3, count)
        assertEquals(listOf("test1", "test2", "test3"), visited)
    }

    @Test
    fun `should not report sent events for an empty array`() {
        val visited = mutableListOf<String?>()

        assertEquals(0, handleEvents("[]") { visited.add(it.eventType) })
        assertEquals(emptyList<String?>(), visited)
    }

    @Test
    fun `should tolerate surrounding and interleaved whitespace`() {
        val eventsString = """  [ {"event_type":"test1"} , {"event_type":"test2"} ]  """

        assertEquals(2, handleEvents(eventsString) {})
    }

    @Test
    fun `should tolerate a trailing comma like JSONArray does`() {
        val eventsString = """[{"event_type":"test1"},{"event_type":"test2"},]"""

        // JSONArray accepts this, so the streaming walk must too
        assertEquals(2, JSONArray(eventsString).length())
        assertEquals(2, handleEvents(eventsString) {})
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
        val count = handleEvents(eventsString) { streamed.add(it) }
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
            handleEvents("""{"event_type":"test1"}""") {}
        }
    }

    @Test
    fun `should throw when the array is truncated`() {
        assertThrows(JSONException::class.java) {
            handleEvents("""[{"event_type":"test1"},{"event_type":"test2"}""") {}
        }
    }

    @Test
    fun `should throw when an element is not an object`() {
        assertThrows(JSONException::class.java) {
            handleEvents("""[{"event_type":"test1"},7]""") {}
        }
    }

    @Test
    fun `should throw on an empty payload`() {
        assertThrows(JSONException::class.java) {
            handleEvents("") {}
        }
    }

    @Test
    fun `should surface events visited before a malformed tail`() {
        val visited = mutableListOf<String?>()

        assertThrows(JSONException::class.java) {
            handleEvents("""[{"event_type":"test1"} {"event_type":"test2"}]""") { visited.add(it.eventType) }
        }
        assertEquals(listOf("test1"), visited)
    }

    private fun handleEvents(
        eventsString: String,
        action: (BaseEvent) -> Unit,
    ): Int {
        var count = 0
        val diagnosticsClient = mockk<DiagnosticsClient>(relaxed = true)
        every { diagnosticsClient.increment("analytics.events.sent", any()) } answers { count = secondArg<Long>().toInt() }
        runTest {
            val handler =
                FileResponseHandler(
                    storage = mockk<EventsFileStorage>(relaxed = true),
                    eventPipeline = mockk<EventPipeline>(),
                    configuration = Configuration(apiKey = "test", callback = { event, _, _ -> action(event) }),
                    scope = this,
                    storageDispatcher = StandardTestDispatcher(testScheduler),
                    logger = null,
                    diagnosticsClient = diagnosticsClient,
                )
            try {
                handler.handleSuccessResponse(SuccessResponse(), "batch", eventsString)
            } finally {
                advanceUntilIdle()
            }
        }
        return count
    }

    private fun eventsStringOf(vararg eventTypes: String): String =
        JSONUtil.eventsToString(eventTypes.map { BaseEvent().apply { eventType = it } })
}
