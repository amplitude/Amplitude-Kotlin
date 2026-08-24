package com.amplitude.android

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class AmplitudeRegistryTest {
    @Test
    fun `failed activation detaches watchers without retiring`() {
        val configuration = mockk<Configuration>(relaxed = true)
        every { configuration.instanceName } returns "failed-crash-detach"
        val instance = mockk<Amplitude>(relaxed = true)
        every { instance.configuration } returns configuration
        every { instance.startAsActiveInstance() } throws IllegalStateException("cannot register")

        assertFailsWith<IllegalStateException> {
            AmplitudeRegistry.activate(instance)
        }

        verify(exactly = 1) { instance.markRetired() }
        verify(exactly = 1) { instance.detachWatchers() }
        verify(exactly = 0) { instance.retire() }
    }
}
