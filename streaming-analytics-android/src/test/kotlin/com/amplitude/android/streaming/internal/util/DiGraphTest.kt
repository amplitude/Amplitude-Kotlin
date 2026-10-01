package com.amplitude.android.streaming.internal.util

import com.amplitude.android.streaming.internal.util.DiGraph.Companion.singleton
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

class DiGraphTest {
    @Nested
    inner class Singletons {
        @Test
        fun `should initialize lazily and reuse the value within one graph`() {
            val graph = RootGraph()
            assertEquals(0, graph.singletonCreations.get())

            val dependency = graph.rootSingleton

            assertSame(dependency, graph.rootSingleton)
            assertEquals(1, graph.singletonCreations.get())
        }

        @Test
        fun `should keep independent graphs and properties of the same value type separate`() {
            val first = RootGraph()
            val second = RootGraph()

            assertNotSame(first.rootSingleton, second.rootSingleton)
            assertNotSame(first.rootSingleton, first.otherSingleton)
            assertSame(first.otherSingleton, first.otherSingleton)
        }

        @Test
        fun `should cache a null result`() {
            val graph = RootGraph()

            assertNull(graph.nullableSingleton)
            assertNull(graph.nullableSingleton)
            assertEquals(1, graph.nullableCreations.get())
        }

        @Test
        fun `should retry failed initialization and cache the successful result`() {
            val graph = RootGraph()
            graph.beforeSingleton = { error("initialization failed") }

            assertThrows(IllegalStateException::class.java) { graph.rootSingleton }
            graph.beforeSingleton = {}
            val dependency = graph.rootSingleton

            assertSame(dependency, graph.rootSingleton)
            assertEquals(2, graph.singletonCreations.get())
        }
    }

    @Nested
    inner class WeakBindings {
        @Test
        fun `should initialize lazily and reuse a value while it is held`() {
            val graph = RootGraph()
            assertEquals(0, graph.weakCreations.get())

            val dependency = graph.rootWeak

            assertSame(dependency, graph.rootWeak)
            assertEquals(1, graph.weakCreations.get())
        }

        @Test
        fun `should keep independent graphs and weak properties of the same value type separate`() {
            val first = RootGraph()
            val second = RootGraph()
            val dependency = first.rootWeak
            val other = first.otherWeak

            assertNotSame(dependency, second.rootWeak)
            assertNotSame(dependency, other)
            assertSame(other, first.otherWeak)
        }

        @Test
        fun `should allow collection and recreate the value on the next read`() {
            val graph = RootGraph()
            val queue = ReferenceQueue<Dependency>()
            val reference = weakDependencyReference(graph, queue)
            assertEquals(1, graph.weakCreations.get())

            awaitCollected(reference, queue)
            val replacement = graph.rootWeak

            assertSame(graph, replacement.owner)
            assertSame(replacement, graph.rootWeak)
            assertEquals(2, graph.weakCreations.get())
        }

        @Test
        fun `should retry failed weak initialization`() {
            val graph = RootGraph()
            graph.beforeWeak = { error("initialization failed") }

            assertThrows(IllegalStateException::class.java) { graph.rootWeak }
            graph.beforeWeak = {}
            val dependency = graph.rootWeak

            assertSame(dependency, graph.rootWeak)
            assertEquals(2, graph.weakCreations.get())
        }
    }

