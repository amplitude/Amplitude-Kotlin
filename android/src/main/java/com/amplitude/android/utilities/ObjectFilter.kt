package com.amplitude.android.utilities

internal class ObjectFilter(
    allowList: List<String> = emptyList(),
    blockList: List<String> = emptyList(),
) {
    private val allowKeyPaths: List<List<String>> = allowList.mapNotNull { parseKeyPath(it) }
    private val blockKeyPaths: List<List<String>> = blockList.mapNotNull { parseKeyPath(it) }

    fun filtered(obj: Any?): Any? {
        if (obj == null || allowKeyPaths.isEmpty()) return null
        return when (obj) {
            is Map<*, *> -> filterMap(obj.filterIsStringKey(), emptyList())
            is List<*> -> filterList(obj, emptyList())
            else -> null
        }
    }

    private fun filterMap(
        map: Map<String, Any?>,
        path: List<String>,
    ): Map<String, Any?>? {
        val result = mutableMapOf<String, Any?>()
        for ((key, value) in map) {
            val newPath = path + key
            if (isBlocked(newPath) || !canInclude(newPath)) continue

            val filtered = processValue(value, newPath)
            if (filtered != null) {
                result[key] = filtered
            } else if (shouldPreserveEmpty(newPath, value)) {
                result[key] = emptyContainer(value)
            }
        }
        return result.ifEmpty { null }
    }

    private fun filterList(
        list: List<*>,
        path: List<String>,
    ): List<Any?>? {
        val result = mutableListOf<Any?>()
        for ((index, value) in list.withIndex()) {
            val newPath = path + index.toString()
            if (isBlocked(newPath) || !canInclude(newPath)) continue

            val filtered = processValue(value, newPath)
            if (filtered != null) {
                result.add(filtered)
            } else if (shouldPreserveEmpty(newPath, value)) {
                result.add(emptyContainer(value))
            }
        }
        return result.ifEmpty { null }
    }

    private fun processValue(
        value: Any?,
        at: List<String>,
    ): Any? {
        if (value !is Map<*, *> && value !is List<*>) {
            return if (isAllowed(at)) value else null
        }

        // Return empty containers that match patterns
        if (isAllowed(at)) {
            if (value is Map<*, *> && value.isEmpty()) return value
            if (value is List<*> && value.isEmpty()) return value
        }

        // Recursively filter containers
        return when (value) {
            is Map<*, *> -> filterMap(value.filterIsStringKey(), at)
            is List<*> -> filterList(value, at)
            else -> null
        }
    }

    private fun shouldPreserveEmpty(
        path: List<String>,
        value: Any?,
    ): Boolean {
        if (value !is Map<*, *> && value !is List<*>) return false
        return allowKeyPaths.any { pattern ->
            pattern.size == path.size &&
                pattern.last() == "*" &&
                matches(path.dropLast(1), pattern.dropLast(1))
        }
    }

    private fun emptyContainer(value: Any?): Any {
        return if (value is List<*>) emptyList<Any?>() else emptyMap<String, Any?>()
    }

    private fun canInclude(path: List<String>): Boolean {
        return allowKeyPaths.any { pattern ->
            matches(path, pattern) || canMatchDescendants(path, pattern)
        }
    }

    private fun canMatchDescendants(
        path: List<String>,
        pattern: List<String>,
    ): Boolean {
        if (pattern.size <= path.size) return pattern.contains("**")
        for (i in path.indices) {
            if (pattern[i] == "**") return true
            if (pattern[i] != path[i] && pattern[i] != "*") return false
        }
        return true
    }

    private fun isAllowed(path: List<String>): Boolean {
        return allowKeyPaths.any { matches(path, it) } && !isBlocked(path)
    }

    private fun isBlocked(path: List<String>): Boolean {
        return blockKeyPaths.any { matches(path, it) }
    }

    internal fun matches(
        path: List<String>,
        pattern: List<String>,
    ): Boolean {
        if (path.isEmpty() && pattern.isEmpty()) return true
        if (pattern == listOf("**")) return true
        return matchImpl(path, pattern, 0, 0)
    }

    private fun matchImpl(
        path: List<String>,
        pattern: List<String>,
        pIdx: Int,
        patIdx: Int,
    ): Boolean {
        if (pIdx == path.size && patIdx == pattern.size) return true
        if (patIdx == pattern.size) return false

        val current = pattern[patIdx]

        if (current == "**") {
            if (patIdx == pattern.size - 1) return true
            if (matchImpl(path, pattern, pIdx, patIdx + 1)) return true
            for (i in pIdx until path.size) {
                if (matchImpl(path, pattern, i + 1, patIdx + 1)) return true
            }
            return false
        }

        if (pIdx == path.size) return false

        return (current == "*" || current == path[pIdx]) &&
            matchImpl(path, pattern, pIdx + 1, patIdx + 1)
    }

    companion object {
        private fun parseKeyPath(path: String): List<String>? {
            if (path.isEmpty()) return null
            val components = path.split("/").filter { it.isNotEmpty() }
            return components.ifEmpty { null }
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun Map<*, *>.filterIsStringKey(): Map<String, Any?> {
    return this.filterKeys { it is String } as Map<String, Any?>
}
