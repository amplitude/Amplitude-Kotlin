package com.amplitude.android.streaming.internal

import androidx.media3.common.C
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import com.amplitude.core.events.BaseEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(AmplitudePreview::class)
class StreamTrackerTest {
    private val events = mutableListOf<BaseEvent>()
    private val amplitude = mockk<Amplitude>(relaxed = true)
    private lateinit var tracker: StreamTracker

    @BeforeEach
    fun setUp() {
        events.clear()
        every { amplitude.track(any<BaseEvent>(), any(), any()) } answers {
            events.add(firstArg())
            amplitude
        }
        every { amplitude.track(any<String>(), any(), any()) } answers {
            val eventType = firstArg<String>()
            val props = secondArg<Map<String, Any?>?>()
            events.add(
                BaseEvent().apply {
                    this.eventType = eventType
                    this.eventProperties = props?.toMutableMap()
                },
            )
            amplitude
        }
        tracker = StreamTracker(amplitude)
    }

    @Nested
    inner class ContentEvents {
        private val snapshot =
            PlayerMediaSnapshot(
                positionMillis = 15_000L,
                durationMillis = 60_000L,
                mediaId = "media-123",
                title = "Test Video",
                isInPictureInPicture = true,
                isInBackground = false,
            )
        private val options =
            PlayerContent(
                contentId = "custom-id",
                title = "Custom Title",
                extraProperties = mapOf("channel" to "news"),
            )

        @Test
        fun `trackStreamStarted sends Stream Started delayed event with properties`() {
            tracker.trackStreamStarted(
                options = options,
                snapshot = snapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-1",
                timestamp = 1_000L,
                insertId = "insert-start-1",
            )

            assertEquals(1, events.size)
            val event = events.first()
            assertTrue(event is DelayedEvent)
            assertEquals("[Amplitude] Stream Started", event.eventType)
            assertEquals(1_000L, event.timestamp)
            assertEquals("insert-start-1", event.insertId)

            val props = event.eventProperties!!
            assertEquals("stream-1", props["stream_session_id"])
            assertEquals("video", props["media_type"])
            assertEquals("custom-id", props["content_id"])
            assertEquals("Custom Title", props["title"])
            assertEquals("on_demand", props["delivery_mode"])
            assertEquals(15.0, props["start_position"])
            assertEquals(60.0, props["duration"])
            assertEquals(true, props["is_in_picture_in_picture"])
            assertEquals(false, props["is_in_background"])
            assertEquals("news", props["channel"])
        }

        @Test
        fun `trackStreamStopped sends Stream Stopped delayed event with properties`() {
            tracker.trackStreamStopped(
                options = options,
                snapshot = snapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-1",
                streamDurationMillis = 5_000L,
                timestamp = 6_000L,
                insertId = "insert-stop-1",
                stopReason = StopReason.PAUSED,
            )

            assertEquals(1, events.size)
            val event = events.first()
            assertTrue(event is DelayedEvent)
            assertEquals("[Amplitude] Stream Stopped", event.eventType)
            assertEquals(6_000L, event.timestamp)
            assertEquals("insert-stop-1", event.insertId)

            val props = event.eventProperties!!
            assertEquals("stream-1", props["stream_session_id"])
            assertEquals("video", props["media_type"])
            assertEquals(15.0, props["current_time"])
            assertEquals(5.0, props["stream_duration"])
            assertEquals("paused", props["stop_reason"])
            assertEquals(25.0, props["percent_completed"])
        }

        @Test
        fun `audio streams use media_type audio and on_demand delivery_mode`() {
            tracker.trackStreamStarted(
                options = PlayerContent(),
                snapshot = snapshot,
                mediaType = MediaType.AUDIO,
                streamSessionId = "stream-audio",
                timestamp = 2_000L,
                insertId = "audio-start",
            )
            tracker.trackStreamStopped(
                options = PlayerContent(),
                snapshot = snapshot,
                mediaType = MediaType.AUDIO,
                streamSessionId = "stream-audio",
                streamDurationMillis = 3_000L,
                timestamp = 5_000L,
                insertId = "audio-stop",
            )

            assertEquals(2, events.size)
            assertEquals("[Amplitude] Stream Started", events[0].eventType)
            assertEquals("audio", events[0].eventProperties?.get("media_type"))
            assertEquals("on_demand", events[0].eventProperties?.get("delivery_mode"))
            assertEquals("audio-start", events[0].insertId)

            assertEquals("[Amplitude] Stream Stopped", events[1].eventType)
            assertEquals("audio", events[1].eventProperties?.get("media_type"))
            assertEquals("on_demand", events[1].eventProperties?.get("delivery_mode"))
            assertEquals("audio-stop", events[1].insertId)
        }

        @Test
        fun `live stream omits duration and percent_completed`() {
            val liveSnapshot = snapshot.copy(isLive = true)
            tracker.trackStreamStopped(
                options = PlayerContent(),
                snapshot = liveSnapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-live",
                streamDurationMillis = 10_000L,
                timestamp = 10_000L,
                insertId = "stop-live",
            )

            val props = events.first().eventProperties!!
            assertEquals("live", props["delivery_mode"])
            assertFalse(props.containsKey("duration"))
            assertFalse(props.containsKey("percent_completed"))
        }

        @Test
        fun `unknown duration omits duration and percent_completed`() {
            val unknownDurationSnapshot = snapshot.copy(durationMillis = C.TIME_UNSET)
            tracker.trackStreamStopped(
                options = PlayerContent(),
                snapshot = unknownDurationSnapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-unknown",
                streamDurationMillis = 5_000L,
                timestamp = 5_000L,
                insertId = "stop-unknown",
            )

            val props = events.first().eventProperties!!
            assertFalse(props.containsKey("duration"))
            assertFalse(props.containsKey("percent_completed"))
        }
    }

