package com.amplitude.android.streaming.internal

import androidx.media3.common.C
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.internal.player.PlayerMediaSnapshot
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Timeline
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
                mediaType = MediaType.VIDEO,
            )
        private val options =
            PlayerContent(
                contentId = "custom-id",
                title = "Custom Title",
                extraProperties = mapOf("channel" to "news"),
            )

        @Test
        fun `trackStreamStarted sends Stream Started with shared identity and position keys`() {
            tracker.trackStreamStarted(
                options = options,
                snapshot = snapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-1",
                playId = "play-1",
                startTimeMillis = 15_000L,
                timestamp = 1_000L,
                insertId = "insert-start-1",
            )

            assertEquals(1, events.size)
            val event = events.first() as DelayedEvent
            assertEquals("[Streaming] Stream Started", event.eventType)
            assertEquals(DelayedEvent.Kind.INSTANT, event.kind)
            assertEquals(1_000L, event.timestamp)
            assertEquals("insert-start-1", event.insertId)

            val props = event.eventProperties!!
            assertEquals("stream-1", props["[Streaming] Stream Session ID"])
            assertEquals("play-1", props["[Streaming] Play ID"])
            assertEquals("video", props["[Streaming] Media Type"])
            assertEquals("custom-id", props["[Streaming] Content ID"])
            assertEquals("Custom Title", props["[Streaming] Title"])
            assertEquals("on_demand", props["[Streaming] Delivery Mode"])
            assertEquals(15.0, props["[Streaming] Start Position Sec"])
            assertEquals(15.0, props["[Streaming] Position Sec"])
            assertEquals(60.0, props["[Streaming] Duration Sec"])
            assertFalse(props.containsKey("[Streaming] Is In Picture In Picture"))
            assertFalse(props.containsKey("[Streaming] Is In Background"))
            assertEquals("news", props["channel"])
            assertFalse(props.containsKey("[Streaming] Play Time Sec"))
            assertFalse(props.containsKey("[Streaming] Play Time Total Sec"))
            assertFalse(props.containsKey("[Streaming] Stop Reason"))
        }

        @Test
        fun `omits whitespace-only content ids from snapshot fallback`() {
            tracker.trackStreamStarted(
                options = PlayerContent(),
                snapshot = snapshot.copy(mediaId = "  "),
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-1",
                playId = "play-1",
                startTimeMillis = 15_000L,
                timestamp = 1_000L,
                insertId = "insert-blank-id",
            )

            assertFalse(events.first().eventProperties!!.containsKey("[Streaming] Content ID"))
        }

        @Test
        fun `trackStreamStopped sends Stream Stopped with progress and reason`() {
            tracker.trackStreamStopped(
                options = options,
                snapshot = snapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-1",
                playId = "play-1",
                startTimeMillis = 10_000L,
                playTimeMillis = 5_000L,
                playTimeTotalMillis = 12_000L,
                timestamp = 6_000L,
                insertId = "insert-stop-1",
                stopReason = StopReason.PAUSED,
            )

            assertEquals(1, events.size)
            val event = events.first() as DelayedEvent
            assertEquals("[Streaming] Stream Stopped", event.eventType)
            assertEquals(DelayedEvent.Kind.INSTANT, event.kind)
            assertEquals(6_000L, event.timestamp)
            assertEquals("insert-stop-1", event.insertId)

            val props = event.eventProperties!!
            assertEquals("stream-1", props["[Streaming] Stream Session ID"])
            assertEquals("play-1", props["[Streaming] Play ID"])
            assertEquals("video", props["[Streaming] Media Type"])
            assertEquals(15.0, props["[Streaming] Position Sec"])
            assertEquals(10.0, props["[Streaming] Start Position Sec"])
            assertEquals(5.0, props["[Streaming] Play Time Sec"])
            assertEquals(12.0, props["[Streaming] Play Time Total Sec"])
            assertEquals("paused", props["[Streaming] Stop Reason"])
            assertEquals(25.0, props["[Streaming] Percent Completed"])
            assertFalse(props.containsKey("[Streaming] Current Time"))
        }

        @Test
        fun `timeout is the only delayed stop`() {
            tracker.trackStreamStopped(
                options = options,
                snapshot = snapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-1",
                playId = "play-1",
                startTimeMillis = 0L,
                playTimeMillis = 5_000L,
                timestamp = 6_000L,
                insertId = "timeout-stop",
                stopReason = StopReason.TIMEOUT,
            )
            assertEquals(DelayedEvent.Kind.DELAYED, (events.last() as DelayedEvent).kind)
            assertEquals("timeout", events.last().eventProperties?.get("[Streaming] Stop Reason"))

            val instantReasons =
                listOf(
                    StopReason.PAUSED,
                    StopReason.ENDED,
                    StopReason.ERROR,
                    StopReason.UNTRACKED,
                    StopReason.CONTENT_CHANGED,
                )
            for (reason in instantReasons) {
                events.clear()
                tracker.trackStreamStopped(
                    options = options,
                    snapshot = snapshot,
                    mediaType = MediaType.VIDEO,
                    streamSessionId = "stream-1",
                    playId = "play-1",
                    startTimeMillis = 0L,
                    playTimeMillis = 5_000L,
                    timestamp = 6_000L,
                    insertId = "stop-${reason.value}",
                    stopReason = reason,
                )
                assertEquals(DelayedEvent.Kind.INSTANT, (events.last() as DelayedEvent).kind)
                assertEquals(reason.value, events.last().eventProperties?.get("[Streaming] Stop Reason"))
            }
        }

        @Test
        fun `audio streams use media type audio and on-demand delivery mode`() {
            tracker.trackStreamStarted(
                options = PlayerContent(),
                snapshot = snapshot,
                mediaType = MediaType.AUDIO,
                streamSessionId = "stream-audio",
                playId = "play-audio",
                startTimeMillis = 15_000L,
                timestamp = 2_000L,
                insertId = "audio-start",
            )
            tracker.trackStreamStopped(
                options = PlayerContent(),
                snapshot = snapshot,
                mediaType = MediaType.AUDIO,
                streamSessionId = "stream-audio",
                playId = "play-audio",
                startTimeMillis = 15_000L,
                playTimeMillis = 3_000L,
                timestamp = 5_000L,
                insertId = "audio-stop",
            )

            assertEquals(2, events.size)
            assertEquals("[Streaming] Stream Started", events[0].eventType)
            assertEquals("audio", events[0].eventProperties?.get("[Streaming] Media Type"))
            assertEquals("on_demand", events[0].eventProperties?.get("[Streaming] Delivery Mode"))
            assertEquals("audio-start", events[0].insertId)

            assertEquals("[Streaming] Stream Stopped", events[1].eventType)
            assertEquals("audio", events[1].eventProperties?.get("[Streaming] Media Type"))
            assertEquals("on_demand", events[1].eventProperties?.get("[Streaming] Delivery Mode"))
            assertEquals("audio-stop", events[1].insertId)
        }

        @Test
        fun `live stream omits duration and percent completed`() {
            val liveSnapshot = snapshot.copy(isLive = true)
            tracker.trackStreamStopped(
                options = PlayerContent(),
                snapshot = liveSnapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-live",
                playId = "play-live",
                startTimeMillis = 15_000L,
                playTimeMillis = 10_000L,
                timestamp = 10_000L,
                insertId = "stop-live",
            )

            val props = events.first().eventProperties!!
            assertEquals("live", props["[Streaming] Delivery Mode"])
            assertFalse(props.containsKey("[Streaming] Duration Sec"))
            assertFalse(props.containsKey("[Streaming] Percent Completed"))
        }

        @Test
        fun `unknown duration omits duration and percent completed`() {
            val unknownDurationSnapshot = snapshot.copy(durationMillis = C.TIME_UNSET)
            tracker.trackStreamStopped(
                options = PlayerContent(),
                snapshot = unknownDurationSnapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-unknown",
                playId = "play-unknown",
                startTimeMillis = 15_000L,
                playTimeMillis = 5_000L,
                timestamp = 5_000L,
                insertId = "stop-unknown",
            )

            val props = events.first().eventProperties!!
            assertFalse(props.containsKey("[Streaming] Duration Sec"))
            assertFalse(props.containsKey("[Streaming] Percent Completed"))
        }

        @Test
        fun `zero duration emits duration and zero percent completed`() {
            val zeroDurationSnapshot = snapshot.copy(durationMillis = 0L)
            tracker.trackStreamStopped(
                options = PlayerContent(),
                snapshot = zeroDurationSnapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-zero",
                playId = "play-zero",
                startTimeMillis = 0L,
                playTimeMillis = 0L,
                timestamp = 5_000L,
                insertId = "stop-zero",
                stopReason = StopReason.ENDED,
            )

            val props = events.first().eventProperties!!
            assertEquals(0.0, props["[Streaming] Duration Sec"])
            assertEquals(0.0, props["[Streaming] Percent Completed"])
        }

        @Test
        fun `times are seconds rounded to millisecond precision`() {
            tracker.trackStreamStopped(
                options = PlayerContent(),
                snapshot = snapshot.copy(positionMillis = 1_501L, durationMillis = 2_500L),
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-precision",
                playId = "play-precision",
                startTimeMillis = 1L,
                playTimeMillis = 1_501L,
                timestamp = 5_000L,
                insertId = "stop-precision",
                stopReason = StopReason.ENDED,
            )

            val props = events.first().eventProperties!!
            assertEquals(0.001, props["[Streaming] Start Position Sec"])
            assertEquals(1.501, props["[Streaming] Position Sec"])
            assertEquals(2.5, props["[Streaming] Duration Sec"])
            assertEquals(1.501, props["[Streaming] Play Time Sec"])
            assertEquals(1.501, props["[Streaming] Play Time Total Sec"])
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
                mediaItemIndex = 0,
            )
        private val options = PlayerContent(extraProperties = mapOf("ad_campaign" to "summer"))

        @BeforeEach
        fun enableAdEvents() {
            StreamTracker.adsEventsEnabled = true
        }

        @AfterEach
        fun disableAdEvents() {
            StreamTracker.adsEventsEnabled = false
        }

        @Test
        fun `ad methods are no-ops when adsEventsEnabled is false`() {
            StreamTracker.adsEventsEnabled = false

            tracker.trackAdStarted(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
                timestamp = 1_000L,
                insertId = "ad-start-1",
            )
            tracker.trackAdStopped(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
                playTimeMillis = 1_000L,
                status = AdCompletionStatus.ENDED,
                timestamp = 1_000L,
                insertId = "ad-stop-1",
            )
            tracker.trackAdSkipped(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
                timestamp = 1_000L,
                insertId = "ad-skip-1",
            )

            assertEquals(emptyList<BaseEvent>(), events)
        }

        @Test
        fun `trackAdStarted emits Ad Started with ad properties`() {
            tracker.trackAdStarted(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
                timestamp = 1_000L,
                insertId = "ad-start-1",
            )

            assertEquals(1, events.size)
            val event = events.first() as DelayedEvent
            assertEquals("[Streaming] Ad Started", event.eventType)
            assertEquals(DelayedEvent.Kind.INSTANT, event.kind)
            val props = event.eventProperties!!
            assertEquals("video-789:0:0:1", props["[Streaming] Ad ID"])
            assertEquals("video-789", props["[Streaming] Content ID"])
            assertEquals("stream-ad-1", props["[Streaming] Stream Session ID"])
            assertEquals(45.0, props["[Streaming] Ad Position Sec"])
            assertEquals(30.0, props["[Streaming] Ad Duration Sec"])
            assertEquals("summer", props["ad_campaign"])
        }

        @Test
        fun `ad_id includes media item index so playlist ads do not collide`() {
            assertEquals(
                "video-789:1:0:1",
                ad.copy(mediaItemIndex = 1).adId,
            )
        }

        @Test
        fun `trackAdStopped records completion status and duration`() {
            tracker.trackAdStopped(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
                playTimeMillis = 30_000L,
                status = AdCompletionStatus.ENDED,
                timestamp = 2_000L,
                insertId = "ad-stop-1",
            )

            val event = events.first() as DelayedEvent
            val props = event.eventProperties!!
            assertEquals("[Streaming] Ad Stopped", event.eventType)
            assertEquals(DelayedEvent.Kind.INSTANT, event.kind)
            assertEquals(30.0, props["[Streaming] Ad Play Time Sec"])
            assertEquals("ended", props["[Streaming] Ad Completion Status"])
            assertEquals(100.0, props["[Streaming] Ad Percent Completed"])
        }

        @Test
        fun `trackAdStopped records abandoned status when not completed`() {
            tracker.trackAdStopped(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
                playTimeMillis = 5_000L,
                status = AdCompletionStatus.ABANDONED,
                timestamp = 2_000L,
                insertId = "ad-stop-1",
            )

            val props = events.first().eventProperties!!
            assertEquals("abandoned", props["[Streaming] Ad Completion Status"])
            assertEquals(5.0, props["[Streaming] Ad Play Time Sec"])
        }

        @Test
        fun `trackAdStopped records skipped status`() {
            tracker.trackAdStopped(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
                playTimeMillis = 8_000L,
                status = AdCompletionStatus.SKIPPED,
                timestamp = 2_000L,
                insertId = "ad-stop-1",
            )

            assertEquals("skipped", events.first().eventProperties!!["[Streaming] Ad Completion Status"])
        }

        @Test
        fun `trackAdSkipped emits Ad Skipped`() {
            tracker.trackAdSkipped(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
                timestamp = 2_000L,
                insertId = "ad-skip-1",
            )

            assertEquals(1, events.size)
            val event = events.first() as DelayedEvent
            assertEquals("[Streaming] Ad Skipped", event.eventType)
            assertEquals(DelayedEvent.Kind.INSTANT, event.kind)
            assertEquals("video-789:0:0:1", event.eventProperties?.get("[Streaming] Ad ID"))
            assertEquals(10.0, event.eventProperties?.get("[Streaming] Skip Position Sec"))
        }

        @Test
        fun `timeout is the only delayed ad stop`() {
            tracker.trackAdStopped(
                options = options,
                ad = ad,
                streamSessionId = "stream-ad-1",
                playTimeMillis = 5_000L,
                status = AdCompletionStatus.TIMEOUT,
                timestamp = 2_000L,
                insertId = "ad-stop-timeout",
            )
            val timeout = events.last() as DelayedEvent
            assertEquals(DelayedEvent.Kind.DELAYED, timeout.kind)
            assertEquals("timeout", timeout.eventProperties?.get("[Streaming] Ad Completion Status"))
            assertEquals("ad-stop-timeout", timeout.insertId)

            val instantStatuses =
                listOf(
                    AdCompletionStatus.ENDED,
                    AdCompletionStatus.SKIPPED,
                    AdCompletionStatus.ABANDONED,
                )
            for (status in instantStatuses) {
                events.clear()
                tracker.trackAdStopped(
                    options = options,
                    ad = ad,
                    streamSessionId = "stream-ad-1",
                    playTimeMillis = 5_000L,
                    status = status,
                    timestamp = 2_000L,
                    insertId = "ad-stop-${status.value}",
                )
                val event = events.last() as DelayedEvent
                assertEquals(DelayedEvent.Kind.INSTANT, event.kind)
                assertEquals(status.value, event.eventProperties?.get("[Streaming] Ad Completion Status"))
            }
        }
    }

    @Nested
    inner class RoutedDelayedEvents {
        private val snapshot =
            PlayerMediaSnapshot(
                positionMillis = 15_000L,
                durationMillis = 60_000L,
                mediaType = MediaType.VIDEO,
            )
        private val routed = mutableListOf<DelayedEvent>()

        @BeforeEach
        fun routeOffTimeline() {
            routed.clear()
            every { amplitude.sessionId } returns 4_200L
            every { amplitude.timeline } returns Timeline().also { it.amplitude = amplitude }
            tracker.routeDelayedEvents { routed.add(it) }
        }

        @Test
        fun `stream events reach the sink instead of the timeline`() {
            tracker.trackStreamStopped(
                options = PlayerContent(),
                snapshot = snapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-1",
                playId = "play-1",
                startTimeMillis = 10_000L,
                playTimeMillis = 5_000L,
                timestamp = 6_000L,
                insertId = "insert-stop-1",
                stopReason = StopReason.UNTRACKED,
            )

            assertEquals(emptyList<BaseEvent>(), events)
            assertEquals(1, routed.size)
            assertEquals("[Streaming] Stream Stopped", routed.single().eventType)
            assertEquals("untracked", routed.single().eventProperties?.get("[Streaming] Stop Reason"))
        }

        @Test
        fun `routed events carry the session id the timeline would have assigned`() {
            tracker.trackStreamStarted(
                options = PlayerContent(),
                snapshot = snapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-1",
                playId = "play-1",
                startTimeMillis = 15_000L,
                timestamp = 1_000L,
                insertId = "insert-start-1",
            )

            assertEquals(4_200L, routed.single().sessionId)
        }

        @Test
        fun `ad events reach the delayed events sink`() {
            StreamTracker.adsEventsEnabled = true
            try {
                tracker.trackAdStarted(
                    options = PlayerContent(),
                    ad =
                        AdContext(
                            adGroupIndex = 0,
                            adIndexInAdGroup = 0,
                            positionMillis = 0L,
                            durationMillis = 30_000L,
                            contentPositionMillis = 0L,
                            contentId = "video-789",
                            mediaItemIndex = 0,
                        ),
                    streamSessionId = "stream-1",
                    timestamp = 1_000L,
                    insertId = "ad-start-1",
                )

                assertEquals(emptyList<BaseEvent>(), events)
                assertEquals(1, routed.size)
                assertEquals("[Streaming] Ad Started", routed.single().eventType)
                assertEquals(DelayedEvent.Kind.INSTANT, routed.single().kind)
            } finally {
                StreamTracker.adsEventsEnabled = false
            }
        }

        @Test
        fun `opted out users do not reach the sink`() {
            every { amplitude.optOut } returns true

            tracker.trackStreamStopped(
                options = PlayerContent(),
                snapshot = snapshot,
                mediaType = MediaType.VIDEO,
                streamSessionId = "stream-1",
                playId = "play-1",
                startTimeMillis = 10_000L,
                playTimeMillis = 5_000L,
                timestamp = 6_000L,
                insertId = "insert-stop-1",
                stopReason = StopReason.UNTRACKED,
            )

            assertEquals(emptyList<BaseEvent>(), events)
            assertEquals(emptyList<DelayedEvent>(), routed)
        }
    }
}
