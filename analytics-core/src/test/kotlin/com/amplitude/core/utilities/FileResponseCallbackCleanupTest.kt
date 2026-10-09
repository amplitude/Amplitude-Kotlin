package com.amplitude.core.utilities

import com.amplitude.core.Configuration
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.http.BadRequestResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
class FileResponseCallbackCleanupTest {
    @ParameterizedTest
    @ValueSource(strings = ["first", "middle", "last", "whitespace", "truncated"])
    fun `should remove callbacks independently of insert id key position`(position: String) =
        runTest {
            val insertId = "00000000-0000-0000-0000-000000000001"
            val payload =
                when (position) {
                    "first" -> """[{"insert_id":"$insertId","event_type":"test","user_id":"user"},truncated"""
                    "middle" -> """[{"event_type":"test","insert_id":"$insertId","user_id":"user"},truncated"""
                    "last" -> """[{"event_type":"test","insert_id":"$insertId"},truncated"""
                    "whitespace" -> """[{"event_type":"test", "insert_id" : "$insertId" },truncated"""
                    else -> """[{"event_type":"test","insert_id":"$insertId"""" // Complete value, missing object/array tail.
                }
            val storage = mockk<EventsFileStorage>(relaxed = true)
            every { storage.getEventCallback(insertId) } returns null
            val handler =
                FileResponseHandler(
                    storage,
                    mockk<EventPipeline>(),
                    Configuration(apiKey = "test"),
                    this,
                    StandardTestDispatcher(testScheduler),
                    null,
                )
            assertThrows<JSONException> {
                handler.handleBadRequestResponse(BadRequestResponse(JSONObject()), "batch", payload)
            }
            verify(exactly = 0) { storage.removeEventCallback(insertId) }
            advanceUntilIdle()
            verify(exactly = 1) { storage.removeEventCallback(insertId) }
            verify(exactly = 1) { storage.removeFile("batch") }
        }
}
