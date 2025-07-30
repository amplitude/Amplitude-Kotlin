package com.amplitude.android.internal

import android.app.Activity
import com.amplitude.android.internal.FrustrationConstants.IGNORE_FRUSTRATION_TAG
import com.amplitude.android.utilities.DefaultEventUtils.Companion.screenName
import com.amplitude.core.Constants.EventProperties.ACTION
import com.amplitude.core.Constants.EventProperties.HIERARCHY
import com.amplitude.core.Constants.EventProperties.SCREEN_NAME
import com.amplitude.core.Constants.EventProperties.TARGET_CLASS
import com.amplitude.core.Constants.EventProperties.TARGET_RESOURCE
import com.amplitude.core.Constants.EventProperties.TARGET_SOURCE
import com.amplitude.core.Constants.EventProperties.TARGET_TAG
import com.amplitude.core.Constants.EventProperties.TARGET_TEXT
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
    internal val ampIgnoreRageClick: Boolean = false,
    internal val ampIgnoreDeadClick: Boolean = false,
) {
    /**
     * Convenience property to check if ignored for all frustration analytics
     */
    val isIgnoredForFrustration: Boolean
        get() = tag == IGNORE_FRUSTRATION_TAG || (ampIgnoreRageClick && ampIgnoreDeadClick)

    private val viewRef: WeakReference<Any> = WeakReference(_view)

    val view: Any?
        get() = viewRef.get()

    enum class Type { Clickable }
}

/**
 * Builds the base properties for ELEMENT_INTERACTED events.
 * This is the foundation used by both standard element tracking and frustration analytics.
 */
fun buildElementInteractedProperties(
    target: ViewTarget,
    activity: Activity,
): Map<String, Any?> =
    mapOf(
        ACTION to "touch",
        TARGET_CLASS to target.className,
        TARGET_RESOURCE to target.resourceName,
        TARGET_TAG to target.tag,
        TARGET_TEXT to target.text,
        TARGET_SOURCE to
            target.source
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
        HIERARCHY to target.hierarchy,
        SCREEN_NAME to activity.screenName,
    )
