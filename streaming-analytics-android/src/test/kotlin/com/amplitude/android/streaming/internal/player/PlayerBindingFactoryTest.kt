package com.amplitude.android.streaming.internal.player

import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.internal.StreamTracker
import com.amplitude.android.streaming.internal.util.Time
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
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
            val factory = playerBindingFactory(playerDispatcher)

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
            val factory = playerBindingFactory(playerDispatcher)

            val binding = factory.getOrCreate(player) { PlayerContent() }

            assertSame(binding, factory.getOrCreate(player) { PlayerContent() })

            playerDispatcher.close()
            playerExecutor.shutdown()
        }

    private fun playerBindingFactory(playerDispatcher: CoroutineDispatcher) =
        PlayerBindingFactory(
            playerObserverFactory = PlayerObserverFactory { _, _, _ -> TestPlayerObserver() },
            streamTracker = StreamTracker(mockk<Amplitude>(relaxed = true)),
            time = Time(),
            scope = CoroutineScope(Dispatchers.IO),
            playerDispatcherFactory =
                mockk<PlayerDispatcherFactory>().also {
                    every { it.create(any()) } returns playerDispatcher
                },
        )
}