    @Nested
    inner class AdEvents {
        private val ad =
            AdContext(
                adGroupIndex = 0,
                adIndexInAdGroup = 1,
                positionMillis = 10_000L,
                durationMillis = 30_000L,
                contentPositionMillis = 45_000L,
                contentId = "video-789",
            )
        private val options = PlayerContent(extraProperties = mapOf("ad_campaign" to "summer"))

        @Test
        fun `trackAdStarted emits Ad Started with ad properties`() {
            tracker.trackAdStarted(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
            )

            assertEquals(1, events.size)
            val event = events.first()
            assertEquals("[Amplitude] Ad Started", event.eventType)
            val props = event.eventProperties!!
            assertEquals("video-789:0:1", props["ad_id"])
            assertEquals("video-789", props["content_id"])
            assertEquals("stream-ad-1", props["stream_session_id"])
            assertEquals(45.0, props["ad_position"])
            assertEquals(30.0, props["ad_duration"])
            assertEquals("summer", props["ad_campaign"])
        }

        @Test
        fun `trackAdStopped records completion status and duration`() {
            tracker.trackAdStopped(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
                streamDurationMillis = 30_000L,
                completed = true,
            )

            val props = events.first().eventProperties!!
            assertEquals("[Amplitude] Ad Stopped", events.first().eventType)
            assertEquals(30.0, props["ad_stream_duration"])
            assertEquals("completed", props["ad_completion_status"])
            assertEquals(33.333, (props["ad_percent_completed"] as Double), 0.01)
        }

        @Test
        fun `trackAdStopped records abandoned status when not completed`() {
            tracker.trackAdStopped(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
                streamDurationMillis = 5_000L,
                completed = false,
            )

            val props = events.first().eventProperties!!
            assertEquals("abandoned", props["ad_completion_status"])
        }

        @Test
        fun `trackAdSkipped emits Ad Skipped`() {
            tracker.trackAdSkipped(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
            )

            assertEquals(1, events.size)
            assertEquals("[Amplitude] Ad Skipped", events.first().eventType)
            assertEquals("video-789:0:1", events.first().eventProperties?.get("ad_id"))
        }
    }
}
