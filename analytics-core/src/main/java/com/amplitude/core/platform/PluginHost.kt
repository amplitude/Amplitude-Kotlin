package com.amplitude.core.platform

/**
 * Provides lookup of registered [UniversalPlugin] instances.
 */
public interface PluginHost {
    /** Returns the registered plugin with the given [name], or `null` if none matches. */
    public fun plugin(name: String): UniversalPlugin?

    /** Returns all registered plugins that are instances of [clazz]. */
    public fun <T : UniversalPlugin> plugins(clazz: Class<T>): List<T>
}

/** Reified convenience for [PluginHost.plugins]. */
public inline fun <reified T : UniversalPlugin> PluginHost.plugins(): List<T> = plugins(T::class.java)
