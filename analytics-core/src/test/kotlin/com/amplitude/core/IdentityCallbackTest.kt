package com.amplitude.core

import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.Identify
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utils.FakeAmplitude
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class IdentityCallbackTest {
    private val amplitude = FakeAmplitude()
    private val callbacks = mutableListOf<String>()
    private val plugin =
        object : Plugin {
            override val type = Plugin.Type.Before
            override lateinit var amplitude: Amplitude

            override fun onUserIdChanged(userId: String?) {
                callbacks += "user:$userId"
            }

            override fun onDeviceIdChanged(deviceId: String?) {
                callbacks += "device:$deviceId"
            }

            override fun onIdentityChanged(identity: AnalyticsIdentity) {
                callbacks += "identity"
            }

            override fun onReset() {
                callbacks += "reset"
            }
        }

    @Nested
    inner class IdSetters {
        @Test
        fun `repeated ids do not notify individual or identity callbacks`() {
            amplitude.setUserId("user")
            amplitude.setDeviceId("device")
            amplitude.add(plugin)

            amplitude.setUserId("user")
            amplitude.setDeviceId("device")

            assertTrue(callbacks.isEmpty())
        }

        @Test
        fun `changed ids notify each individual and identity callback once`() {
            amplitude.add(plugin)

            amplitude.setUserId("user")
            amplitude.setDeviceId("device")
            amplitude.setUserId(null)

            assertEquals(listOf("user:user", "identity", "device:device", "identity", "user:null", "identity"), callbacks)
        }

        @Test
        fun `identify with unchanged option ids does not trigger id callbacks`() {
            amplitude.setUserId("user")
            amplitude.setDeviceId("device")
            amplitude.add(plugin)

            amplitude.identify(
                Identify(),
                EventOptions().apply {
                    userId = "user"
                    deviceId = "device"
                },
            )

            assertTrue(callbacks.isEmpty())
        }
    }

    @Nested
    inner class Reset {
        @Test
        fun `anonymous reset skips user callback but not device identity or reset callbacks`() {
            amplitude.add(plugin)

            amplitude.reset()

            assertEquals(listOf("device:${amplitude.getDeviceId()}", "identity", "reset"), callbacks)
        }

        @Test
        fun `identified reset notifies changed user and one atomic identity`() {
            amplitude.setUserId("user")
            amplitude.add(plugin)

            amplitude.reset()

            assertEquals(listOf("user:null", "device:${amplitude.getDeviceId()}", "identity", "reset"), callbacks)
        }
    }
}
