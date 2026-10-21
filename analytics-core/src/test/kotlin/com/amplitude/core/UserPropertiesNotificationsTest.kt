package com.amplitude.core

import com.amplitude.core.events.AnalyticsEvent
import com.amplitude.core.events.Identify
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.utils.FakeAmplitude
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UserPropertiesNotificationsTest {
    @Test
    fun `every plugin is notified of property, id, and reset changes`() {
        val amplitude = FakeAmplitude()
        val observer = Recorder()
        amplitude.add(observer)

        amplitude.identify(Identify().set("plan", "paid"))
        assertEquals(listOf(mapOf("plan" to "paid")), observer.identities.map { it.userProperties })
        amplitude.setUserId("user")
        amplitude.setDeviceId("device")
        amplitude.reset()
        assertEquals(4, observer.identities.size)
        assertTrue(observer.identities.last().userProperties.isEmpty())
    }

    @Test
    fun `setting an unchanged id does not notify identity changes`() {
        val amplitude = FakeAmplitude()
        amplitude.setUserId("user")
        amplitude.setDeviceId("device")
        val observer = Recorder()
        amplitude.add(observer)

        amplitude.setUserId("user")
        amplitude.setDeviceId("device")

        assertTrue(observer.identities.isEmpty())
    }

    @Test
    fun `effective changes notify once and no op or unsupported operations do not notify`() {
        val amplitude = FakeAmplitude()
        val observer = Recorder()
        amplitude.add(observer)

        amplitude.identify(Identify().set("plan", "paid").set("name", "name"))
        amplitude.identify(Identify().set("plan", "paid"))
        amplitude.identify(Identify().unset("missing").add("count", 1).append("list", "item"))
        amplitude.identify(Identify().unset("name"))
        amplitude.identify(Identify().clearAll())
        amplitude.identify(Identify().clearAll())

        assertEquals(3, observer.identities.size)
    }

    @Test
    fun `each observer receives an independent snapshot after the event is queued`() {
        val amplitude = FakeAmplitude()
        val first = Recorder()
        val second = Recorder()
        val order = mutableListOf<String>()
        amplitude.add(first).add(second)
        amplitude.add(
            object : UniversalPlugin {
                override fun onIdentityChanged(identity: AnalyticsIdentity) {
                    order += "identity"
                    assertEquals(identity.userProperties, amplitude.identity.userProperties)
                }

                override fun <T : AnalyticsEvent> execute(event: T): T {
                    order += "execute"
                    return event
                }
            },
        )

        amplitude.identify(mapOf("plan" to "paid"))

        assertNotSame(first.identities.single(), second.identities.single())
        assertNotSame(first.identities.single().userProperties, second.identities.single().userProperties)
        assertEquals(listOf("execute", "identity"), order)
    }

    @Test
    fun `property callback reentrancy commits newer properties without deadlock or clobbering`() {
        val amplitude = FakeAmplitude()
        val eventOrder = mutableListOf<Any?>()
        val observer = Recorder()
        amplitude.add(
            object : UniversalPlugin {
                override fun <T : AnalyticsEvent> execute(event: T): T {
                    if (event is com.amplitude.core.events.BaseEvent) {
                        eventOrder += (event.userProperties?.get("\$set") as? Map<*, *>)?.get("plan")
                    }
                    return event
                }

                override fun onIdentityChanged(identity: AnalyticsIdentity) {
                    if (identity.userProperties["plan"] == "free") {
                        amplitude.identify(Identify().set("plan", "paid"))
                    }
                }
            },
        ).add(observer)

        amplitude.identify(Identify().set("plan", "free"))

        assertEquals(mapOf("plan" to "paid"), amplitude.identity.userProperties)
        assertEquals(listOf("free", "paid"), eventOrder)
        assertEquals(mapOf("plan" to "paid"), observer.identities.last().userProperties)
    }

    @Test
    fun `id callbacks leave observers on the latest properties after an earlier observer reenters identify`() {
        val amplitude = FakeAmplitude()
        amplitude.identify(mapOf("plan" to "free"))
        amplitude.add(
            object : UniversalPlugin {
                override fun onIdentityChanged(identity: AnalyticsIdentity) {
                    if (identity.userProperties["plan"] == "free") amplitude.identify(mapOf("plan" to "paid"))
                }
            },
        )
        val observer = Recorder()
        amplitude.add(observer)

        amplitude.setUserId("signed-in")

        assertEquals(mapOf("plan" to "paid"), observer.identities.last().userProperties)
    }

    @Test
    fun `throwing property callback does not prevent the next observer or event`() {
        val amplitude = FakeAmplitude()
        val observer = Recorder()
        amplitude.add(
            object : UniversalPlugin {
                override fun onIdentityChanged(identity: AnalyticsIdentity) {
                    error("callback failed")
                }
            },
        ).add(observer)

        amplitude.identify(mapOf("plan" to "paid"))

        assertEquals(1, observer.identities.size)
        assertEquals(1, observer.events)
    }

    private class Recorder : UniversalPlugin {
        val identities = mutableListOf<AnalyticsIdentity>()
        var events = 0

        override fun onIdentityChanged(identity: AnalyticsIdentity) {
            identities += identity
        }

        override fun <T : AnalyticsEvent> execute(event: T): T {
            events++
            return event
        }
    }
}