    @Nested
    inner class Scopes {
        @Test
        fun `should initialize parent bindings on the parent when a child reads first`() {
            val root = RootGraph()
            val first = ChildGraph(root)
            val second = ChildGraph(root)

            val singleton = first.rootSingleton
            val weak = first.rootWeak

            assertSame(root, singleton.owner)
            assertSame(root, weak.owner)
            assertSame(singleton, root.rootSingleton)
            assertSame(singleton, second.rootSingleton)
            assertSame(weak, root.rootWeak)
            assertSame(weak, second.rootWeak)
            assertEquals(1, root.singletonCreations.get())
            assertEquals(1, root.weakCreations.get())
            assertEquals(0, first.singletonCreations.get())
            assertEquals(0, first.weakCreations.get())
        }

        @Test
        fun `should give sibling scopes their own bindings with shared parent dependencies`() {
            val root = RootGraph()
            val first = ChildGraph(root)
            val second = ChildGraph(root)
            val singleton = first.childSingleton
            val weak = first.childWeak

            assertSame(first, singleton.owner)
            assertSame(first, weak.owner)
            assertSame(singleton, first.childSingleton)
            assertSame(weak, first.childWeak)
            assertNotSame(singleton, second.childSingleton)
            assertNotSame(weak, second.childWeak)
            assertSame(root.rootSingleton, singleton.parentDependency)
            assertSame(root.rootSingleton, second.childSingleton.parentDependency)
            assertSame(root.rootWeak, weak.parentDependency)
            assertSame(root.rootWeak, second.childWeak.parentDependency)
        }

        @Test
        fun `should resolve each binding to its owner across multiple scope levels`() {
            val root = RootGraph()
            val child = ChildGraph(root)
            val first = GrandchildGraph(child)
            val second = GrandchildGraph(child)

            val rootSingleton = first.rootSingleton
            val rootWeak = first.rootWeak
            val childSingleton = first.childSingleton
            val childWeak = first.childWeak

            assertSame(root, rootSingleton.owner)
            assertSame(root, rootWeak.owner)
            assertSame(child, childSingleton.owner)
            assertSame(child, childWeak.owner)
            assertSame(childSingleton, second.childSingleton)
            assertSame(childWeak, second.childWeak)
            assertSame(first, first.grandchildSingleton.owner)
            assertSame(first.grandchildSingleton, first.grandchildSingleton)
            assertNotSame(first.grandchildSingleton, second.grandchildSingleton)
        }

        @Test
        fun `should share bindings with an ancestor of the same scope type`() {
            val outer = ChildGraph(RootGraph())
            val inner = ChildGraph(outer)
            val singleton = inner.childSingleton
            val weak = inner.childWeak

            assertSame(outer, singleton.owner)
            assertSame(outer, weak.owner)
            assertSame(singleton, outer.childSingleton)
            assertSame(weak, outer.childWeak)
        }

        @Test
        fun `should share inherited member bindings as well as extension bindings`() {
            val root = RootGraph()
            val first = ChildGraph(root)
            val second = ChildGraph(root)

            val dependency = first.memberSingleton

            assertSame(root, dependency.owner)
            assertSame(dependency, root.memberSingleton)
            assertSame(dependency, second.memberSingleton)
        }

        @Test
        fun `should use the owning graphs lazy hook for both cache types`() {
            val root = RootGraph()
            val child = ChildGraph(root)

            val singleton = child.rootSingleton
            val weak = child.rootWeak

            assertEquals(2, root.lazyCreations.get())
            assertEquals(0, child.lazyCreations.get())
            assertSame(singleton, child.rootSingleton)
            assertSame(weak, child.rootWeak)
            assertEquals(2, root.lazyCreations.get())
        }
    }

    @Nested
    inner class ConcurrentAccess {
        @ParameterizedTest(name = "weak = {0}")
        @ValueSource(booleans = [false, true])
        fun `should initialize once when the parent and its scopes read concurrently`(weak: Boolean) {
            val root = RootGraph()
            val readers = listOf(root, ChildGraph(root), ChildGraph(root), GrandchildGraph(ChildGraph(root)))
            val executor = Executors.newFixedThreadPool(readers.size)
            val barrier = CyclicBarrier(readers.size)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            root.beforeRead(weak) {
                entered.countDown()
                assertTrue(release.await(5, SECONDS), "initializer was not released")
            }
            try {
                val futures =
                    readers.map { graph ->
                        executor.submit<Dependency> {
                            barrier.await(5, SECONDS)
                            graph.read(weak)
                        }
                    }
                assertTrue(entered.await(5, SECONDS), "initializer did not start")
                release.countDown()
                val values = futures.map { it.get(5, SECONDS) }

                values.forEach { assertSame(values.first(), it) }
                assertSame(root, values.first().owner)
                assertEquals(1, if (weak) root.weakCreations.get() else root.singletonCreations.get())
            } finally {
                release.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(5, SECONDS))
            }
        }

