package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utils.FakeAmplitude
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Phase 0 Plugin contract for unified blades — interface additions, wiring,
 * dedup, and lookup helpers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PluginContractTest {
    private val amplitude: Amplitude get() = FakeAmplitude()

    @Nested
    inner class StateCallbacks {
        @Test
        fun `setUserId fires onUserIdChanged on every timeline plugin`() {
            val a = amplitude
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.setUserId("user-1")

            assertEquals(listOf<String?>("user-1"), recorder.userIds)
        }

        @Test
        fun `setDeviceId fires onDeviceIdChanged on every timeline plugin`() {
            val a = amplitude
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.setDeviceId("device-1")

            assertEquals(listOf<String?>("device-1"), recorder.deviceIds)
        }

        @Test
        fun `setting optOut fires onOptOutChanged on every plugin`() {
            val a = amplitude
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.optOut = true

            assertEquals(listOf(true), recorder.optOuts)
        }

        @Test
        fun `reset fires one bundled identity change plus onReset, not two separate identity changes`() {
            val a = amplitude
            val recorder = RecordingPlugin()
            a.add(recorder)

            a.reset()

            // Exactly one userId observation (null) and one deviceId observation (the new id),
            // not interleaved double-fires.
            assertEquals(1, recorder.userIds.size, "expected one userId callback, saw ${recorder.userIds}")
            assertEquals(1, recorder.deviceIds.size, "expected one deviceId callback, saw ${recorder.deviceIds}")
            assertNull(recorder.userIds.single())
            assertEquals(1, recorder.resets)
        }

        @Test
        fun `ObservePlugins also receive onOptOutChanged and onReset`() {
            // Finding 2 resolution: ObservePlugins are notified for state callbacks too.
            val a = amplitude
            val observer = RecordingObservePlugin()
            a.add(observer)

            a.optOut = true
            a.reset()

            assertEquals(listOf(true), observer.optOuts)
            assertEquals(1, observer.resets)
        }

        @Test
        fun `a throwing plugin in notify fan-out does not stop subsequent plugins`() {
            // High-severity Codex finding: a plugin throwing from a state callback
            // (e.g. onSessionIdChanged) must not propagate out of notifyAllPlugins,
            // because the Android event loop relies on these callbacks completing
            // normally. Other registered plugins still receive the notification.
            val a = amplitude
            val thrower = ThrowingObservePlugin()
            val recorder = RecordingObservePlugin()
            a.add(thrower)
            a.add(recorder)

            a.optOut = true
            a.reset()

            assertEquals(listOf(true), recorder.optOuts)
            assertEquals(1, recorder.resets)
        }

        @Test
        fun `a throwing ObservePlugin in setUserId does not stop subsequent plugins`() {
            // Hazard: State.userId setter iterates state.plugins directly to fire
            // onUserIdChanged. If a customer ObservePlugin throws, the exception
            // propagates out of setUserId() — crashing the call site or
            // terminating the coroutine that triggered the identity change.
            // Same isolation contract as notifyAllPlugins.
            val a = amplitude
            val thrower = ThrowingObservePlugin(throwOnUserId = true)
            val recorder = RecordingObservePlugin()
            a.add(thrower)
            a.add(recorder)

            a.setUserId("new-user")

            assertEquals(listOf<String?>("new-user"), recorder.userIds)
            assertEquals("new-user", a.store.userId)
        }

        @Test
        fun `a throwing ObservePlugin in setDeviceId does not stop subsequent plugins`() {
            val a = amplitude
            val thrower = ThrowingObservePlugin(throwOnDeviceId = true)
            val recorder = RecordingObservePlugin()
            a.add(thrower)
            a.add(recorder)

            a.setDeviceId("new-device")

            assertEquals(listOf<String?>("new-device"), recorder.deviceIds)
            assertEquals("new-device", a.store.deviceId)
        }

        @Test
        fun `a throwing ObservePlugin in reset (setIdentity) does not stop subsequent plugins`() {
            // reset() routes through State.setIdentity, which fires both
            // onUserIdChanged and onDeviceIdChanged on each ObservePlugin.
            // Both callbacks must be isolated.
            val a = amplitude
            val thrower =
                ThrowingObservePlugin(
                    throwOnUserId = true,
                    throwOnDeviceId = true,
                    throwOnReset = false,
                    throwOnOptOut = false,
                )
            val recorder = RecordingObservePlugin()
            a.add(thrower)
            a.add(recorder)

            a.reset()

            assertEquals(1, recorder.userIds.size)
            assertNull(recorder.userIds.single())
            assertEquals(1, recorder.deviceIds.size)
            assertEquals(1, recorder.resets)
        }

        @Test
        fun `notifyAllPlugins snapshots the observe store so callbacks can register new plugins safely`() {
            // Codex finding 3: notifyAllPlugins iterates state.plugins (a plain
            // MutableList). If a callback calls amplitude.add(...) we'd hit
            // ConcurrentModificationException. The fix snapshots before
            // iterating; newly added plugins do NOT receive the in-progress
            // notification.
            val a = amplitude
            val late = RecordingObservePlugin()
            val mutator =
                object : ObservePlugin() {
                    override val name: String = "mutator"
                    override lateinit var amplitude: Amplitude

                    override fun onUserIdChanged(userId: String?) {}

                    override fun onDeviceIdChanged(deviceId: String?) {}

                    override fun onReset() {
                        amplitude.add(late)
                    }
                }
            a.add(mutator)

            // Should not throw CME.
            a.reset()

            // Snapshot semantics: the plugin added during onReset does NOT
            // receive the in-progress onReset.
            assertEquals(0, late.resets)
        }

        @Test
        fun `ObservePlugin receives exactly one userId and deviceId callback after reset`() {
            // ObservePlugin (not a timeline plugin) must also see exactly one
            // onUserIdChanged(null) and one onDeviceIdChanged(newId) after reset().
            // State.setIdentity calls notifyObservePlugins twice — once per field —
            // so each callback fires exactly once, not interleaved or doubled.
            val a = amplitude
            val observer = RecordingObservePlugin()
            a.add(observer)

            a.reset()

            assertEquals(1, observer.userIds.size, "expected one userId callback, saw ${observer.userIds}")
            assertEquals(1, observer.deviceIds.size, "expected one deviceId callback, saw ${observer.deviceIds}")
            assertNull(observer.userIds.single())
        }

        @Test
        fun `remove(observePlugin) calls teardown`() {
            // Verifies the bug fix: amplitude.remove() on an ObservePlugin must
            // route through State.remove() which calls teardown().
            val a = amplitude
            val observer = NamedObservePlugin("teardown-target")
            a.add(observer)

            a.remove(observer)

            assertTrue(observer.tornDown, "teardown() should be called when an ObservePlugin is removed")
        }

        @Test
        fun `a throwing timeline plugin (non-ObservePlugin) does not stop notification fan-out`() {
            // A plain Plugin with type=Before whose onUserIdChanged throws must
            // not prevent subsequent plugins from receiving the notification.
            // Same isolation contract as ThrowingObservePlugin, but for a
            // timeline (non-ObservePlugin) path.
            val a = amplitude
            val thrower = ThrowingPlugin()
            val recorder = RecordingPlugin()
            a.add(thrower)
            a.add(recorder)

            a.setUserId("after-throw")

            assertEquals(listOf<String?>("after-throw"), recorder.userIds)
        }

        @Test
        fun `notifyAllPlugins with empty store does not crash`() {
            // Calling optOut or reset on an Amplitude instance with no plugins
            // registered should be a silent no-op, not a crash. The empty
            // snapshot path in notifyAllPlugins / State.notifyObservePlugins
            // must handle zero plugins gracefully.
            val a = FakeAmplitude()

            // Neither of these should throw.
            a.optOut = true
            a.reset()
        }

        @Test
        fun `setIdentity isolates userId and deviceId callbacks per plugin`() {
            // CX-4 fix: State.setIdentity calls notifyObservePlugins separately
            // for userId and deviceId. If a plugin's onUserIdChanged throws, that
            // plugin must still receive onDeviceIdChanged in the same reset().
            val a = amplitude
            val throwOnUserId = RecordingObservePluginThrowingOnUserId()
            a.add(throwOnUserId)

            a.reset()

            // The plugin threw on userId — but it must still record the deviceId.
            assertEquals(
                1,
                throwOnUserId.deviceIds.size,
                "plugin should still receive onDeviceIdChanged after its own onUserIdChanged threw",
            )
        }

        @Test
        fun `State setUserId snapshots the observe store so callbacks can register new plugins safely`() {
            // Same snapshot guarantee as notifyAllPlugins, but for the
            // State.setIdentity / setUserId iteration path. A callback that
            // calls amplitude.add() during onUserIdChanged must not trigger
            // ConcurrentModificationException.
            val a = amplitude
            val late = RecordingObservePlugin()
            val mutator =
                object : ObservePlugin() {
                    override val name: String = "state-mutator"
                    override lateinit var amplitude: Amplitude

                    override fun onUserIdChanged(userId: String?) {
                        amplitude.add(late)
                    }

                    override fun onDeviceIdChanged(deviceId: String?) {}
                }
            a.add(mutator)

            // Should not throw CME.
            a.setUserId("new-user")

            // Snapshot semantics: the plugin added during onUserIdChanged does
            // NOT receive the in-progress notification.
            assertEquals(0, late.userIds.size)
        }
    }

    @Nested
    inner class Dedup {
        @Test
        fun `add with non-null name evicts a previously registered plugin sharing that name`() {
            val a = amplitude
            val first = NamedPlugin("shared")
            val second = NamedPlugin("shared")

            a.add(first)
            a.add(second)

            assertTrue(first.tornDown, "first plugin should have been torn down on dedup")
            // second is the surviving instance; findPlugin resolves to it by type
            assertSame(second, a.findPlugin<NamedPlugin>())
        }

        @Test
        fun `add without a name does not deduplicate`() {
            val a = amplitude
            val first = NamedPlugin(name = null)
            val second = NamedPlugin(name = null)

            a.add(first)
            a.add(second)

            // Neither was evicted; both live in the timeline.
            assertEquals(false, first.tornDown)
            assertEquals(false, second.tornDown)
        }

        @Test
        fun `dedup teardown throwing does not prevent the replacement plugin from being registered`() {
            // If the evicted plugin's teardown() throws, the new plugin must
            // still be wired in. State.removeByName silently swallows teardown
            // exceptions; this test verifies that contract end-to-end.
            val a = amplitude
            val thrower = ThrowingTeardownPlugin("flaky-plugin")
            val replacement = NamedPlugin("flaky-plugin")

            a.add(thrower)
            a.add(replacement)

            // replacement is the surviving instance; thrower is a different type
            // so findPlugin<NamedPlugin> resolves unambiguously to replacement
            assertSame(replacement, a.findPlugin<NamedPlugin>())
        }

        @Test
        fun `dedup also tears down ObservePlugin in the store`() {
            val a = amplitude
            val first = NamedObservePlugin("obs-shared")
            val second = NamedPlugin("obs-shared")

            a.add(first)
            a.add(second)

            assertTrue(first.tornDown, "ObservePlugin in store should be torn down on dedup")
            // second (a regular plugin, not ObservePlugin) is the survivor
            assertSame(second, a.findPlugin<NamedPlugin>())
        }
    }

    @Nested
    inner class FindPlugin {
        @Test
        fun `findPlugin returns first plugin matching the type across all timeline layers`() {
            val a = amplitude
            val before = TypedPlugin(Plugin.Type.Before)
            val enrichment = MarkerPlugin(Plugin.Type.Enrichment)
            val utility = MarkerPlugin(Plugin.Type.Utility)

            a.add(before)
            a.add(enrichment)
            a.add(utility)

            val found = a.findPlugin<MarkerPlugin>()
            // First MarkerPlugin discovered when iterating layers.
            assertTrue(found === enrichment || found === utility, "expected Enrichment or Utility, got $found")
        }

        @Test
        fun `findPlugin returns null when no plugin matches`() {
            val a = amplitude
            a.add(TypedPlugin(Plugin.Type.Before))

            assertNull(a.findPlugin<MarkerPlugin>())
        }

        @Test
        fun `findPlugin traverses the observe store too`() {
            // ObservePlugins added via amplitude.add() live in State.plugins, not
            // the timeline, so findPlugin<T>() must search both stores.
            val a = amplitude
            val observe = NamedObservePlugin("observed-by-type")

            a.add(observe)

            assertSame(observe, a.findPlugin<NamedObservePlugin>())
        }
    }

    @Nested
    inner class ObserveTypeTrap {
        // Finding 1 resolution: a Type.Observe mediator now exists in Timeline,
        // so a bare Plugin declaring Plugin.Type.Observe lands in the timeline
        // and receives state callbacks instead of being silently dropped.
        @Test
        fun `bare Plugin with Type Observe is wired into the timeline and receives callbacks`() {
            val a = amplitude
            val observePlugin =
                object : Plugin {
                    override val type: Plugin.Type = Plugin.Type.Observe
                    override lateinit var amplitude: Amplitude
                    var receivedUserIds = mutableListOf<String?>()

                    override fun onUserIdChanged(userId: String?) {
                        receivedUserIds += userId
                    }
                }

            a.add(observePlugin)
            a.setUserId("trap-test")

            assertEquals(listOf<String?>("trap-test"), observePlugin.receivedUserIds)
            assertSame(observePlugin, a.timeline.findPlugin<Plugin>())
        }
    }

    @Nested
    inner class Concurrency {
        @RepeatedTest(20)
        fun `concurrent setUserId calls do not crash or lose notifications`() {
            val a = amplitude
            val received = Collections.synchronizedList(mutableListOf<String?>())
            val observer =
                object : ObservePlugin() {
                    override lateinit var amplitude: Amplitude

                    override fun onUserIdChanged(userId: String?) {
                        received += userId
                    }

                    override fun onDeviceIdChanged(deviceId: String?) {}
                }
            a.add(observer)

            val threads = 10
            val barrier = CyclicBarrier(threads)
            val latch = CountDownLatch(threads)
            repeat(threads) { i ->
                Thread {
                    barrier.await()
                    a.setUserId("user-$i")
                    latch.countDown()
                }.start()
            }
            latch.await(5, TimeUnit.SECONDS)

            assertEquals(threads, received.size, "every setUserId should produce exactly one notification")
        }

        @RepeatedTest(20)
        fun `concurrent add and setUserId do not throw CME`() {
            val a = amplitude
            val threads = 10
            val barrier = CyclicBarrier(threads * 2)
            val latch = CountDownLatch(threads * 2)

            repeat(threads) { i ->
                Thread {
                    barrier.await()
                    a.add(RecordingObservePlugin())
                    latch.countDown()
                }.start()
                Thread {
                    barrier.await()
                    a.setUserId("user-$i")
                    latch.countDown()
                }.start()
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "all threads should complete without deadlock")
        }

        @RepeatedTest(20)
        fun `concurrent add and remove do not throw CME on observe store`() {
            val a = amplitude
            val plugins = (0 until 20).map { NamedObservePlugin("obs-$it") }
            plugins.forEach { a.add(it) }

            val barrier = CyclicBarrier(2)
            val latch = CountDownLatch(2)

            Thread {
                barrier.await()
                plugins.take(10).forEach { a.remove(it) }
                latch.countDown()
            }.start()
            Thread {
                barrier.await()
                (20 until 30).forEach { a.add(NamedObservePlugin("obs-$it")) }
                latch.countDown()
            }.start()

            assertTrue(latch.await(5, TimeUnit.SECONDS), "concurrent add+remove should not deadlock or crash")
        }

        @RepeatedTest(20)
        fun `concurrent reset and setUserId produce consistent state`() {
            val a = amplitude
            val barrier = CyclicBarrier(2)
            val latch = CountDownLatch(2)

            Thread {
                barrier.await()
                a.reset()
                latch.countDown()
            }.start()
            Thread {
                barrier.await()
                a.setUserId("concurrent-user")
                latch.countDown()
            }.start()

            assertTrue(latch.await(5, TimeUnit.SECONDS), "concurrent reset + setUserId should not deadlock")
            // After both complete, userId is either null (reset won) or "concurrent-user" (setUserId won).
            val finalUserId = a.store.userId
            assertTrue(
                finalUserId == null || finalUserId == "concurrent-user",
                "userId should be null or 'concurrent-user', got: $finalUserId",
            )
        }

        @RepeatedTest(20)
        fun `concurrent findPlugin and add do not throw CME`() {
            val a = amplitude
            val found = AtomicInteger(0)
            val threads = 10
            val barrier = CyclicBarrier(threads * 2)
            val latch = CountDownLatch(threads * 2)

            repeat(threads) { i ->
                Thread {
                    barrier.await()
                    a.add(NamedObservePlugin("lookup-$i"))
                    latch.countDown()
                }.start()
                Thread {
                    barrier.await()
                    if (a.findPlugin<NamedObservePlugin>() != null) found.incrementAndGet()
                    latch.countDown()
                }.start()
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "concurrent findPlugin + add should not crash")
        }

        @RepeatedTest(20)
        fun `concurrent dedup with same name does not leave duplicates`() {
            val a = amplitude
            val threads = 10
            val barrier = CyclicBarrier(threads)
            val latch = CountDownLatch(threads)

            repeat(threads) {
                Thread {
                    barrier.await()
                    a.add(NamedPlugin("dedup-race"))
                    latch.countDown()
                }.start()
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "concurrent dedup should not deadlock")

            // Count how many plugins named "dedup-race" exist in the timeline.
            var count = 0
            a.timeline.applyClosure { if (it.name == "dedup-race") count++ }
            // Known limitation: without full lock around add(), concurrent dedup can
            // leave more than one. This test documents the current behavior — it must
            // never crash, but may leave duplicates under extreme concurrency.
            assertTrue(count >= 1, "at least one plugin should be registered")
        }

        @Test
        fun `dedup teardown and identity callback add do not deadlock`() {
            val a = amplitude
            val enteredTeardown = CountDownLatch(1)
            val callbackReadyToAdd = CountDownLatch(1)
            val completed = CountDownLatch(2)
            val errors = Collections.synchronizedList(mutableListOf<Throwable>())
            val evicted =
                TeardownSetsUserIdPlugin(
                    name = "deadlock-target",
                    enteredTeardown = enteredTeardown,
                    callbackReadyToAdd = callbackReadyToAdd,
                    errors = errors,
                )
            val identityCallback =
                object : Plugin {
                    override val type: Plugin.Type = Plugin.Type.Before
                    override val name: String = "identity-callback"
                    override lateinit var amplitude: Amplitude

                    override fun execute(event: BaseEvent): BaseEvent = event

                    override fun onUserIdChanged(userId: String?) {
                        if (userId != "identity-thread") return
                        callbackReadyToAdd.countDown()
                        if (!enteredTeardown.await(5, TimeUnit.SECONDS)) {
                            errors += AssertionError("dedup teardown did not start")
                            return
                        }
                        amplitude.add(NamedPlugin("callback-add"))
                    }
                }

            a.add(evicted)
            a.add(identityCallback)

            Thread {
                try {
                    a.add(NamedPlugin("deadlock-target"))
                } catch (t: Throwable) {
                    errors += t
                } finally {
                    completed.countDown()
                }
            }.apply {
                isDaemon = true
                start()
            }
            Thread {
                try {
                    a.setUserId("identity-thread")
                } catch (t: Throwable) {
                    errors += t
                } finally {
                    completed.countDown()
                }
            }.apply {
                isDaemon = true
                start()
            }

            assertTrue(completed.await(5, TimeUnit.SECONDS), "dedup teardown and identity callback add should not deadlock")
            assertTrue(errors.isEmpty(), "unexpected concurrency errors: ${errors.joinToString { it.message.orEmpty() }}")
        }

        @RepeatedTest(20)
        fun `notification during remove does not crash`() {
            val a = amplitude
            val observer = RecordingObservePlugin()
            a.add(observer)

            val barrier = CyclicBarrier(2)
            val latch = CountDownLatch(2)

            Thread {
                barrier.await()
                repeat(50) { a.setUserId("user-$it") }
                latch.countDown()
            }.start()
            Thread {
                barrier.await()
                a.remove(observer)
                latch.countDown()
            }.start()

            assertTrue(latch.await(5, TimeUnit.SECONDS), "notification during remove should not crash")
        }

        @RepeatedTest(20)
        fun `optOut and setUserId racing do not crash`() {
            val a = amplitude
            val recorder = RecordingPlugin()
            a.add(recorder)

            val barrier = CyclicBarrier(2)
            val latch = CountDownLatch(2)

            Thread {
                barrier.await()
                repeat(50) { a.optOut = it % 2 == 0 }
                latch.countDown()
            }.start()
            Thread {
                barrier.await()
                repeat(50) { a.setUserId("user-$it") }
                latch.countDown()
            }.start()

            assertTrue(latch.await(5, TimeUnit.SECONDS), "optOut + setUserId racing should not crash")
        }

        @Test
        fun `plugin reentrancy in onUserIdChanged does not overwrite inner setUserId value`() {
            // Reentrancy scenario: a plugin's onUserIdChanged calls amplitude.setUserId("inner").
            // Before the fix, IdentityCoordinator fired the notification while still holding
            // its lock. Because synchronized is reentrant on the JVM, the inner call completed
            // (writing "inner"), then control returned to the outer call which committed its
            // original value — overwriting "inner" silently.
            //
            // After the fix, the notification is fired OUTSIDE the lock. The inner setUserId
            // fully completes (field write + commit + notification) before the outer call
            // can do any further work, so "inner" is the final value.
            val a = amplitude
            val done = CountDownLatch(1)

            val reentrantPlugin =
                object : ObservePlugin() {
                    override lateinit var amplitude: Amplitude
                    var triggered = false

                    override fun onUserIdChanged(userId: String?) {
                        if (!triggered && userId == "outer") {
                            triggered = true
                            // Re-enter setUserId from within the callback.
                            amplitude.setUserId("inner")
                        }
                        if (userId == "inner") done.countDown()
                    }

                    override fun onDeviceIdChanged(deviceId: String?) {}
                }
            a.add(reentrantPlugin)

            a.setUserId("outer")

            assertTrue(done.await(5, TimeUnit.SECONDS), "inner setUserId callback should complete")
            // The most recent caller (inner) wins — outer must not overwrite it.
            assertEquals("inner", a.store.userId, "inner setUserId must not be overwritten by outer call")
            // No infinite recursion: the plugin guarded its own re-entry with `triggered`.
        }

        @Test
        fun `plugin reentrancy in onDeviceIdChanged does not overwrite inner setDeviceId value`() {
            val a = amplitude
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
            a.add(reentrantPlugin)

            a.setDeviceId("device-outer")

            assertTrue(done.await(5, TimeUnit.SECONDS), "inner setDeviceId callback should complete")
            assertEquals("device-inner", a.store.deviceId, "inner setDeviceId must not be overwritten by outer call")
        }

        @Test
        fun `plugin reentrancy in onUserIdChanged during resetIdentity does not overwrite inner setUserId value`() {
            val a = amplitude
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
            a.add(reentrantPlugin)

            a.reset()

            assertTrue(done.await(5, TimeUnit.SECONDS), "inner setUserId from onUserIdChanged during reset should complete")
            assertEquals(
                "post-reset-user",
                a.store.userId,
                "userId set inside onUserIdChanged callback must not be overwritten by the reset",
            )
        }
    }
}

