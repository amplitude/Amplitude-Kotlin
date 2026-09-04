package com.amplitude.android.streaming.internal

import com.amplitude.core.events.BaseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DelayedEventTest {
    @Test
    fun `wrapping copies property maps so later mutation does not affect the delayed event`() {
        val original = BaseEvent().apply {
            eventType = "Video Content Started"
            eventProperties = mutableMapOf("video_id" to "v-1")
            userProperties = mutableMapOf("plan" to "free")
            groups = mutableMapOf("org" to "amplitude")
            groupProperties = mutableMapOf("tier" to "a")
        }

        val delayed = DelayedEvent(original, DelayedEvent.Kind.DELAYED)

        original.eventProperties?.put("video_id", "v-2")
        original.userProperties?.put("plan", "paid")
        original.groups?.put("org", "other")
        original.groupProperties?.put("tier", "b")

        assertEquals("v-1", delayed.eventProperties?.get("video_id"))
        assertEquals("free", delayed.userProperties?.get("plan"))
        assertEquals("amplitude", delayed.groups?.get("org"))
        assertEquals("a", delayed.groupProperties?.get("tier"))
        assertNotSame(original.eventProperties, delayed.eventProperties)
        assertNotSame(original.userProperties, delayed.userProperties)
        assertNotSame(original.groups, delayed.groups)
        assertNotSame(original.groupProperties, delayed.groupProperties)
    }

    @Test
    fun `wrapping deep-copies nested maps`() {
        val nested = mutableMapOf<String, Any?>("season" to 1)
        val original = BaseEvent().apply {
            eventType = "Video Content Started"
            eventProperties = mutableMapOf("meta" to nested)
        }

        val delayed = DelayedEvent(original, DelayedEvent.Kind.INSTANT)
        nested["season"] = 2

        @Suppress("UNCHECKED_CAST")
        val copied = delayed.eventProperties?.get("meta") as Map<String, Any?>
        assertEquals(1, copied["season"])
        assertNotSame(nested, copied)
    }

    @Test
    fun `wrapping keeps null property maps null`() {
        val original = BaseEvent().apply { eventType = "Video Content Started" }
        val delayed = DelayedEvent(original, DelayedEvent.Kind.DELAYED)
        assertNull(delayed.eventProperties)
        assertNull(delayed.userProperties)
        assertNull(delayed.groups)
        assertNull(delayed.groupProperties)
    }
}
