package com.amplitude.core.utilities

import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONObject
import org.junit.jupiter.api.Test

class FileResponseHandlerTest {
    @Test
    fun testBadResponseHandlerForInvalidApiKey() {
        val response = BadRequestResponse(
            JSONObject("{\"error\":\"Invalid API key\"}")
        )
        val storage = mockk<EventsFileStorage>()
        val pipeline = mockk<EventPipeline>()
        val handler =
            FileResponseHandler(storage, pipeline, Configuration("test"), mockk(), mockk(), null)

        every {
            storage.removeFile("file_path")
        } returns true


        handler.handleBadRequestResponse(
            response, "file_path", JSONUtil.eventsToString(
                listOf(generateBaseEvent("test1"), generateBaseEvent("test2"))
            )
        )

        verify(exactly = 1) {
            storage.removeFile("file_path")
        }
    }

    private fun generateBaseEvent(eventType: String): BaseEvent {
        val baseEvent = BaseEvent()
        baseEvent.eventType = eventType
        return baseEvent
    }
}
