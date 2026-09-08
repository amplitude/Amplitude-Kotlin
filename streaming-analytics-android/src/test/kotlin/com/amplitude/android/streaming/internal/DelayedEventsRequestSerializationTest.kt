package com.amplitude.android.streaming.internal

import com.amplitude.android.streaming.internal.storage.DelayedEventsRequestEntity
import com.amplitude.android.streaming.internal.storage.delayedEventsStorageJson
import com.amplitude.android.streaming.internal.storage.toDelayedEvent
import com.amplitude.android.streaming.internal.storage.toEntity
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DelayedEventsRequestSerializationTest {
    @Nested
    inner class RoundTrip {
        @Test
        fun `should persist enriched BaseEvent fields as ingest json`() {
            val delayed =
                DelayedEvent(
                    eventType = "video_stopped",
                    kind = DelayedEvent.Kind.DELAYED,
                    timestamp = 1_700_000_000_000L,
                    deviceId = "device-1",
                    userId = "user-1",
                    sessionId = 42L,
                    eventProperties =
                        mutableMapOf(
                            "video_id" to "v-100",
                            "nested" to mapOf("quality" to "1080p"),
                        ),
                ).also { event ->
                    event.insertId = "insert-1"
                    event.library = "amplitude-android/1.0"
                    event.osName = "android"
                    event.osVersion = "14"
                    event.deviceModel = "Pixel 8"
                    event.platform = "Android"
                    event.appVersion = "2.0.0"
                    event.country = "US"
                    event.language = "en"
                    event.ip = "\$remote"
                    event.userProperties = mutableMapOf("plan" to "pro")
                }
            val instant =
                DelayedEvent(
                    eventType = "video_started",
                    kind = DelayedEvent.Kind.INSTANT,
                    timestamp = 1_700_000_000_000L,
                    eventProperties = mutableMapOf("video_id" to "v-100"),
                )
            val request =
                DelayedEventsRequestEntity(
                    id = "view-1",
                    timeoutMillis = 5_000L,
                    events = listOf(delayed.toEntity()),
                    instantEvents = listOf(instant.toEntity()),
                )

            val decoded =
                delayedEventsStorageJson.decodeFromString<DelayedEventsRequestEntity>(
                    delayedEventsStorageJson.encodeToString(request),
                )
            val restored = decoded.events.single().toDelayedEvent(DelayedEvent.Kind.DELAYED)

            assertEquals("view-1", decoded.id)
            assertEquals("video_stopped", restored.eventType)
            assertEquals("user-1", restored.userId)
            assertEquals("device-1", restored.deviceId)
            assertEquals(42L, restored.sessionId)
            assertEquals("insert-1", restored.insertId)
            assertEquals("amplitude-android/1.0", restored.library)
            assertEquals("android", restored.osName)
            assertEquals("14", restored.osVersion)
            assertEquals("Pixel 8", restored.deviceModel)
            assertEquals("Android", restored.platform)
            assertEquals("2.0.0", restored.appVersion)
            assertEquals("US", restored.country)
            assertEquals("en", restored.language)
            assertEquals("\$remote", restored.ip)
            assertEquals("v-100", restored.eventProperties?.get("video_id"))
            assertEquals("pro", restored.userProperties?.get("plan"))
            assertEquals(
                DelayedEvent.Kind.INSTANT,
                decoded.instantEvents?.single()?.toDelayedEvent(DelayedEvent.Kind.INSTANT)?.kind,
            )
        }

        @Test
        fun `should store events as amplitude ingest json objects`() {
            val request =
                DelayedEventsRequestEntity(
                    id = "view-2",
                    timeoutMillis = 1_000L,
                    events =
                        listOf(
                            DelayedEvent(
                                eventType = "audio_stopped",
                                kind = DelayedEvent.Kind.DELAYED,
                                timestamp = 1L,
                                eventProperties = mutableMapOf(),
                            ).toEntity(),
                        ),
                    queueKey = "queue-key",
                )

            val json = delayedEventsStorageJson.encodeToString(request)
            val body = JSONObject(json)
            val event = body.getJSONArray("events").getJSONObject(0)

            assertEquals("view-2", body.getString("id"))
            assertEquals(1_000L, body.getLong("timeoutMillis"))
            assertEquals("audio_stopped", event.getString("event_type"))
            assertEquals(1L, event.getLong("time"))
            assertFalse(event.has("eventType"))
            assertFalse(body.has("queueKey"))
            assertFalse(body.has("key"))
            assertNull(delayedEventsStorageJson.decodeFromString<DelayedEventsRequestEntity>(json).instantEvents)
            assertNull(delayedEventsStorageJson.decodeFromString<DelayedEventsRequestEntity>(json).queueKey)
        }
    }
}
