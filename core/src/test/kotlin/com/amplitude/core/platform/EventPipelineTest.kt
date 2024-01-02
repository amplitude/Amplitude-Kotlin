package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class EventPipelineTest {
    private lateinit var amplitude: Amplitude
    private lateinit var networkConnectivityChecker: NetworkConnectivityChecker
    private val config = Configuration(
        apiKey = "API_KEY",
        flushIntervalMillis = 5,
        storageProvider = InMemoryStorageProvider(),
        loggerProvider = ConsoleLoggerProvider(),
        identifyInterceptStorageProvider = InMemoryStorageProvider(),
        identityStorageProvider = IMIdentityStorageProvider()
    )

    @BeforeEach
    fun setup() {
        amplitude = Amplitude(config)
        networkConnectivityChecker = mockk<NetworkConnectivityChecker>(relaxed = true)
    }

    @Test
    fun `should not flush when put and offline`() =
        runBlocking {
            coEvery { networkConnectivityChecker.isConnected() } returns false
            val eventPipeline = spyk(EventPipeline(amplitude, networkConnectivityChecker))
            val event = BaseEvent().apply { eventType = "test_event" }

            eventPipeline.start()
            eventPipeline.put(event)
            delay(6)

            verify(exactly = 0) { eventPipeline.flush() }
        }

    @Test
    fun `should flush when put and online`() =
        runBlocking {
            coEvery { networkConnectivityChecker.isConnected() } returns true
            val eventPipeline = spyk(EventPipeline(amplitude, networkConnectivityChecker))
            val event = BaseEvent().apply { eventType = "test_event" }

            eventPipeline.start()
            eventPipeline.put(event)
            delay(6)

            verify(exactly = 1) { eventPipeline.flush() }
        }

    @Test
    fun `should flush when back to online and an event is tracked`() =
        runBlocking {
            coEvery { networkConnectivityChecker.isConnected() } returns false
            val eventPipeline = spyk(EventPipeline(amplitude, networkConnectivityChecker))
            val event1 = BaseEvent().apply { eventType = "test_event1" }
            val event2 = BaseEvent().apply { eventType = "test_event2" }

            eventPipeline.start()
            eventPipeline.put(event1)
            delay(6)

            coEvery { networkConnectivityChecker.isConnected() } returns true
            eventPipeline.put(event2)
            delay(6)

            verify(exactly = 1) { eventPipeline.flush() }
        }
}
