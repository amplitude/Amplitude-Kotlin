package com.amplitude.core.platform.plugins

interface PluginHost {

    fun plugin(name: String): UniversalPlugin?

    // Generics in Kotlin do not support reified types in interfaces,
    // but you can pass the class as a parameter
    fun <T : UniversalPlugin> plugins(type: Class<T>): List<T> {
        // Default implementation: return empty list
        return emptyList()
    }
}