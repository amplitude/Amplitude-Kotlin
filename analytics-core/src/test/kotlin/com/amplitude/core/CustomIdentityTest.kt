package com.amplitude.core

import com.amplitude.core.platform.UniversalPlugin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CustomIdentityTest {
    @Test
    fun `observing plugins receive ids and reset with a subclass identity implementation`() {
        val amplitude = CustomAmplitude()
        val observer = Recorder()
        amplitude.add(observer)

        amplitude.setUserId("user")
        amplitude.setDeviceId("device")
        amplitude.reset()

        assertEquals(3, observer.identities.size)
        assertEquals("user", observer.identities[0].userId)
        assertEquals("device", observer.identities[1].deviceId)
        assertNull(observer.identities[2].userId)
        assertTrue(observer.identities.all { it.userProperties == mapOf("source" to "custom") })
    }

    @Test
    fun `custom identity ends on the latest value when an observer reenters an id setter`() {
        val amplitude = CustomAmplitude()
        amplitude.add(
            object : UniversalPlugin {
                override fun onIdentityChanged(identity: AnalyticsIdentity) {
                    if (identity.userId == "outer") amplitude.setUserId("inner")
                }
            },
        )
        val observer = Recorder()
        amplitude.add(observer)

        amplitude.setUserId("outer")

        assertEquals("inner", observer.identities.last().userId)
        assertEquals("inner", amplitude.identity.userId)
    }

    private class CustomAmplitude : Amplitude(Configuration("test")) {
        override val identity: AnalyticsIdentity
            get() {
                val userId = store.userId
                val deviceId = store.deviceId
                return object : AnalyticsIdentity {
                    override val userId = userId
                    override val deviceId = deviceId
                    override val userProperties: Map<String, Any?> = mapOf("source" to "custom")
                }
            }

        override fun build(): Deferred<Boolean> = CompletableDeferred(true)
    }

    private class Recorder : UniversalPlugin {
        val identities = mutableListOf<AnalyticsIdentity>()

        override fun onIdentityChanged(identity: AnalyticsIdentity) {
            identities += identity
        }
    }
}
