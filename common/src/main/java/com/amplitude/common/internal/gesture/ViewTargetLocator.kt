package com.amplitude.common.internal.gesture

fun interface ViewTargetLocator {
    /**
     * Locates a [ViewTarget] at the given position based on the view type.
     *
     * @param targetPosition the position to locate the view target at.
     * @param targetType the type of the view target to locate.
     * @return the [ViewTarget] at the given position, or null if none was found.
     */
    fun Any.locate(
        targetPosition: Pair<Float, Float>,
        targetType: ViewTarget.Type,
    ): ViewTarget?
}
