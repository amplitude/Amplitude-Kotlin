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
    fun amplitude_reset_wipesUserIdDeviceId() {
        amplitude?.setUserId("test user")
        amplitude?.setDeviceId("test device")
        println("=======================")
        println(amplitude?.store?.userId)
        println(amplitude?.store?.deviceId)
//        Assertions.assertEquals("test user", amplitude?.store?.userId)
//        Assertions.assertEquals("test device", amplitude?.store?.deviceId)

        amplitude?.reset()
        println(amplitude?.store?.userId)
        println(amplitude?.store?.deviceId)
        Assertions.assertNull(amplitude?.store?.userId)
        Assertions.assertNotEquals("test device", amplitude?.store?.deviceId)
    }
}
