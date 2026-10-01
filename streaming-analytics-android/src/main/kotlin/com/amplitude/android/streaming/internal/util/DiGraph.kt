package com.amplitude.android.streaming.internal.util

import java.lang.ref.WeakReference
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A dependency graph: lazily created objects owned by one instance.
 *
 * Subclass this with the inputs construction needs. Declare each dependency as an extension
 * property on the subclass, ideally in the file that defines its type. The subclass is the
 * initializer's receiver, so its inputs and the other dependencies are in scope.
 *
 * [singleton] is retained until its owning graph is.
 * [weak] is retained only while something else holds it, and is created again if needed later.
 *
 * A scope subclasses its parent graph's type and passes the parent instance to this constructor.
 * A dependency belongs to the furthest ancestor assignable to its declared receiver type.
 * Parent bindings are therefore shared, while bindings declared on the scope belong to that
 * scope instance. Initializers run on the owning graph, even when first accessed through a child.
 * Bindings are additive, not overrides. Nesting another instance of the same graph type shares
 * that type's bindings with the outer instance; use a distinct subtype for a new binding scope.
 *
 * ```kotlin
 * internal val MyGraph.myThing: MyThing by singleton { MyThing(appContext) }
 * internal val MyGraph.myClient: MyClient by weak { MyClient() }
 *
 * internal class MyScope(parent: MyGraph) : MyGraph(parent)
 * internal val MyScope.scopedThing: ScopedThing by singleton { ScopedThing(myThing) }
 * ```
 */
internal abstract class DiGraph(
    private val parent: DiGraph? = null,
) {
    private val singletons: SingletonCache = SingletonCache()
    private val weaks: WeakCache = WeakCache()

    /**
     * Wraps an initializer the caches will run on first read. The default is an unmeasured
     * [lazy]. Override at the platform edge to time or log construction.
     */
    protected open fun <T> lazyOf(initializer: () -> T): Lazy<T> = lazy(initializer)

    private fun <G : DiGraph> ownerOf(graphType: Class<G>): G {
        var owner: DiGraph = this
        while (true) {
            val parent = owner.parent ?: break
            if (!graphType.isInstance(parent)) break
            owner = parent
        }
        return checkNotNull(graphType.cast(owner))
    }

    /**
     * Declaring the caches private and the factories here keeps the delegates the only way in.
     * Nothing outside this class can reach a cache or key it with a property of its choosing.
     */
    companion object {
        /**
         * Declares a dependency built once per owning graph, on first read. The graph the
         * property is declared on is the initializer's receiver, so its inputs and the rest of
         * the graph are in scope.
         *
         * Use this to keep construction in the file that owns the type instead of in the graph
         * class:
         *
         * ```
         * internal val MyGraph.viewCache: ViewCache by singleton {
         *     ViewCache(privacyConfig, logger)
         * }
         * ```
         *
         * Reads through a child scope use the owning ancestor's cache and initializer receiver.
         * Reads are thread safe and successful initialization happens once per owning graph.
         */
        inline fun <reified G : DiGraph, T> singleton(noinline initializer: G.() -> T): ReadOnlyProperty<G, T> =
            singleton(G::class.java, initializer)

        /**
         * Declares a dependency created on first read. The graph the property is declared on is
         * the initializer's receiver, so its inputs and the rest of the graph are in scope.
         *
         * Unlike [singleton], the graph keeps only a [WeakReference]. If nothing else holds the
         * value, it may be collected and the initializer runs again on the next read:
         *
         * ```
         * internal val MyGraph.myClient: MyClient by weak { MyClient() }
         * ```
         *
         * Reads through a child scope use the owning ancestor's cache and initializer receiver.
         * Reads are thread safe.
         */
        inline fun <reified G : DiGraph, T : Any> weak(noinline initializer: G.() -> T): ReadOnlyProperty<G, T> =
            weak(G::class.java, initializer)

        // Keep cache access outside the inline functions: Kotlin does not allow non-private
        // inline functions to call members of private cache classes.
        private fun <G : DiGraph, T> singleton(
            graphType: Class<G>,
            initializer: G.() -> T,
        ): ReadOnlyProperty<G, T> =
            ReadOnlyProperty { thisRef, property ->
                val owner = thisRef.ownerOf(graphType)
                owner.singletons.valueOf(property) { owner.initializer() }
            }

        private fun <G : DiGraph, T : Any> weak(
            graphType: Class<G>,
            initializer: G.() -> T,
        ): ReadOnlyProperty<G, T> =
            ReadOnlyProperty { thisRef, property ->
                val owner = thisRef.ownerOf(graphType)
                owner.weaks.valueOf(property) {
                    owner.lazyOf { owner.initializer() }.value
                }
            }
    }

    /**
     * Holds one lazy value per [singleton] property, created on first read of that property and
     * returned to every read after it.
     *
     * Keying by property rather than by type keeps two dependencies of the same type distinct.
     */
    private inner class SingletonCache {
        private val delegates = mutableMapOf<KProperty<*>, Lazy<*>>()

        @Suppress("UNCHECKED_CAST")
        fun <T> valueOf(
            property: KProperty<*>,
            initializer: () -> T,
        ): T {
            return synchronized(delegates) {
                delegates.getOrPut(property) {
                    lazyOf(initializer)
                }
            }.value as T
        }
    }

    /**
     * Holds one [WeakReference] per [weak] property. A collected value is created again on the
     * next read.
     *
     * Keying by property rather than by type keeps two dependencies of the same type distinct.
     */
    private class WeakCache {
        private val entries = mutableMapOf<KProperty<*>, WeakEntry<*>>()

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> valueOf(
            property: KProperty<*>,
            initializer: () -> T,
        ): T {
            val entry =
                synchronized(entries) {
                    entries.getOrPut(property) { WeakEntry<T>() }
                } as WeakEntry<T>
            return entry.getOrCreate(initializer)
        }

        /**
         * The value of one [weak] property. Each entry locks on itself, so creating one value
         * blocks only readers of that same property, and those readers still share the one value
         * created.
         */
        private class WeakEntry<T : Any> {
            @Volatile
            private var ref: WeakReference<T>? = null

            fun getOrCreate(create: () -> T): T {
                ref?.get()?.let { return it }
                return synchronized(this) {
                    ref?.get() ?: create().also { ref = WeakReference(it) }
                }
            }
        }
    }
}
