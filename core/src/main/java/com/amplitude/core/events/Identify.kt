package com.amplitude.core.events

enum class IdentifyOperation(val operation: String) {
    SET("\$set"),
    SET_ONCE("\$setOnce"),
    ADD("\$add"),
    APPEND("\$append"),
    CLEAR_ALL("\$clearAll"),
    PREPEND("\$prepend"),
    UNSET("\$unset"),
    PRE_INSERT("\$preInsert"),
    POST_INSERT("\$postInsert"),
    REMOVE("\$remove")
}

class Identify() {

    internal val propertySet: MutableSet<String> = mutableSetOf()
    internal val properties: MutableMap<String, Any> = mutableMapOf()

    init {
    }

    internal fun setUserProperty(operation: IdentifyOperation, property: String, value: Any) {
    }
}