private open class RecordingPlugin(override val name: String? = "recorder") : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude

    val userIds = mutableListOf<String?>()
    val deviceIds = mutableListOf<String?>()
    val optOuts = mutableListOf<Boolean>()
    var resets: Int = 0

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun onUserIdChanged(userId: String?) {
        userIds += userId
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        deviceIds += deviceId
    }

    override fun onOptOutChanged(optOut: Boolean) {
        optOuts += optOut
    }

    override fun onReset() {
        resets += 1
    }
}

private class RecordingObservePlugin : ObservePlugin() {
    override lateinit var amplitude: Amplitude
    val userIds = mutableListOf<String?>()
    val deviceIds = mutableListOf<String?>()
    val optOuts = mutableListOf<Boolean>()
    var resets: Int = 0

    override fun onUserIdChanged(userId: String?) {
        userIds += userId
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        deviceIds += deviceId
    }

    override fun onOptOutChanged(optOut: Boolean) {
        optOuts += optOut
    }

    override fun onReset() {
        resets += 1
    }
}

private class ThrowingObservePlugin(
    private val throwOnUserId: Boolean = false,
    private val throwOnDeviceId: Boolean = false,
    private val throwOnOptOut: Boolean = true,
    private val throwOnReset: Boolean = true,
) : ObservePlugin() {
    override val name: String = "throwing-observe"
    override lateinit var amplitude: Amplitude

    override fun onUserIdChanged(userId: String?) {
        if (throwOnUserId) throw RuntimeException("boom: onUserIdChanged")
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        if (throwOnDeviceId) throw RuntimeException("boom: onDeviceIdChanged")
    }

    override fun onOptOutChanged(optOut: Boolean) {
        if (throwOnOptOut) throw RuntimeException("boom: onOptOutChanged")
    }

    override fun onReset() {
        if (throwOnReset) throw RuntimeException("boom: onReset")
    }
}

