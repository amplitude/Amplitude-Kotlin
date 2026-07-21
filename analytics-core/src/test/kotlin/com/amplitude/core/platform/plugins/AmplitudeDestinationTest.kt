package com.amplitude.core.platform.plugins

import com.amplitude.core.Amplitude
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utils.FakeAmplitude
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class AmplitudeDestinationTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var amplitude: Amplitude

    @BeforeEach
    fun setup() {
        amplitude =
            FakeAmplitude(
                testDispatcher = testDispatcher,
                amplitudeScope = TestScope(testDispatcher),
            )
        mockkConstructor(EventPipeline::class)
        every { anyConstructed<EventPipeline>().start() } returns Unit
        every { anyConstructed<EventPipeline>().stop() } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkConstructor(EventPipeline::class)
    }

    @Test
    fun `teardown stops the pipeline`() =
        runTest(testDispatcher) {
            amplitude.isBuilt.await()

            val destination = AmplitudeDestination()
            destination.setup(amplitude)

            destination.teardown()

            verify { anyConstructed<EventPipeline>().stop() }
        }

    @Test
    fun `teardown before setup does not crash`() {
        val destination = AmplitudeDestination()
        destination.teardown()
    }
}
