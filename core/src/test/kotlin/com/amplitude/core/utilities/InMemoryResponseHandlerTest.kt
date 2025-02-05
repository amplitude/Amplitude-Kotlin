package com.amplitude.core.utilities

import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.http.BadRequestResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONObject
import org.junit.jupiter.api.Test

class InMemoryResponseHandlerTest {
    @Test
    fun testBadResponseHandlerForInvalidApiKey() {
        val response = BadRequestResponse(
            JSONObject("{\"error\":\"Invalid API key\"}")
        )
        val storage = mockk<EventsFileStorage>()
        val pipeline = mockk<EventPipeline>()
        val handler =
            InMemoryResponseHandler(pipeline, Configuration("test"), mockk(), mockk())

        every {
            storage.removeFile("file_path")
        } returns true

        handler.handleBadRequestResponse(
            response,
            listOf(generateBaseEvent("test1"), generateBaseEvent("test2")),
            ""
        )

        verify(exactly = 0) {
            pipeline.put(any())
        }
    }

    private fun generateBaseEvent(eventType: String): BaseEvent {
        val baseEvent = BaseEvent()
        baseEvent.eventType = eventType
        return baseEvent
    }
}
