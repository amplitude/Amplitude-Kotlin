package com.amplitude.core.platform.plugins

import com.amplitude.core.platform.Plugin

interface PluginHost {

    fun plugin(name: String): UniversalPlugin? = null

    // Generics in Kotlin do not support reified types in interfaces,
    // but you can pass the class as a parameter
    fun plugins(type: Plugin.Type): List<UniversalPlugin> {
        // Default implementation: return empty list
        return emptyList()
    }
}