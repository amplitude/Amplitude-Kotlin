package com.amplitude.core.utilities

import com.amplitude.core.events.BaseEvent
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class JSONUtilTest {
    @Nested
    inner class ToBaseEvent {
        @Test
        fun `restores version_name and currency from ingest json`() {
            val json =
                JSONObject()
                    .put("event_type", "revenue")
                    .put("version_name", "2.0.0-prod")
                    .put("currency", "USD")

            val event = json.toBaseEvent()

            assertEquals("2.0.0-prod", event.versionName)
            assertEquals("USD", event.currency)
        }

        @Test
        fun `leaves version_name and currency null when absent`() {
            val event = JSONObject().put("event_type", "click").toBaseEvent()

            assertNull(event.versionName)
            assertNull(event.currency)
        }

        @Test
        fun `round trips versionName and currency through eventToString`() {
            val original =
                BaseEvent().apply {
                    eventType = "revenue"
                    versionName = "2.0.0-prod"
                    currency = "USD"
                }

            val restored = JSONObject(JSONUtil.eventToString(original)).toBaseEvent()

            assertEquals("2.0.0-prod", restored.versionName)
            assertEquals("USD", restored.currency)
        }
    }
}
