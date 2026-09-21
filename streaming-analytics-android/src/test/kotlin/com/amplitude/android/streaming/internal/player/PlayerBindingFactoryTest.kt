package com.amplitude.android.streaming.internal.player

import android.os.Looper
import androidx.media3.common.Player
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.concurrent.CyclicBarrier
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
                factory.getOrCreate(player)
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

            val binding = factory.getOrCreate(player)

            assertSame(binding, factory.getOrCreate(player))

            playerDispatcher.close()
            playerExecutor.shutdown()
        }

    @Test
    fun `detach stops the binding and does not reuse it`() =
        runTest {
            val events = mutableListOf<BaseEvent>()
            val amplitude =
                mockk<Amplitude>(relaxed = true).also { amplitude ->
                    every { amplitude.track(any<BaseEvent>(), any(), any()) } answers {
                        events.add(firstArg())
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
            val player = mockk<Player>(relaxed = true)
            val binding = factory.getOrCreate(player)
            try {
                runCurrent()
                observers.single().emit(PlayerEvent.Playing)
                runCurrent()

                factory.detach(player)

                assertTrue(
                    events.any {
                        it.eventType == "[Amplitude] Stream Stopped" &&
                            it.eventProperties?.get("stop_reason") == "untracked"
                    },
                )
                assertNotSame(binding, factory.getOrCreate(player))
            } finally {
                factory.detachAll()
                runCurrent()
            }
        }

    @Test
    fun `detach is a no-op when the player is not tracked`() =
        runTest {
            val amplitude = mockk<Amplitude>(relaxed = true)
            val factory = playerBindingFactory(streamTracker = StreamTracker(amplitude))
            val player = mockk<Player>(relaxed = true)

            factory.detach(player)

            verify(exactly = 0) { amplitude.track(any<BaseEvent>(), any(), any()) }
        }

    @Test
    fun `getOrCreate after detach does not overlap the previous session`() =
        runTest {
            val events = mutableListOf<BaseEvent>()
            val amplitude =
                mockk<Amplitude>(relaxed = true).also { amplitude ->
                    every { amplitude.track(any<BaseEvent>(), any(), any()) } answers {
                        events.add(firstArg())
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
            val player = mockk<Player>(relaxed = true)
            try {
                factory.getOrCreate(player)
                runCurrent()
                observers.single().emit(PlayerEvent.Playing)
                runCurrent()

                factory.detach(player)
                factory.getOrCreate(player)
                runCurrent()
                observers.last().emit(PlayerEvent.Playing)
                runCurrent()

                val started = events.filter { it.eventType == "[Amplitude] Stream Started" }
                val untrackedStops =
                    events.filter {
                        it.eventType == "[Amplitude] Stream Stopped" &&
                            it.eventProperties?.get("stop_reason") == "untracked"
                    }
                assertEquals(2, started.size)
                assertEquals(1, untrackedStops.size)
                assertTrue(events.indexOf(untrackedStops.single()) < events.indexOf(started.last()))
            } finally {
                factory.detachAll()
            }
        }

    @Test
    fun `detachAll tracks stream stopped before returning`() =
        runTest {
            val events = mutableListOf<BaseEvent>()
            val amplitude =
                mockk<Amplitude>(relaxed = true).also { amplitude ->
                    every { amplitude.track(any<BaseEvent>(), any(), any()) } answers {
                        events.add(firstArg())
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
            val player = mockk<Player>(relaxed = true)
            factory.getOrCreate(player)
            runCurrent()
            observers.single().emit(PlayerEvent.Playing)
            runCurrent()

            factory.detachAll()

            assertTrue(
                events.any {
                    it.eventType == "[Amplitude] Stream Stopped" &&
                        it.eventProperties?.get("stop_reason") == "untracked"
                },
            )
        }

    @Test
    fun `detach finishes the stop even when its caller is cancelled`() =
        runTest {
            val events = mutableListOf<BaseEvent>()
            val amplitude =
                mockk<Amplitude>(relaxed = true).also { amplitude ->
                    every { amplitude.track(any<BaseEvent>(), any(), any()) } answers {
                        events.add(firstArg())
                        amplitude
                    }
                }
            val observers = mutableListOf<TestPlayerObserver>()
            val factory =
                playerBindingFactory(
                    playerDispatcher = StandardTestDispatcher(testScheduler),
                    streamTracker = StreamTracker(amplitude),
                    playerObserverFactory =
                        PlayerObserverFactory { _, _, _ ->
                            TestPlayerObserver().also { observers.add(it) }
                        },
                )
            val player = mockk<Player>(relaxed = true)
            factory.getOrCreate(player)
            runCurrent()
            observers.single().emit(PlayerEvent.Playing)
            runCurrent()

            val caller = launch { factory.detach(player) }
            runCurrent()
            caller.cancel()
            runCurrent()

            assertTrue(
                events.any {
                    it.eventType == "[Amplitude] Stream Stopped" &&
                        it.eventProperties?.get("stop_reason") == "untracked"
                },
            )
        }

    @Test
    fun `getOrCreate after detachAll does not start a new binding`() =
        runTest {
            val events = mutableListOf<BaseEvent>()
            val amplitude =
                mockk<Amplitude>(relaxed = true).also { amplitude ->
                    every { amplitude.track(any<BaseEvent>(), any(), any()) } answers {
                        events.add(firstArg())
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
            val player = mockk<Player>(relaxed = true)
            factory.getOrCreate(player)
            runCurrent()
            observers.single().emit(PlayerEvent.Playing)
            runCurrent()

            factory.detachAll()
            val started = events.count { it.eventType == "[Amplitude] Stream Started" }

            assertNull(factory.getOrCreate(player))
            runCurrent()
            assertEquals(started, events.count { it.eventType == "[Amplitude] Stream Started" })
            assertEquals(1, observers.size)
        }

    @Test
    fun `should not start a second collector when getOrCreate races`() =
        runTest {
            val events = mutableListOf<BaseEvent>()
            val amplitude =
                mockk<Amplitude>(relaxed = true).also { amplitude ->
                    every { amplitude.track(any<BaseEvent>(), any(), any()) } answers {
                        events.add(firstArg())
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
            val player = mockk<Player>(relaxed = true)
            val callers = 8
            val barrier = CyclicBarrier(callers)
            val threads =
                List(callers) {
                    Thread {
                        barrier.await()
                        runBlocking { factory.getOrCreate(player) }
                    }
                }
            try {
                threads.forEach { it.start() }
                threads.forEach { it.join() }
                runCurrent()

                assertEquals(1, observers.size)
                observers.single().emit(PlayerEvent.Playing)
                runCurrent()

                assertEquals(1, events.count { it.eventType == "[Amplitude] Stream Started" })
            } finally {
                factory.detachAll()
                runCurrent()
            }
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
            factory.getOrCreate(playerProxy())
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
            assertEquals(0, factory.trackedCount())
        }

    @Test
    fun `should keep paused stop reason when a paused player is collected`() =
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
            observers.first().emit(PlayerEvent.Paused)
            runCurrent()

            val paused =
                events.filter { it.eventType == "[Amplitude] Stream Stopped" }
            assertEquals("paused", paused.last().eventProperties?.get("stop_reason"))
            val insertId = paused.last().insertId

            awaitCollected(playerReference)
            advanceTimeBy(1_000)
            runCurrent()

            val samePlay =
                events.filter {
                    it.eventType == "[Amplitude] Stream Stopped" && it.insertId == insertId
                }
            assertEquals("paused", samePlay.last().eventProperties?.get("stop_reason"))
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

    private suspend fun PlayerBindingFactory.createAbandonedBinding(): Pair<PlayerBinding, WeakReference<Player>> {
        val player = playerProxy()
        val binding = checkNotNull(getOrCreate(player))
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
