package com.amplitude.android.streaming.internal.player

import android.os.Looper
import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.internal.StreamTracker
import com.amplitude.android.streaming.internal.util.Time
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import com.amplitude.core.events.BaseEvent
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.concurrent.Executors

@OptIn(AmplitudePreview::class, ExperimentalCoroutinesApi::class)
class PlayerBindingFactoryTest {
    @Test
    fun `should read the player on the player dispatcher`() =
        runTest {
            val playerExecutor = Executors.newSingleThreadExecutor()
            val playerDispatcher = playerExecutor.asCoroutineDispatcher()
            var readThread: Thread? = null
            val player = mockk<Player>(relaxed = true)
            every { player.currentMediaItem } answers {
                readThread = Thread.currentThread()
                null
            }
            val factory = playerBindingFactory(playerDispatcher, scope = CoroutineScope(Dispatchers.IO))

            val callerThread = Thread.currentThread()
            withContext(Dispatchers.IO) {
                factory.getOrCreate(player) { PlayerContent() }
            }
            playerExecutor.submit { }.get()

            assertNotNull(readThread)
            assertNotSame(callerThread, readThread)

            playerDispatcher.close()
            playerExecutor.shutdown()
        }

    @Test
    fun `should reuse the binding already registered for a player`() =
        runTest {
            val playerExecutor = Executors.newSingleThreadExecutor()
            val playerDispatcher = playerExecutor.asCoroutineDispatcher()
            val player = mockk<Player>(relaxed = true)
            val factory = playerBindingFactory(playerDispatcher, scope = CoroutineScope(Dispatchers.IO))

            val binding = factory.getOrCreate(player) { PlayerContent() }

            assertSame(binding, factory.getOrCreate(player) { PlayerContent() })

            playerDispatcher.close()
            playerExecutor.shutdown()
        }

    @Test
    fun `getOrCreate stops bindings whose player was collected`() =
        runTest {
            val amplitude = mockk<Amplitude>(relaxed = true)
            val observers = mutableListOf<TestPlayerObserver>()
            val factory =
                playerBindingFactory(
                    streamTracker = StreamTracker(amplitude),
                    scope = backgroundScope,
                    playerObserverFactory =
                        PlayerObserverFactory { _, _, _ ->
                            TestPlayerObserver().also { observers.add(it) }
                        },
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
                playerBindingFactory(
                    streamTracker = StreamTracker(amplitude),
                    playerObserverFactory =
                        PlayerObserverFactory { _, _, _ ->
                            TestPlayerObserver().also { observers.add(it) }
                        },
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
                        it.eventProperties?.get("stop_reason") == "untracked"
                },
            )
        }

    private lateinit var dispatcherFactory: PlayerDispatcherFactory

    private fun TestScope.playerBindingFactory(
        playerDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
        scope: CoroutineScope = this,
        streamTracker: StreamTracker = StreamTracker(mockk<Amplitude>(relaxed = true)),
        playerObserverFactory: PlayerObserverFactory =
            PlayerObserverFactory { _, _, _ -> TestPlayerObserver() },
    ): PlayerBindingFactory {
        dispatcherFactory =
            mockk<PlayerDispatcherFactory>().also {
                every { it.create(any()) } returns playerDispatcher
            }
        return PlayerBindingFactory(
            playerObserverFactory = playerObserverFactory,
            streamTracker = streamTracker,
            heartbeatFactory = HeartbeatFactory(time = Time()),
            time = Time(),
            scope = scope,
            playerDispatcherFactory = dispatcherFactory,
        )
    }

    private fun PlayerBindingFactory.createAbandonedBinding(): Pair<PlayerBinding, WeakReference<Player>> {
        val player = playerProxy()
        val binding = getOrCreate(player) { PlayerContent() }
        // MockK records create(player); drop that so the proxy can be collected.
        clearMocks(dispatcherFactory, answers = false, recordedCalls = true)
        return binding to WeakReference(player)
    }

    /**
     * A [Proxy] rather than a mock, because MockK keeps strong references to its mocks and these
     * tests assert that the player becomes unreachable.
     */
    private fun playerProxy(): Player {
        val looper = mockk<Looper>(relaxed = true)
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, _ ->
            when {
                method.name == "getApplicationLooper" -> looper
                method.returnType == java.lang.Boolean.TYPE -> false
                method.returnType == java.lang.Integer.TYPE -> 0
                method.returnType == java.lang.Long.TYPE -> 0L
                else -> null
            }
        } as Player
    }

    private fun awaitCollected(reference: WeakReference<*>) {
        repeat(100) {
            if (reference.get() == null) return
            System.gc()
            Thread.sleep(10)
        }
        fail<Unit>("Player was not garbage collected")
    }
}
