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
        fun `trackVideoStarted sends Video Content Started delayed event with properties`() {
            tracker.trackVideoStarted(
                options = options,
                snapshot = snapshot,
                viewSessionId = "view-1",
                timestamp = 1_000L,
                insertId = "insert-start-1",
            )

            assertEquals(1, events.size)
            val event = events.first()
            assertTrue(event is DelayedEvent)
            assertEquals("Video Content Started", event.eventType)
            assertEquals(1_000L, event.timestamp)
            assertEquals("insert-start-1", event.insertId)

            val props = event.eventProperties!!
            assertEquals("view-1", props["view_session_id"])
            assertEquals("custom-id", props["content_id"])
            assertEquals("Custom Title", props["title"])
            assertEquals(PlayerContent.CONTENT_TYPE_VOD, props["content_type"])
            assertEquals(15.0, props["start_position"])
            assertEquals(60.0, props["duration"])
            assertEquals(true, props["is_in_picture_in_picture"])
            assertEquals(false, props["is_in_background"])
            assertEquals("news", props["channel"])
        }

        @Test
        fun `trackVideoStopped sends Video Content Stopped delayed event with properties`() {
            tracker.trackVideoStopped(
                options = options,
                snapshot = snapshot,
                viewSessionId = "view-1",
                watchDurationMillis = 5_000L,
                timestamp = 6_000L,
                insertId = "insert-stop-1",
                stopReason = StopReason.PAUSED,
            )

            assertEquals(1, events.size)
            val event = events.first()
            assertTrue(event is DelayedEvent)
            assertEquals("Video Content Stopped", event.eventType)
            assertEquals(6_000L, event.timestamp)
            assertEquals("insert-stop-1", event.insertId)

            val props = event.eventProperties!!
            assertEquals("view-1", props["view_session_id"])
            assertEquals(15.0, props["current_time"])
            assertEquals(5.0, props["watch_duration"])
            assertEquals("paused", props["stop_reason"])
            assertEquals(25.0, props["percent_completed"])
        }

        @Test
        fun `trackAudioStarted and trackAudioStopped default to audio content type`() {
            tracker.trackAudioStarted(
                options = PlayerContent(),
                snapshot = snapshot,
                viewSessionId = "view-audio",
                timestamp = 2_000L,
                insertId = "audio-start",
            )
            tracker.trackAudioStopped(
                options = PlayerContent(),
                snapshot = snapshot,
                viewSessionId = "view-audio",
                watchDurationMillis = 3_000L,
                timestamp = 5_000L,
                insertId = "audio-stop",
            )

            assertEquals(2, events.size)
            assertEquals("Audio Content Started", events[0].eventType)
            assertEquals(PlayerContent.CONTENT_TYPE_AUDIO, events[0].eventProperties?.get("content_type"))
            assertEquals("audio-start", events[0].insertId)

            assertEquals("Audio Content Stopped", events[1].eventType)
            assertEquals(PlayerContent.CONTENT_TYPE_AUDIO, events[1].eventProperties?.get("content_type"))
            assertEquals("audio-stop", events[1].insertId)
        }

        @Test
        fun `live stream omits duration and percent_completed`() {
            val liveSnapshot = snapshot.copy(isLive = true)
            tracker.trackVideoStopped(
                options = PlayerContent(),
                snapshot = liveSnapshot,
                viewSessionId = "view-live",
                watchDurationMillis = 10_000L,
                timestamp = 10_000L,
                insertId = "stop-live",
            )

            val props = events.first().eventProperties!!
            assertEquals(PlayerContent.CONTENT_TYPE_LIVE, props["content_type"])
            assertFalse(props.containsKey("duration"))
            assertFalse(props.containsKey("percent_completed"))
        }

        @Test
        fun `unknown duration omits duration and percent_completed`() {
            val unknownDurationSnapshot = snapshot.copy(durationMillis = C.TIME_UNSET)
            tracker.trackVideoStopped(
                options = PlayerContent(),
                snapshot = unknownDurationSnapshot,
                viewSessionId = "view-unknown",
                watchDurationMillis = 5_000L,
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
                viewSessionId = "view-ad-1",
            )

            assertEquals(1, events.size)
            val event = events.first()
            assertEquals("Ad Started", event.eventType)
            val props = event.eventProperties!!
            assertEquals("video-789:0:1", props["ad_id"])
            assertEquals("video-789", props["content_id"])
            assertEquals("view-ad-1", props["view_session_id"])
            assertEquals(45.0, props["ad_position"])
            assertEquals(30.0, props["ad_duration"])
            assertEquals("summer", props["ad_campaign"])
        }

        @Test
        fun `trackAdStopped records completion status and duration`() {
            tracker.trackAdStopped(
                options = options,
                ad = ad,
                viewSessionId = "view-ad-1",
                watchDurationMillis = 30_000L,
                completed = true,
            )

            val props = events.first().eventProperties!!
            assertEquals("Ad Stopped", events.first().eventType)
            assertEquals(30.0, props["ad_watch_duration"])
            assertEquals("completed", props["ad_completion_status"])
            assertEquals(33.333, (props["ad_percent_completed"] as Double), 0.01)
        }

        @Test
        fun `trackAdStopped records abandoned status when not completed`() {
            tracker.trackAdStopped(
                options = options,
                ad = ad,
                viewSessionId = "view-ad-1",
                watchDurationMillis = 5_000L,
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
                viewSessionId = "view-ad-1",
            )

            assertEquals(1, events.size)
            assertEquals("Ad Skipped", events.first().eventType)
            assertEquals("video-789:0:1", events.first().eventProperties?.get("ad_id"))
        }
    }
}
