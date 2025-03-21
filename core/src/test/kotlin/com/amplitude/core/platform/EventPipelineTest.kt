package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.State
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class EventPipelineTest {
    private val config = Configuration(
        apiKey = "API_KEY",
        flushIntervalMillis = 1,
        storageProvider = InMemoryStorageProvider(),
        loggerProvider = ConsoleLoggerProvider(),
        identifyInterceptStorageProvider = InMemoryStorageProvider(),
        identityStorageProvider = IMIdentityStorageProvider(),
    )
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val amplitude: Amplitude = Amplitude(
        configuration = config,
        store = State(),
        amplitudeScope = testScope,
        amplitudeDispatcher = testDispatcher,
        networkIODispatcher = testDispatcher,
        storageIODispatcher = testDispatcher,
        retryDispatcher = testDispatcher
    )

    @Test
    fun `should not flush when put and offline`() = runTest(testDispatcher) {
        amplitude.isBuilt.await()
        amplitude.configuration.offline = true
        val eventPipeline = spyk(EventPipeline(amplitude))
        val event = BaseEvent().apply { eventType = "test_event" }

        eventPipeline.start()
        eventPipeline.put(event)
        advanceUntilIdle()

        verify(exactly = 0) { eventPipeline.flush() }
    }

    @Test
    fun `should flush when put and online`() = runTest(testDispatcher) {
        amplitude.isBuilt.await()
        amplitude.configuration.offline = false
        val eventPipeline = spyk(EventPipeline(amplitude))
        val event = BaseEvent().apply { eventType = "test_event" }

        eventPipeline.start()
        eventPipeline.put(event)
        advanceUntilIdle()

        verify(exactly = 1) { eventPipeline.flush() }
    }

    @Test
    fun `should flush when put and offline is disabled`() = runTest(testDispatcher) {
        amplitude.isBuilt.await()
        amplitude.configuration.offline = null
        val eventPipeline = spyk(EventPipeline(amplitude))
        val event = BaseEvent().apply { eventType = "test_event" }

        eventPipeline.start()
        eventPipeline.put(event)
        advanceUntilIdle()

        verify(exactly = 1) { eventPipeline.flush() }
    }
}
