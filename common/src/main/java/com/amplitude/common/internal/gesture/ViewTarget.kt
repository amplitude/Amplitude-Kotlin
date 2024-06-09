package com.amplitude.common.internal.gesture

import java.lang.ref.WeakReference

/**
 * Represents a UI element in the view hierarchy from [ViewHierarchyScanner][com.amplitude.android.internal.ViewHierarchyScanner].
 *
 * @property className the class name of the view.
 * @property recourseName the resource name of the view.
 * @property tag the tag of the view.
 */
data class ViewTarget(
    private val _view: Any?,
    val className: String?,
    val recourseName: String?,
    val tag: String?,
    val source: String,
) {
    private val viewRef: WeakReference<Any> = WeakReference(_view)

    val view: Any?
        get() = viewRef.get()

    enum class Type {
        Clickable,
        Scrollable,
    }
}
