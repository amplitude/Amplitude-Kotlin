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
 * [singleton] is retained until this graph is.
 * [weak] is retained only while something else holds it, and is created again if needed later.
 * Currently, no support for scopes, but could be added in the future.
 *
 * ```kotlin
 * internal val MyGraph.myThing: MyThing by singleton { MyThing(appContext) }
 * internal val MyGraph.myClient: MyClient by weak { MyClient() }
 * ```
 */
internal abstract class DiGraph {
    private val singletons: SingletonCache = SingletonCache()
    private val weaks: WeakCache = WeakCache()

    /**
     * Wraps an initializer the caches will run on first read. The default is an unmeasured
     * [lazy]. Override at the platform edge to time or log construction.
     */
    protected open fun <T> lazyOf(initializer: () -> T): Lazy<T> = lazy(initializer)

    /**
     * Declaring the caches private and the factories here keeps the delegates the only way in.
     * Nothing outside this class can reach a cache or key it with a property of its choosing.
     */
    companion object {
        /**
         * Declares a dependency built once per graph instance, on first read. The graph the
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
         * Reads are thread safe and the initializer runs at most once per graph.
         */
        fun <G : DiGraph, T> singleton(initializer: G.() -> T): ReadOnlyProperty<G, T> =
            ReadOnlyProperty { thisRef, property ->
                thisRef.singletons.valueOf(property) { thisRef.initializer() }
            }

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
         * Reads are thread safe.
         */
        fun <G : DiGraph, T : Any> weak(initializer: G.() -> T): ReadOnlyProperty<G, T> =
            ReadOnlyProperty { thisRef, property ->
                thisRef.weaks.valueOf(property) {
                    thisRef.lazyOf { thisRef.initializer() }.value
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
                    entries.getOrPut(property) { WeakEntry<T>() as WeakEntry<*> }
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
