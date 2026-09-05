package com.amplitude.android.streaming.internal.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.amplitude.android.streaming.internal.MediaType
import com.google.common.collect.ImmutableList
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Media3PlayerObserverTest {
    @Test
    fun `should add the listener once while subscribers are present and remove it when they leave`() =
        runTest {
            val player = mockk<Player>(relaxed = true)
            val observer =
                Media3PlayerObserver(
                    player = player,
                    scope = backgroundScope,
                    playerDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            val first = launch { observer.eventFlow.collect {} }
            runCurrent()
            verify(exactly = 1) { player.addListener(observer) }

            val second = launch { observer.eventFlow.collect {} }
            runCurrent()
            verify(exactly = 1) { player.addListener(observer) }

            first.cancel()
            runCurrent()
            verify(exactly = 0) { player.removeListener(observer) }

            second.cancel()
            runCurrent()
            verify(exactly = 1) { player.removeListener(observer) }
        }

    @Test
    fun `should not emit Paused when playback ends`() =
        runTest {
            val player = mockk<Player>(relaxed = true)
            every { player.playbackState } returns Player.STATE_ENDED
            val (observer, events) = observerCollectingEvents(player)

            observer.onPlaybackStateChanged(Player.STATE_ENDED)
            observer.onIsPlayingChanged(false)
            runCurrent()

            assertTrue(events.any { it is PlayerEvent.Ended })
            assertTrue(events.none { it is PlayerEvent.Paused })
        }

    @Test
    fun `should emit Paused when playback stops in STATE_READY`() =
        runTest {
            val player = mockk<Player>(relaxed = true)
            every { player.playbackState } returns Player.STATE_READY
            val (observer, events) = observerCollectingEvents(player)

            observer.onIsPlayingChanged(false)
            runCurrent()

            assertTrue(events.any { it is PlayerEvent.Paused })
        }

    @Test
    fun `should snapshot video mediaType when a video track is present`() =
        runTest {
            val observer =
                Media3PlayerObserver(
                    player = playerWithTrackType(C.TRACK_TYPE_VIDEO),
                    scope = backgroundScope,
                    playerDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            assertEquals(MediaType.VIDEO, observer.snapshot().mediaType)
        }

    @Test
    fun `should snapshot audio mediaType when only an audio track is present`() =
        runTest {
            val observer =
                Media3PlayerObserver(
                    player = playerWithTrackType(C.TRACK_TYPE_AUDIO),
                    scope = backgroundScope,
                    playerDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            assertEquals(MediaType.AUDIO, observer.snapshot().mediaType)
        }

    @Test
    fun `should emit AdStarted on attach when an ad is already playing`() =
        runTest {
            val player = playingAdPlayer(adGroupIndex = 0, adIndexInAdGroup = 2)
            val (_, events) = observerCollectingEvents(player)

            val started = events.filterIsInstance<PlayerEvent.AdStarted>()
            assertEquals(1, started.size)
            assertEquals(2, started.first().ad.adIndexInAdGroup)
        }

    @Test
    fun `should not skip an ad that ended while detached`() =
        runTest {
            val player = playingAdPlayer(adGroupIndex = 0, adIndexInAdGroup = 0)
            val events = mutableListOf<PlayerEvent>()
            val observer =
                Media3PlayerObserver(
                    player = player,
                    scope = backgroundScope,
                    playerDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
            val first =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    observer.eventFlow.collect { events.add(it) }
                }
            runCurrent()
            assertEquals(1, events.filterIsInstance<PlayerEvent.AdStarted>().size)

            first.cancel()
            runCurrent()
            events.clear()
            every { player.isPlayingAd } returns false

            val second =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    observer.eventFlow.collect { events.add(it) }
                }
            runCurrent()
            assertTrue(events.none { it is PlayerEvent.AdSkipped || it is PlayerEvent.AdStopped })
            second.cancel()
            runCurrent()
        }

    @Test
    fun `should start each ad in a pod while isPlayingAd stays true`() =
        runTest {
            val player = playingAdPlayer(adGroupIndex = 0, adIndexInAdGroup = 0)
            val (observer, events) = observerCollectingEvents(player)

            observer.detectAdTransition()
            every { player.currentAdIndexInAdGroup } returns 1
            observer.detectAdTransition()
            runCurrent()

            val started = events.filterIsInstance<PlayerEvent.AdStarted>()
            assertEquals(listOf(0, 1), started.map { it.ad.adIndexInAdGroup })
            assertTrue(events.any { it is PlayerEvent.AdSkipped })
        }

    @Test
    fun `should complete the previous ad on auto-transition to the next ad`() =
        runTest {
            val player = playingAdPlayer(adGroupIndex = 0, adIndexInAdGroup = 0)
            val (observer, events) = observerCollectingEvents(player)

            observer.detectAdTransition()
            runCurrent()
            observer.onPositionDiscontinuity(
                oldPosition = adPosition(adGroupIndex = 0, adIndexInAdGroup = 0, positionMs = 1_500L),
                newPosition = adPosition(adGroupIndex = 0, adIndexInAdGroup = 1, positionMs = 0L),
                reason = Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
            )
            every { player.currentAdIndexInAdGroup } returns 1
            observer.detectAdTransition()
            runCurrent()

            val stopped = events.filterIsInstance<PlayerEvent.AdStopped>()
            assertEquals(1, stopped.size)
            assertEquals(0, stopped.first().ad.adIndexInAdGroup)
            assertEquals(1_500L, stopped.first().ad.positionMillis)
            assertTrue(stopped.first().completed)
            assertEquals(
                listOf(0, 1),
                events.filterIsInstance<PlayerEvent.AdStarted>().map { it.ad.adIndexInAdGroup },
            )
        }

    @Test
    fun `should treat ads with the same indices on different media items as distinct`() =
        runTest {
            val player = playingAdPlayer(adGroupIndex = 0, adIndexInAdGroup = 0, mediaId = "item-a")
            val (observer, events) = observerCollectingEvents(player)

            observer.detectAdTransition()
            every { player.currentMediaItem } returns mediaItem("item-b")
            observer.detectAdTransition()
            runCurrent()

            assertEquals(
                listOf("item-a", "item-b"),
                events.filterIsInstance<PlayerEvent.AdStarted>().map { it.ad.contentId },
            )
            assertTrue(events.any { it is PlayerEvent.AdSkipped })
        }

    @Test
    fun `should complete the previous ad on auto-transition to the next media item's ad`() =
        runTest {
            val player = playingAdPlayer(adGroupIndex = 0, adIndexInAdGroup = 0, mediaId = "item-a")
            val (observer, events) = observerCollectingEvents(player)

            observer.detectAdTransition()
            runCurrent()
            observer.onPositionDiscontinuity(
                oldPosition =
                    adPosition(
                        adGroupIndex = 0,
                        adIndexInAdGroup = 0,
                        positionMs = 2_000L,
                        mediaItemIndex = 0,
                        mediaItem = mediaItem("item-a"),
                    ),
                newPosition =
                    adPosition(
                        adGroupIndex = 0,
                        adIndexInAdGroup = 0,
                        positionMs = 0L,
                        mediaItemIndex = 1,
                        mediaItem = mediaItem("item-b"),
                    ),
                reason = Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
            )
            every { player.currentMediaItem } returns mediaItem("item-b")
            observer.detectAdTransition()
            runCurrent()

            val stopped = events.filterIsInstance<PlayerEvent.AdStopped>()
            assertEquals(1, stopped.size)
            assertEquals("item-a", stopped.first().ad.contentId)
            assertEquals(2_000L, stopped.first().ad.positionMillis)
            assertTrue(stopped.first().completed)
            assertEquals(
                listOf("item-a", "item-b"),
                events.filterIsInstance<PlayerEvent.AdStarted>().map { it.ad.contentId },
            )
        }

    @Test
    fun `should complete an active ad when playback ends`() =
        runTest {
            val player = playingAdPlayer(adGroupIndex = 0, adIndexInAdGroup = 0)
            every { player.currentPosition } returns 15_000L
            val (observer, events) = observerCollectingEvents(player)

            observer.onPlaybackStateChanged(Player.STATE_ENDED)
            every { player.playbackState } returns Player.STATE_ENDED
            observer.detectAdTransition()
            runCurrent()

            val stopped = events.filterIsInstance<PlayerEvent.AdStopped>()
            assertEquals(1, stopped.size)
            assertTrue(stopped.first().completed)
            assertEquals(15_000L, stopped.first().ad.positionMillis)
            assertTrue(events.any { it is PlayerEvent.Ended })
            assertTrue(events.none { it is PlayerEvent.AdSkipped })
            assertEquals(1, events.filterIsInstance<PlayerEvent.AdStarted>().size)
        }

    private fun TestScope.observerCollectingEvents(
        player: Player,
    ): Pair<Media3PlayerObserver, MutableList<PlayerEvent>> {
        val events = mutableListOf<PlayerEvent>()
        val observer =
            Media3PlayerObserver(
                player = player,
                scope = backgroundScope,
                playerDispatcher = UnconfinedTestDispatcher(testScheduler),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.eventFlow.collect { events.add(it) }
        }
        runCurrent()
        return observer to events
    }
}

private fun playerWithTrackType(trackType: Int): Player {
    val group = mockk<Tracks.Group>()
    every { group.type } returns trackType
    val tracks = mockk<Tracks>()
    every { tracks.groups } returns ImmutableList.of(group)
    val player = mockk<Player>(relaxed = true)
    every { player.currentTracks } returns tracks
    return player
}

private fun playingAdPlayer(
    adGroupIndex: Int,
    adIndexInAdGroup: Int,
    mediaId: String? = null,
): Player {
    val player = mockk<Player>(relaxed = true)
    every { player.isPlayingAd } returns true
    every { player.currentAdGroupIndex } returns adGroupIndex
    every { player.currentAdIndexInAdGroup } returns adIndexInAdGroup
    every { player.currentPosition } returns 0L
    every { player.duration } returns 15_000L
    every { player.contentPosition } returns 30_000L
    every { player.currentMediaItem } returns mediaId?.let { mediaItem(it) }
    return player
}

private fun mediaItem(mediaId: String): MediaItem =
    MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri("https://example.com/$mediaId")
        .build()

private fun adPosition(
    adGroupIndex: Int,
    adIndexInAdGroup: Int,
    positionMs: Long,
    mediaItemIndex: Int = 0,
    mediaItem: MediaItem? = null,
): Player.PositionInfo =
    Player.PositionInfo(
        /* windowUid= */ null,
        mediaItemIndex,
        mediaItem,
        /* periodUid= */ null,
        /* periodIndex= */ 0,
        positionMs,
        positionMs,
        adGroupIndex,
        adIndexInAdGroup,
    )
