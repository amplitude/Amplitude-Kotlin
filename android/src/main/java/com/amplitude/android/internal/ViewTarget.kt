package com.amplitude.android.internal

import com.amplitude.android.internal.FrustrationConstants.IGNORE_FRUSTRATION_COMPOSE_TAG
import com.amplitude.android.internal.FrustrationConstants.IGNORE_FRUSTRATION_TAG
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
) {
    /**
     * Convenience property to check if ignored for all frustration analytics
     */
    val isIgnoredForFrustration: Boolean
        get() = tag == IGNORE_FRUSTRATION_TAG || tag == IGNORE_FRUSTRATION_COMPOSE_TAG
    
    private val viewRef: WeakReference<Any> = WeakReference(_view)

    val view: Any?
        get() = viewRef.get()

    enum class Type { Clickable }
}
