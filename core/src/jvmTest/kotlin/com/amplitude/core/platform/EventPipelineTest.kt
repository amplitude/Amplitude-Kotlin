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
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class EventPipelineTest {
    private lateinit var amplitude: Amplitude
    private lateinit var testScope: TestScope
    private lateinit var testDispatcher: TestDispatcher
    private val config = Configuration(
        apiKey = "API_KEY",
        flushIntervalMillis = 1,
        storageProvider = InMemoryStorageProvider(),
        loggerProvider = ConsoleLoggerProvider(),
        identifyInterceptStorageProvider = InMemoryStorageProvider(),
        identityStorageProvider = IMIdentityStorageProvider(),
    )

    @BeforeEach
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        amplitude = Amplitude(config, State(), testScope, testDispatcher, testDispatcher, testDispatcher, testDispatcher)
    }

    @Test
    fun `should not flush when put and offline`() =
        runTest(testDispatcher) {
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
    fun `should flush when put and online`() =
        runTest(testDispatcher) {
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
    fun `should flush when put and offline is disabled`() =
        runTest(testDispatcher) {
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
