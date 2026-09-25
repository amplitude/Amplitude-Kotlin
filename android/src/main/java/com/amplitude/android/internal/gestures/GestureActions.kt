package com.amplitude.android.internal.gestures

/**
 * Action values shared with the iOS SDK for element interaction events.
 */
internal object GestureActions {
    const val TOUCH = "touch"
    const val PAN = "pan"
    const val LONG_PRESS = "longPress"
    const val PINCH = "pinch"
    const val ROTATION = "rotation"

    val TRANSFORM = setOf(PINCH, ROTATION)
}