        @ParameterizedTest(name = "weak = {0}")
        @ValueSource(booleans = [false, true])
        fun `should not block unrelated bindings while an initializer is running`(weak: Boolean) {
            val graph = RootGraph()
            val executor = Executors.newFixedThreadPool(2)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            graph.beforeRead(weak) {
                entered.countDown()
                assertTrue(release.await(10, SECONDS), "initializer was not released")
            }
            try {
                val blocked = executor.submit<Dependency> { graph.read(weak) }
                assertTrue(entered.await(5, SECONDS), "initializer did not start")

                val independent =
                    executor.submit<Dependency> {
                        if (weak) graph.otherWeak else graph.otherSingleton
                    }.get(5, SECONDS)

                assertSame(graph, independent.owner)
                release.countDown()
                assertSame(graph, blocked.get(5, SECONDS).owner)
            } finally {
                release.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(5, SECONDS))
            }
        }
    }
}

private open class RootGraph(parent: RootGraph? = null) : DiGraph(parent) {
    val singletonCreations = AtomicInteger()
    val weakCreations = AtomicInteger()
    val nullableCreations = AtomicInteger()
    val lazyCreations = AtomicInteger()
    var beforeSingleton: () -> Unit = {}
    var beforeWeak: () -> Unit = {}

    val memberSingleton: Dependency by singleton { Dependency(this) }

    override fun <T> lazyOf(initializer: () -> T): Lazy<T> {
        lazyCreations.incrementAndGet()
        return super.lazyOf(initializer)
    }
}

private open class ChildGraph(parent: RootGraph) : RootGraph(parent)

private class GrandchildGraph(parent: ChildGraph) : ChildGraph(parent)

private class Dependency(val owner: RootGraph)

private class ChildDependency(val owner: ChildGraph, val parentDependency: Dependency)

private val RootGraph.rootSingleton: Dependency by singleton {
    singletonCreations.incrementAndGet()
    beforeSingleton()
    Dependency(this)
}

private val RootGraph.rootWeak: Dependency by weak {
    weakCreations.incrementAndGet()
    beforeWeak()
    Dependency(this)
}

private val RootGraph.otherSingleton: Dependency by singleton { Dependency(this) }

private val RootGraph.otherWeak: Dependency by weak { Dependency(this) }

private val RootGraph.nullableSingleton: Dependency? by singleton {
    nullableCreations.incrementAndGet()
    null
}

private val ChildGraph.childSingleton: ChildDependency by singleton { ChildDependency(this, rootSingleton) }

private val ChildGraph.childWeak: ChildDependency by weak { ChildDependency(this, rootWeak) }

private val GrandchildGraph.grandchildSingleton: Dependency by singleton { Dependency(this) }

private fun RootGraph.read(weak: Boolean): Dependency = if (weak) rootWeak else rootSingleton

private fun RootGraph.beforeRead(
    weak: Boolean,
    initializer: () -> Unit,
) {
    if (weak) beforeWeak = initializer else beforeSingleton = initializer
}

// Keep the only strong reference to the value inside a separate stack frame.
private fun weakDependencyReference(
    graph: RootGraph,
    queue: ReferenceQueue<Dependency>,
): WeakReference<Dependency> = WeakReference(graph.rootWeak, queue)

private fun awaitCollected(
    reference: WeakReference<Dependency>,
    queue: ReferenceQueue<Dependency>,
) {
    repeat(100) {
        System.gc()
        if (queue.remove(50) === reference) return
    }
    fail<Unit>("Weak binding was not garbage collected")
}
