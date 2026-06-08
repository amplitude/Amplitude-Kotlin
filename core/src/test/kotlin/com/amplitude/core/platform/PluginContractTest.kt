package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utils.FakeAmplitude
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PluginContractTest {
    @Test
    fun `state callbacks fan out to timeline and observe plugins`() {
        val amplitude = FakeAmplitude()
        val timeline = RecordingPlugin("timeline")
        val observe = RecordingObservePlugin("observe")

        amplitude.add(timeline)
        amplitude.add(observe)

        amplitude.setUserId("user-1")
        amplitude.setDeviceId("device-1")
        amplitude.optOut = true
        amplitude.reset()

        assertEquals(listOf("user-1", null), timeline.userIds)
        assertEquals(2, timeline.deviceIds.size)
        assertEquals("device-1", timeline.deviceIds.first())
        assertEquals(listOf(true), timeline.optOuts)
        assertEquals(1, timeline.resets)

        assertEquals(listOf("user-1", null), observe.userIds)
        assertEquals(2, observe.deviceIds.size)
        assertEquals("device-1", observe.deviceIds.first())
        assertEquals(listOf(true), observe.optOuts)
        assertEquals(1, observe.resets)
    }

    @Test
    fun `reset uses one plugin snapshot for identity and reset callbacks`() {
        val amplitude = FakeAmplitude()
        val late = RecordingPlugin("late")
        val mutator =
            object : Plugin {
                override val type: Plugin.Type = Plugin.Type.Before
                override val name: String = "mutator"
                override lateinit var amplitude: Amplitude

                override fun execute(event: BaseEvent): BaseEvent = event

                override fun onUserIdChanged(userId: String?) {
                    if (userId == null) {
                        amplitude.add(late)
                    }
                }
            }

        amplitude.add(mutator)
        amplitude.reset()

        assertTrue(late.userIds.isEmpty())
        assertTrue(late.deviceIds.isEmpty())
        assertEquals(0, late.resets)
        assertSame(late, amplitude.findPlugin<RecordingPlugin>())
    }

    @Test
    fun `throwing plugins do not stop later callbacks or later plugins`() {
        val amplitude = FakeAmplitude()
        val thrower = ThrowingObservePlugin()
        val recorder = RecordingObservePlugin("recorder")

        amplitude.add(thrower)
        amplitude.add(recorder)

        amplitude.reset()

        assertEquals(listOf<String?>(null), recorder.userIds)
        assertEquals(1, recorder.deviceIds.size)
        assertEquals(1, recorder.resets)
        assertEquals(1, thrower.deviceCallbackAttempts)
    }

    @Test
    fun `reset still delivers onReset to a plugin that threw in onDeviceIdChanged`() {
        val amplitude = FakeAmplitude()
        val thrower = ThrowingObservePlugin()
        val recorder = RecordingObservePlugin("recorder")

        amplitude.add(thrower)
        amplitude.add(recorder)

        amplitude.setUserId("pre-reset-user")
        amplitude.reset()

        // Thrower: all three loops attempted — throws are caught, not propagated.
        assertEquals(2, thrower.userCallbackAttempts, "onUserIdChanged called for setUserId + reset")
        assertEquals(1, thrower.deviceCallbackAttempts, "onDeviceIdChanged called during reset")
        assertEquals(1, thrower.resets, "onReset still called despite prior throws in same reset")

        // Clean recorder: all three callbacks delivered regardless of thrower's exceptions.
        assertEquals(listOf("pre-reset-user", null), recorder.userIds)
        assertEquals(1, recorder.deviceIds.size)
        assertEquals(1, recorder.resets)
    }

    @Test
    fun `reentrant userId callback does not get overwritten by outer mutation`() {
        val amplitude = FakeAmplitude()
        val completed = CountDownLatch(1)
        val plugin =
            object : ObservePlugin() {
                override lateinit var amplitude: Amplitude
                private var reentered = false

                override fun onUserIdChanged(userId: String?) {
                    if (!reentered && userId == "outer") {
                        reentered = true
                        amplitude.setUserId("inner")
                    }
                    if (userId == "inner") {
                        completed.countDown()
                    }
                }

                override fun onDeviceIdChanged(deviceId: String?) {}
            }

        amplitude.add(plugin)
        amplitude.setUserId("outer")

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        assertEquals("inner", amplitude.store.userId)
    }

    @Test
    fun `named plugin add keeps the first registration across stores`() {
        val amplitude = FakeAmplitude()
        // first is a timeline-type plugin, duplicate is an ObservePlugin — proves name
        // reservation blocks across both registries.
        val first = NamedPlugin("shared")
        val duplicate = NamedObservePlugin("shared")

        amplitude.add(first)
        amplitude.add(duplicate)

        // First plugin survives untouched.
        assertSame(first, amplitude.findPlugin<NamedPlugin>())
        assertFalse(first.tornDown, "incumbent must not be torn down")
        // Duplicate was never set up and is not findable.
        assertFalse(duplicate.wasSetUp, "duplicate must not be set up")
        assertNull(amplitude.findPlugin<NamedObservePlugin>())
    }

    @Test
    fun `bare observe type plugin is stored in the timeline`() {
        val amplitude = FakeAmplitude()
        val plugin = RecordingPlugin("bare-observe", Plugin.Type.Observe)

        amplitude.add(plugin)
        amplitude.setUserId("user-1")

        assertEquals(listOf<String?>("user-1"), plugin.userIds)
        assertSame(plugin, amplitude.findPlugin<RecordingPlugin>())
    }

    @RepeatedTest(10)
    fun `concurrent add setUserId and reset do not crash or deadlock`() {
        val amplitude = FakeAmplitude()
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val barrier = CyclicBarrier(3)
        val done = CountDownLatch(3)

        fun launch(block: () -> Unit) {
            Thread {
                try {
                    barrier.await()
                    block()
                } catch (t: Throwable) {
                    errors += t
                } finally {
                    done.countDown()
                }
            }.start()
        }

        launch {
            repeat(50) { amplitude.add(RecordingObservePlugin("observe-$it")) }
        }
        launch {
            repeat(50) { amplitude.setUserId("user-$it") }
        }
        launch {
            repeat(50) { amplitude.reset() }
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertTrue(errors.isEmpty(), errors.joinToString { it.message.orEmpty() })
    }

    @RepeatedTest(20)
    fun `concurrent setUserId calls do not crash or lose notifications`() {
        val amplitude = FakeAmplitude()
        val received = Collections.synchronizedList(mutableListOf<String?>())
        val observer =
            object : ObservePlugin() {
                override lateinit var amplitude: Amplitude

                override fun onUserIdChanged(userId: String?) {
                    received += userId
                }

                override fun onDeviceIdChanged(deviceId: String?) {}
            }
        amplitude.add(observer)

        val threads = 10
        val barrier = CyclicBarrier(threads)
        val latch = CountDownLatch(threads)
        repeat(threads) { i ->
            Thread {
                barrier.await()
                amplitude.setUserId("user-$i")
                latch.countDown()
            }.start()
        }
        latch.await(5, TimeUnit.SECONDS)

        assertEquals(threads, received.size, "every setUserId should produce exactly one notification")
    }

    @RepeatedTest(20)
    fun `concurrent add and setUserId do not throw CME`() {
        val amplitude = FakeAmplitude()
        val threads = 10
        val barrier = CyclicBarrier(threads * 2)
        val latch = CountDownLatch(threads * 2)

        repeat(threads) { i ->
            Thread {
                barrier.await()
                amplitude.add(RecordingObservePlugin("obs-add-$i"))
                latch.countDown()
            }.start()
            Thread {
                barrier.await()
                amplitude.setUserId("user-$i")
                latch.countDown()
            }.start()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "all threads should complete without deadlock")
    }

    @RepeatedTest(20)
    fun `concurrent add and remove do not throw CME on observe store`() {
        val amplitude = FakeAmplitude()
        val plugins = (0 until 20).map { NamedObservePlugin("obs-$it") }
        plugins.forEach { amplitude.add(it) }

        val barrier = CyclicBarrier(2)
        val latch = CountDownLatch(2)

        Thread {
            barrier.await()
            plugins.take(10).forEach { amplitude.remove(it) }
            latch.countDown()
        }.start()
        Thread {
            barrier.await()
            (20 until 30).forEach { amplitude.add(NamedObservePlugin("obs-$it")) }
            latch.countDown()
        }.start()

        assertTrue(latch.await(5, TimeUnit.SECONDS), "concurrent add+remove should not deadlock or crash")
    }

    @RepeatedTest(20)
    fun `concurrent reset and setUserId produce consistent state`() {
        val amplitude = FakeAmplitude()
        val barrier = CyclicBarrier(2)
        val latch = CountDownLatch(2)

        Thread {
            barrier.await()
            amplitude.reset()
            latch.countDown()
        }.start()
        Thread {
            barrier.await()
            amplitude.setUserId("concurrent-user")
            latch.countDown()
        }.start()

        assertTrue(latch.await(5, TimeUnit.SECONDS), "concurrent reset + setUserId should not deadlock")
        val finalUserId = amplitude.store.userId
        assertTrue(
            finalUserId == null || finalUserId == "concurrent-user",
            "userId should be null or 'concurrent-user', got: $finalUserId",
        )
    }

    @RepeatedTest(20)
    fun `concurrent findPlugin and add do not throw CME`() {
        val amplitude = FakeAmplitude()
        val found = AtomicInteger(0)
        val threads = 10
        val barrier = CyclicBarrier(threads * 2)
        val latch = CountDownLatch(threads * 2)

        repeat(threads) { i ->
            Thread {
                barrier.await()
                amplitude.add(NamedObservePlugin("lookup-$i"))
                latch.countDown()
            }.start()
            Thread {
                barrier.await()
                if (amplitude.findPlugin<NamedObservePlugin>() != null) found.incrementAndGet()
                latch.countDown()
            }.start()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "concurrent findPlugin + add should not crash")
    }

    @RepeatedTest(20)
    fun `concurrent dedup with same name does not leave duplicates`() {
        val amplitude = FakeAmplitude()
        val threads = 10
        val barrier = CyclicBarrier(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) {
            Thread {
                barrier.await()
                amplitude.add(NamedPlugin("dedup-race"))
                latch.countDown()
            }.start()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "concurrent dedup should not deadlock")

        // Exactly one plugin named "dedup-race" must survive — registration is atomic via
        // putIfAbsent, so only one thread wins and the rest are rejected.
        var count = 0
        amplitude.timeline.applyClosure { if (it.name == "dedup-race") count++ }
        assertEquals(1, count, "exactly one plugin should be registered")
    }

    @Test
    fun `duplicate add neither tears down the incumbent nor sets up the newcomer`() {
        val amplitude = FakeAmplitude()
        val incumbent = NamedPlugin("dup-target")
        val newcomer = NamedPlugin("dup-target")

        amplitude.add(incumbent)
        amplitude.add(newcomer)

        // Incumbent is still registered and was not torn down.
        assertSame(incumbent, amplitude.findPlugin<NamedPlugin>())
        assertFalse(incumbent.tornDown, "incumbent must not be torn down")
        // Newcomer was never set up.
        assertFalse(newcomer.wasSetUp, "newcomer must not be set up")
    }

    @RepeatedTest(20)
    fun `notification during remove does not crash`() {
        val amplitude = FakeAmplitude()
        val observer = RecordingObservePlugin("remove-race")
        amplitude.add(observer)

        val barrier = CyclicBarrier(2)
        val latch = CountDownLatch(2)

        Thread {
            barrier.await()
            repeat(50) { amplitude.setUserId("user-$it") }
            latch.countDown()
        }.start()
        Thread {
            barrier.await()
            amplitude.remove(observer)
            latch.countDown()
        }.start()

        assertTrue(latch.await(5, TimeUnit.SECONDS), "notification during remove should not crash")
    }

    @RepeatedTest(20)
    fun `optOut and setUserId racing do not crash`() {
        val amplitude = FakeAmplitude()
        val recorder = RecordingPlugin("opt-out-race")
        amplitude.add(recorder)

        val barrier = CyclicBarrier(2)
        val latch = CountDownLatch(2)

        Thread {
            barrier.await()
            repeat(50) { amplitude.optOut = it % 2 == 0 }
            latch.countDown()
        }.start()
        Thread {
            barrier.await()
            repeat(50) { amplitude.setUserId("user-$it") }
            latch.countDown()
        }.start()

        assertTrue(latch.await(5, TimeUnit.SECONDS), "optOut + setUserId racing should not crash")
    }

    @Test
    fun `plugin reentrancy in onDeviceIdChanged does not overwrite inner setDeviceId value`() {
        val amplitude = FakeAmplitude()
        val done = CountDownLatch(1)

        val reentrantPlugin =
            object : ObservePlugin() {
                override lateinit var amplitude: Amplitude
                var triggered = false

                override fun onUserIdChanged(userId: String?) {}

                override fun onDeviceIdChanged(deviceId: String?) {
                    if (!triggered && deviceId == "device-outer") {
                        triggered = true
                        amplitude.setDeviceId("device-inner")
                    }
                    if (deviceId == "device-inner") done.countDown()
                }
            }
        amplitude.add(reentrantPlugin)

        amplitude.setDeviceId("device-outer")

        assertTrue(done.await(5, TimeUnit.SECONDS), "inner setDeviceId callback should complete")
        assertEquals("device-inner", amplitude.store.deviceId, "inner setDeviceId must not be overwritten by outer call")
    }

    @Test
    fun `plugin reentrancy in onUserIdChanged during reset does not overwrite inner setUserId value`() {
        val amplitude = FakeAmplitude()
        val done = CountDownLatch(1)

        val reentrantPlugin =
            object : ObservePlugin() {
                override lateinit var amplitude: Amplitude
                var triggered = false

                override fun onUserIdChanged(userId: String?) {
                    if (!triggered && userId == null) {
                        triggered = true
                        // Simulate a plugin that re-assigns userId after a reset.
                        amplitude.setUserId("post-reset-user")
                    }
                    if (userId == "post-reset-user") done.countDown()
                }

                override fun onDeviceIdChanged(deviceId: String?) {}
            }
        amplitude.add(reentrantPlugin)

        amplitude.reset()

        assertTrue(done.await(5, TimeUnit.SECONDS), "inner setUserId from onUserIdChanged during reset should complete")
        assertEquals(
            "post-reset-user",
            amplitude.store.userId,
            "userId set inside onUserIdChanged callback must not be overwritten by the reset",
        )
    }
}

private open class RecordingPlugin(
    override val name: String?,
    override val type: Plugin.Type = Plugin.Type.Before,
) : Plugin {
    override lateinit var amplitude: Amplitude
    val userIds = Collections.synchronizedList(mutableListOf<String?>())
    val deviceIds = Collections.synchronizedList(mutableListOf<String?>())
    val optOuts = Collections.synchronizedList(mutableListOf<Boolean>())
    private val resetCount = AtomicInteger()
    val resets: Int get() = resetCount.get()

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun onUserIdChanged(userId: String?) {
        userIds.add(userId)
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        deviceIds.add(deviceId)
    }

    override fun onOptOutChanged(optOut: Boolean) {
        optOuts.add(optOut)
    }

    override fun onReset() {
        resetCount.incrementAndGet()
    }
}

private class RecordingObservePlugin(
    override val name: String?,
) : ObservePlugin() {
    override lateinit var amplitude: Amplitude
    val userIds = Collections.synchronizedList(mutableListOf<String?>())
    val deviceIds = Collections.synchronizedList(mutableListOf<String?>())
    val optOuts = Collections.synchronizedList(mutableListOf<Boolean>())
    private val resetCount = AtomicInteger()
    val resets: Int get() = resetCount.get()

    override fun onUserIdChanged(userId: String?) {
        userIds.add(userId)
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        deviceIds.add(deviceId)
    }

    override fun onOptOutChanged(optOut: Boolean) {
        optOuts.add(optOut)
    }

    override fun onReset() {
        resetCount.incrementAndGet()
    }
}

private class ThrowingObservePlugin : ObservePlugin() {
    override val name: String = "thrower"
    override lateinit var amplitude: Amplitude
    var userCallbackAttempts = 0
    var deviceCallbackAttempts = 0
    private val resetCount = AtomicInteger()
    val resets: Int get() = resetCount.get()

    override fun onUserIdChanged(userId: String?) {
        userCallbackAttempts += 1
        throw RuntimeException("user")
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        deviceCallbackAttempts += 1
        throw RuntimeException("device")
    }

    override fun onReset() {
        resetCount.incrementAndGet()
        throw RuntimeException("reset")
    }
}

private class NamedPlugin(override val name: String) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    var tornDown = false
    var wasSetUp = false

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        wasSetUp = true
    }

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun teardown() {
        tornDown = true
    }
}

private class NamedObservePlugin(override val name: String) : ObservePlugin() {
    override lateinit var amplitude: Amplitude
    var wasSetUp = false

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        wasSetUp = true
    }

    override fun onUserIdChanged(userId: String?) {}

    override fun onDeviceIdChanged(deviceId: String?) {}
}
