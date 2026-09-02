package com.amplitude.android.plugins

import com.amplitude.core.Amplitude
import com.amplitude.core.platform.Plugin
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OptionalClasspathPluginsTest {
    @Test
    fun `install is a no-op when streaming analytics is not on the classpath`() {
        val amplitude = mockk<Amplitude>(relaxed = true)
        OptionalClasspathPlugins.install(amplitude)
        verify(exactly = 0) { amplitude.add(any<Plugin>()) }
    }
}
