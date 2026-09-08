package com.amplitude.android.streaming.internal.player

import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.internal.StreamTracker
import com.amplitude.android.streaming.internal.util.Time
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import com.amplitude.core.events.BaseEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy

@OptIn(AmplitudePreview::class, ExperimentalCoroutinesApi::class)
class PlayerBindingFactoryTest {
    @Test
    fun `getOrCreate stops bindings whose player was collected`() =
        runTest {
            val amplitude = mockk<Amplitude>(relaxed = true)
            val observers = mutableListOf<TestPlayerObserver>()
            val factory =
                PlayerBindingFactory(
                    playerObserverFactory =
                        PlayerObserverFactory { _, _ ->
                            TestPlayerObserver().also { observers.add(it) }
                        },
                    streamTracker = StreamTracker(amplitude),
                    heartbeatFactory = HeartbeatFactory(Time()),
                    time = Time(),
                    scope = backgroundScope,
                )
            val (abandonedBinding, playerReference) = factory.createAbandonedBinding()
            runCurrent()

            awaitCollected(playerReference)
            factory.getOrCreate(playerProxy()) { PlayerContent() }
            runCurrent()
            observers.first().emit(PlayerEvent.Playing)
            runCurrent()

            assertTrue(abandonedBinding.isOrphaned())
            verify(exactly = 0) { amplitude.track(any<BaseEvent>(), any(), any()) }
        }

    @Test
    fun `stops a binding when its player is collected without another getOrCreate`() =
        runTest {
            val events = mutableListOf<BaseEvent>()
            val amplitude =
                mockk<Amplitude>(relaxed = true).also { amplitude ->
                    every { amplitude.track(any<BaseEvent>(), any(), any()) } answers {
                        events.add(firstArg())
                        amplitude
                    }
                    every { amplitude.track(any<String>(), any(), any()) } answers {
                        events.add(
                            BaseEvent().apply {
                                eventType = firstArg()
                                eventProperties = secondArg<Map<String, Any?>?>()?.toMutableMap()
                            },
                        )
                        amplitude
                    }
                }
            val observers = mutableListOf<TestPlayerObserver>()
            val factory =
                PlayerBindingFactory(
                    playerObserverFactory =
                        PlayerObserverFactory { _, _ ->
                            TestPlayerObserver().also { observers.add(it) }
                        },
                    streamTracker = StreamTracker(amplitude),
                    heartbeatFactory = HeartbeatFactory(Time()),
                    time = Time(),
                    scope = this,
                )
            val playerReference = factory.createAbandonedBinding().second
            runCurrent()
            observers.first().emit(PlayerEvent.Playing)
            runCurrent()

            awaitCollected(playerReference)
            advanceTimeBy(1_000)
            runCurrent()

            assertTrue(events.any { it.eventType == "[Amplitude] Stream Started" })
            assertTrue(
                events.any {
                    it.eventType == "[Amplitude] Stream Stopped" &&
                        it.eventProperties?.get("stop_reason") == "unsubscribed"
                },
            )
        }

    private fun PlayerBindingFactory.createAbandonedBinding(): Pair<PlayerBinding, WeakReference<Player>> {
        val player = playerProxy()
        return getOrCreate(player) { PlayerContent() } to WeakReference(player)
    }

    private fun playerProxy(): Player =
        Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, _ ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        } as Player

    private fun awaitCollected(reference: WeakReference<*>) {
        repeat(100) {
            if (reference.get() == null) return
            System.gc()
            Thread.sleep(10)
        }
        fail<Unit>("Player was not garbage collected")
    }
}
