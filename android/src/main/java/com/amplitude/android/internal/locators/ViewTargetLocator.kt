package com.amplitude.android.internal.locators

import com.amplitude.android.internal.ViewTarget

fun interface ViewTargetLocator {
    /**
     * Locates a [ViewTarget] at the given position based on the [View] type.
     *
     * @param position the position to locate the view target at.
     * @param targetType the type of the view target to locate.
     * @return the [ViewTarget] at the given position, or null if none was found.
     */
    fun Any.locate(
        position: Pair<Float, Float>,
        targetType: ViewTarget.Type,
    ): ViewTarget?
}