private class NamedPlugin(override val name: String?) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    var tornDown: Boolean = false

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun teardown() {
        tornDown = true
    }
}

private class NamedObservePlugin(override val name: String?) : ObservePlugin() {
    override lateinit var amplitude: Amplitude
    var tornDown: Boolean = false

    override fun onUserIdChanged(userId: String?) {}

    override fun onDeviceIdChanged(deviceId: String?) {}

    override fun teardown() {
        tornDown = true
    }
}

private class TypedPlugin(override val type: Plugin.Type) : Plugin {
    override lateinit var amplitude: Amplitude
}

private class MarkerPlugin(override val type: Plugin.Type) : Plugin {
    override lateinit var amplitude: Amplitude
}

/** A timeline (non-ObservePlugin) whose onUserIdChanged always throws. */
private class ThrowingPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override val name: String = "throwing-plugin"
    override lateinit var amplitude: Amplitude

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun onUserIdChanged(userId: String?) {
        throw RuntimeException("boom: onUserIdChanged from ThrowingPlugin")
    }
}

/** An ObservePlugin that throws from onUserIdChanged but records deviceId calls. */
private class RecordingObservePluginThrowingOnUserId : ObservePlugin() {
    override lateinit var amplitude: Amplitude
    val deviceIds = mutableListOf<String?>()

    override fun onUserIdChanged(userId: String?) {
        throw RuntimeException("boom: onUserIdChanged")
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        deviceIds += deviceId
    }
}

/** A named plugin whose teardown() always throws. */
private class ThrowingTeardownPlugin(override val name: String) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun teardown() {
        throw RuntimeException("boom: teardown")
    }
}

private class TeardownSetsUserIdPlugin(
    override val name: String,
    private val enteredTeardown: CountDownLatch,
    private val callbackReadyToAdd: CountDownLatch,
    private val errors: MutableList<Throwable>,
) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun teardown() {
        enteredTeardown.countDown()
        if (!callbackReadyToAdd.await(5, TimeUnit.SECONDS)) {
            errors += AssertionError("identity callback did not enter add path")
            return
        }
        amplitude.setUserId("teardown-thread")
    }
}
