package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.Identify
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utils.FakeAmplitude
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class UserPropertiesTest {
    @Test
    fun `identify applies supported operations synchronously and preserves outgoing operations`() {
        val amplitude = FakeAmplitude()
        val events = mutableListOf<BaseEvent>()
        amplitude.add(recorder { events += it })

        amplitude.identify(Identify().set("plan", "free").set("remove", true))
        amplitude.identify(Identify().set("plan", "paid").unset("remove").add("count", 1))

        assertEquals(mapOf("plan" to "paid"), amplitude.identity.userProperties)
        assertEquals(
            mapOf("\$set" to mapOf("plan" to "paid"), "\$unset" to mapOf("remove" to "-"), "\$add" to mapOf("count" to 1)),
            events.last().userProperties,
        )
        amplitude.identify(Identify().clearAll())
        assertTrue(amplitude.identity.userProperties.isEmpty())
        assertEquals(mapOf("\$clearAll" to "-"), events.last().userProperties)
    }

    @Test
    fun `store mirrors identity user properties`() {
        val amplitude = FakeAmplitude()

        amplitude.identify(Identify().set("plan", "paid"))
        assertEquals(mapOf("plan" to "paid"), amplitude.store.userProperties)

        amplitude.setIdentity("user", "device", mapOf("plan" to "pro"))
        assertEquals(mapOf("plan" to "pro"), amplitude.store.userProperties)

        amplitude.reset()
        assertTrue(amplitude.store.userProperties.isEmpty())
    }

    @Test
    fun `plain properties and operation maps are applied before plugins execute`() {
        val amplitude = FakeAmplitude()
        amplitude.identify(mapOf("old" to true))
        amplitude.add(
            recorder {
                assertEquals(
                    mapOf("plain" to "value", "plan" to "paid", "\$email" to "user@example.com", "\$custom" to mapOf("nested" to true)),
                    amplitude.identity.userProperties,
                )
            },
        )
        amplitude.track(
            BaseEvent().apply {
                eventType = Constants.IDENTIFY_EVENT
                userProperties =
                    mutableMapOf(
                        "\$clearAll" to "-",
                        "\$unset" to mapOf("old" to "-"),
                        "\$set" to mapOf("plan" to "paid"),
                        "plain" to "value",
                        "\$email" to "user@example.com",
                        "\$custom" to mapOf("nested" to true),
                        "\$add" to mapOf("count" to 1),
                        "\$setOnce" to mapOf("ignored" to "value"),
                        "\$append" to mapOf("ignored" to "value"),
                    )
            },
        )
    }

    @Test
    fun `ordinary event user properties are uploaded without changing identity or notifying observers`() {
        val amplitude = FakeAmplitude()
        amplitude.identify(mapOf("plan" to "free"))
        val events = mutableListOf<BaseEvent>()
        var identityCallbacks = 0
        amplitude.add(
            object : Plugin {
                override val type = Plugin.Type.Before
                override lateinit var amplitude: Amplitude

                override fun execute(event: BaseEvent): BaseEvent {
                    events += event
                    return event
                }

                override fun onIdentityChanged(identity: AnalyticsIdentity) {
                    identityCallbacks++
                }
            },
        )
        val properties =
            mutableMapOf<String, Any?>(
                "\$clearAll" to "-",
                "\$unset" to mapOf("plan" to "-"),
                "\$set" to mapOf("plan" to "paid"),
                "plain" to "value",
            )

        amplitude.track(
            BaseEvent().apply {
                eventType = "ordinary event"
                userProperties = properties
            },
        )

        assertEquals(mapOf("plan" to "free"), amplitude.identity.userProperties)
        assertEquals(0, identityCallbacks)
        assertEquals(properties, events.single().userProperties)
    }

    @Test
    fun `snapshots detach nested containers and normalize arrays`() {
        val amplitude = FakeAmplitude()
        val values = mutableListOf<Any>("original")
        val array = intArrayOf(1, 2)
        val nested = mutableMapOf<String, Any>("list" to values, "array" to array)
        amplitude.identify(Identify().set("nested", nested))
        val snapshot = amplitude.identity
        values += "later"
        array[0] = 9
        nested["new"] = true

        assertEquals(mapOf("nested" to mapOf("list" to listOf("original"), "array" to listOf(1, 2))), snapshot.userProperties)
        assertEquals(snapshot.userProperties, amplitude.identity.userProperties)
        assertNotSame(snapshot.userProperties, amplitude.identity.userProperties)
        @Suppress("UNCHECKED_CAST")
        val nestedSnapshot = snapshot.userProperties["nested"] as MutableMap<String, Any?>
        assertThrows(UnsupportedOperationException::class.java) { nestedSnapshot["mutation"] = true }
        @Suppress("UNCHECKED_CAST")
        val listSnapshot = nestedSnapshot["list"] as MutableList<Any?>
        assertThrows(UnsupportedOperationException::class.java) { listSnapshot.add("mutation") }
    }

    @Test
    fun `reset clears properties once while changing ids and leaves old snapshots intact`() {
        val amplitude = FakeAmplitude()
        amplitude.setUserId("before")
        amplitude.setDeviceId("device")
        amplitude.identify(mapOf("plan" to "paid"))
        val before = amplitude.identity
        val snapshots = mutableListOf<AnalyticsIdentity>()
        amplitude.add(
            object : Plugin {
                override val type = Plugin.Type.Before
                override lateinit var amplitude: Amplitude

                override fun onIdentityChanged(identity: AnalyticsIdentity) {
                    snapshots += identity
                }
            },
        )

        amplitude.reset()

        assertEquals(1, snapshots.size)
        assertEquals(null, snapshots.single().userId)
        assertTrue(snapshots.single().userProperties.isEmpty())
        assertEquals(mapOf("plan" to "paid"), before.userProperties)
    }

    @Test
    fun `opted out events and group properties do not modify local user properties`() {
        val amplitude = FakeAmplitude()
        amplitude.identify(mapOf("plan" to "free"))
        amplitude.groupIdentify("team", "engineering", Identify().set("plan", "group"))
        amplitude.optOut = true
        amplitude.identify(Identify().clearAll())
        assertEquals(mapOf("plan" to "free"), amplitude.identity.userProperties)
    }

    @Test
    fun `user changes retain properties until reset and separate instances start empty`() {
        val amplitude = FakeAmplitude()
        amplitude.identify(mapOf("plan" to "paid"))
        amplitude.setUserId("another-user")
        assertEquals(mapOf("plan" to "paid"), amplitude.identity.userProperties)
        assertTrue(FakeAmplitude().identity.userProperties.isEmpty())
    }

    @Test
    fun `concurrent identify mutations do not lose independent properties`() {
        val amplitude = FakeAmplitude()
        val start = CountDownLatch(1)
        val finished = CountDownLatch(8)
        repeat(8) { worker ->
            thread {
                try {
                    start.await()
                    repeat(50) { property -> amplitude.identify(mapOf("$worker-$property" to property)) }
                } finally {
                    finished.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(finished.await(10, TimeUnit.SECONDS))
        assertEquals(400, amplitude.identity.userProperties.size)
    }

    private fun recorder(block: (BaseEvent) -> Unit): Plugin =
        object : Plugin {
            override val type = Plugin.Type.Before
            override lateinit var amplitude: Amplitude

            override fun execute(event: BaseEvent): BaseEvent {
                block(event)
                return event
            }
        }
}
