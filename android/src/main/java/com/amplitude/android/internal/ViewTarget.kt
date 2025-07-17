package com.amplitude.android.internal

import java.lang.ref.WeakReference

/**
 * Represents a UI element in the view hierarchy from [ViewHierarchyScanner][com.amplitude.android.internal.ViewHierarchyScanner].
 *
 * @property className the class name of the view.
 * @property resourceName the resource name of the view.
 * @property tag the tag of the view.
 */
data class ViewTarget(
    private val _view: Any?,
    val className: String?,
    val resourceName: String?,
    val tag: String?,
    val text: String?,
    val source: String,
    val hierarchy: String?,
    val isIgnoredForRageClick: Boolean = false,
    val isIgnoredForDeadClick: Boolean = false,
) {
    /**
     * Convenience property to check if ignored for all frustration analytics
     */
    val isIgnoredForFrustration: Boolean
        get() = isIgnoredForRageClick && isIgnoredForDeadClick
    private val viewRef: WeakReference<Any> = WeakReference(_view)

    val view: Any?
        get() = viewRef.get()

    enum class Type { Clickable }
}
