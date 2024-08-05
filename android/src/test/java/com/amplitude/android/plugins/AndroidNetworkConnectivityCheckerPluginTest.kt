package com.amplitude.android.plugins

import android.app.Application
import com.amplitude.android.Configuration
import com.amplitude.core.Amplitude
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

class AndroidNetworkConnectivityCheckerPluginTest {

    private lateinit var amplitude: Amplitude
    private lateinit var plugin: AndroidNetworkConnectivityCheckerPlugin

    private val context = mockk<Application>(relaxed = true)

    @Before
    fun setup() {
        amplitude = Amplitude(
            Configuration(
                apiKey = "api-key",
                context = context,
                storageProvider = InMemoryStorageProvider(),
                loggerProvider = ConsoleLoggerProvider(),
                identifyInterceptStorageProvider = InMemoryStorageProvider(),
                identityStorageProvider = IMIdentityStorageProvider(),
                autocapture = setOf()
            )
        )
        plugin = AndroidNetworkConnectivityCheckerPlugin()
    }

    @Test
    fun `should set up correctly by default`() {
        // amplitude.configuration.offline defaults to false
        plugin.setup(amplitude)
        assertEquals(amplitude, plugin.amplitude)
        assertNotNull(plugin.networkConnectivityChecker)
        // Unit tests are run on JVM so default to online
        assertEquals(false, amplitude.configuration.offline)
        assertNotNull(plugin.networkListener)
    }

    @Test
    fun `should teardown correctly`() {
        plugin.setup(amplitude)
        assertNotNull(plugin.networkListener)
        plugin.networkListener?.let { networkListener ->
            spyk(networkListener)
            plugin.teardown()
            verify { networkListener.stopListening() }
        }
    }
}
