package com.amplitude.android

import android.app.Application
import android.content.Context
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityConfiguration
import com.amplitude.id.IdentityContainer
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AmplitudeTest {
    private var context: Context? = null
    private var amplitude: Amplitude? = null

    @BeforeEach
    fun setUp() {
        context = mockk<Application>(relaxed = true)
        mockkStatic(AndroidLifecyclePlugin::class)
        val configuration = IdentityConfiguration(
            "testInstance",
            identityStorageProvider = IMIdentityStorageProvider()
        )
        IdentityContainer.getInstance(configuration)
        amplitude = Amplitude(
            Configuration(
                apiKey = "api-key",
                context = context!!,
                instanceName = "testInstance",
                storageProvider = InMemoryStorageProvider()
            )
        )
    }

    @Test
    fun amplitude_reset_wipesUserIdDeviceId() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // inject the amplitudeDispatcher field with reflection, as the field is val (read-only)
        val amplitudeDispatcherField = com.amplitude.core.Amplitude::class.java.getDeclaredField("amplitudeDispatcher")
        amplitudeDispatcherField.isAccessible = true
        amplitudeDispatcherField.set(amplitude, dispatcher)

        amplitude?.setUserId("test user")
        amplitude?.setDeviceId("test device")
        advanceUntilIdle()
        Assertions.assertEquals("test user", amplitude?.store?.userId)
        Assertions.assertEquals("test device", amplitude?.store?.deviceId)

        amplitude?.reset()
        advanceUntilIdle()
        Assertions.assertNull(amplitude?.store?.userId)
        Assertions.assertNotEquals("test device", amplitude?.store?.deviceId)
    }
}
