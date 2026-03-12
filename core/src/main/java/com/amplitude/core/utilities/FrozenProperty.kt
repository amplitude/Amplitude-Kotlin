package com.amplitude.core.utilities

import com.amplitude.common.Logger
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A property delegate that freezes a value after construction.
 *
 * On read, returns the initial value.
 * On write, logs a warning and ignores the new value.
 */
fun <T> frozen(
    initial: T,
    logger: Logger,
): ReadWriteProperty<Any, T> =
    object : ReadWriteProperty<Any, T> {
        override fun getValue(
            thisRef: Any,
            property: KProperty<*>,
        ) = initial

        override fun setValue(
            thisRef: Any,
            property: KProperty<*>,
            value: T,
        ) {
            logger.warn(
                "Property '${property.name}' is frozen and cannot be modified after construction. " +
                    "Use ConfigurationBuilder to set this value.",
            )
        }
    }
