package com.amplitude.android.internal

import android.view.View
import java.lang.ref.WeakReference

/**
 * Represents a UI element in the view hierarchy from [ViewHierarchyScanner].
 *
 * @property className the class name of the view.
 * @property recourseName the resource name of the view.
 * @property tag the tag of the view.
 */
internal data class ViewTarget(
    private val _view: View?,
    val className: String?,
    val recourseName: String?,
    val tag: String?,
    val source: String,
) {
    private val viewRef: WeakReference<View> = WeakReference(_view)

    val view: View?
        get() = viewRef.get()

    enum class Type { Clickable }
}
