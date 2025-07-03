package com.amplitude.core.events

import com.amplitude.common.jvm.ConsoleLogger

enum class IdentifyOperation(val operationType: String) {
    SET("\$set"),
    SET_ONCE("\$setOnce"),
    ADD("\$add"),
    APPEND("\$append"),
    CLEAR_ALL("\$clearAll"),
    PREPEND("\$prepend"),
    UNSET("\$unset"),
    PRE_INSERT("\$preInsert"),
    POST_INSERT("\$postInsert"),
    REMOVE("\$remove"),
}

open class Identify {
    private val propertySet: MutableSet<String> = mutableSetOf()

    private val _properties = mutableMapOf<String, Any?>()
    val properties: MutableMap<String, Any?>
        @Synchronized get() {
            val clonedProperties = _properties.toMutableMap()
            for ((key, value) in clonedProperties) {
                if (value is Map<*, *>) {
                    clonedProperties[key] = value.toMutableMap()
                }
            }
            return clonedProperties
        }

    fun set(
        property: String,
        value: Boolean,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: Double,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: Float,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: Int,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: Long,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: String,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: Map<String, Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: List<Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: Array<Boolean>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: Array<Double>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: Array<Float>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: Array<Int>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: Array<Long>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: Array<String>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun set(
        property: String,
        value: Any,
    ): Identify {
        setUserProperty(IdentifyOperation.SET, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: Boolean,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: Double,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: Float,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: Int,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: Long,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: String,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: Map<String, Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: List<Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: Array<Boolean>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: Array<Double>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: Array<Float>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: Array<Int>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: Array<Long>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun setOnce(
        property: String,
        value: Array<String>,
    ): Identify {
        setUserProperty(IdentifyOperation.SET_ONCE, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: Boolean,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: Double,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: Float,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: Int,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: Long,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: String,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: Map<String, Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: List<Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: Array<Boolean>,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: Array<Double>,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: Array<Float>,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: Array<Int>,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: Array<Long>,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun prepend(
        property: String,
        value: Array<String>,
    ): Identify {
        setUserProperty(IdentifyOperation.PREPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: Boolean,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: Double,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: Float,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: Int,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: Long,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: String,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: Map<String, Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: List<Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: Array<Boolean>,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: Array<Double>,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: Array<Float>,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: Array<Int>,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: Array<Long>,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun append(
        property: String,
        value: Array<String>,
    ): Identify {
        setUserProperty(IdentifyOperation.APPEND, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: Boolean,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: Double,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: Float,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: Int,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: Long,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: String,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: Map<String, Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: List<Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: Array<Boolean>,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: Array<Double>,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: Array<Float>,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: Array<Int>,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: Array<Long>,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun postInsert(
        property: String,
        value: Array<String>,
    ): Identify {
        setUserProperty(IdentifyOperation.POST_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: Boolean,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: Double,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: Float,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: Int,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: Long,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: String,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: Map<String, Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: List<Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: Array<Boolean>,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: Array<Double>,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: Array<Float>,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: Array<Int>,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: Array<Long>,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun preInsert(
        property: String,
        value: Array<String>,
    ): Identify {
        setUserProperty(IdentifyOperation.PRE_INSERT, property, value)
        return this
    }

    fun remove(
        property: String,
        value: Boolean,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: Double,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: Float,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: Int,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: Long,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: String,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: Map<String, Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: List<Any>,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: Array<Boolean>,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: Array<Double>,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: Array<Float>,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: Array<Int>,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: Array<Long>,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun remove(
        property: String,
        value: Array<String>,
    ): Identify {
        setUserProperty(IdentifyOperation.REMOVE, property, value)
        return this
    }

    fun add(
        property: String,
        value: Double,
    ): Identify {
        setUserProperty(IdentifyOperation.ADD, property, value)
        return this
    }

    fun add(
        property: String,
        value: Float,
    ): Identify {
        setUserProperty(IdentifyOperation.ADD, property, value)
        return this
    }

    fun add(
        property: String,
        value: Int,
    ): Identify {
        setUserProperty(IdentifyOperation.ADD, property, value)
        return this
    }

    fun add(
        property: String,
        value: Long,
    ): Identify {
        setUserProperty(IdentifyOperation.ADD, property, value)
        return this
    }

    fun unset(property: String): Identify {
        setUserProperty(IdentifyOperation.UNSET, property, UNSET_VALUE)
        return this
    }

    @Synchronized fun clearAll(): Identify {
        _properties.clear()
        _properties.put(IdentifyOperation.CLEAR_ALL.operationType, UNSET_VALUE)
        return this
    }

    @Synchronized private fun setUserProperty(
        operation: IdentifyOperation,
        property: String,
        value: Any?,
    ) {
        if (property.isEmpty()) {
            ConsoleLogger.logger.warn(
                "Attempting to perform operation ${operation.operationType} with a null or empty string property, ignoring",
            )
            return
        }
        if (value == null) {
            ConsoleLogger.logger.warn(
                "Attempting to perform operation ${operation.operationType} with null value for property $property, ignoring",
            )
            return
        }
        // check that clearAll wasn't already used in this Identify
        if (_properties.containsKey(IdentifyOperation.CLEAR_ALL.operationType)) {
            ConsoleLogger.logger.warn("This Identify already contains a \$clearAll operation, ignoring operation %s")
            return
        }
        // check if property already used in previous operation
        if (propertySet.contains(property)) {
            ConsoleLogger.logger.warn(
                "Already used property $property in previous operation, ignoring operation ${operation.operationType}",
            )
            return
        }
        if (!_properties.containsKey(operation.operationType)) {
            _properties[operation.operationType] = mutableMapOf<String, Any>()
        }
        (_properties[operation.operationType] as MutableMap<String, Any>)[property] = value
        propertySet.add(property)
    }

    companion object {
        const val UNSET_VALUE = "-"
    }
}
