package com.amplitude.android.internal

import com.amplitude.android.Constants.EventProperties.ACTION
import com.amplitude.android.Constants.EventProperties.HIERARCHY
import com.amplitude.android.Constants.EventProperties.SCREEN_NAME
import com.amplitude.android.Constants.EventProperties.TARGET_ACCESSIBILITY_LABEL
import com.amplitude.android.Constants.EventProperties.TARGET_CLASS
import com.amplitude.android.Constants.EventProperties.TARGET_RESOURCE
import com.amplitude.android.Constants.EventProperties.TARGET_SOURCE
import com.amplitude.android.Constants.EventProperties.TARGET_TAG
import com.amplitude.android.Constants.EventProperties.TARGET_TEXT
import com.amplitude.android.internal.gestures.GestureActions
import java.lang.ref.WeakReference

/**
 * Represents a UI element in the view hierarchy from [ViewHierarchyScanner][com.amplitude.android.internal.ViewHierarchyScanner].
 *
 * @property className the class name of the view.
 * @property resourceName the resource name of the view.
 * @property tag the tag of the view.
 */
@Deprecated("Not intended for public use. Will be internal in a future release.")
public data class ViewTarget(
    private val _view: Any?,
    val className: String?,
    val resourceName: String?,
    val tag: String?,
    val text: String?,
    val accessibilityLabel: String?,
    val source: String,
    val hierarchy: String?,
    internal val ampIgnoreRageClick: Boolean = false,
    internal val ampIgnoreDeadClick: Boolean = false,
) {
    /**
     * Action reported for a completed tap. Toggles and value-selection controls use
     * [InteractionAction.ValueChange]; everything else uses [InteractionAction.Touch].
     */
    internal var interactionAction: InteractionAction = InteractionAction.Touch

    /**
     * Elements under the touch that can receive a gesture, outermost first. An Android view
     * consumes the whole touch stream, so only the hit view is listed. Compose passes pointer
     * events a child did not consume up to its ancestors, so every interactive node is listed.
     */
    internal var gestureOwners: List<GestureOwner> =
        listOf(GestureOwner(setOf(GestureActions.TOUCH), tag, accessibilityLabel))

    /**
     * Convenience property to check if ignored for all frustration analytics
     */
    val isIgnoredForFrustration: Boolean
        get() = ampIgnoreRageClick && ampIgnoreDeadClick

    private val viewRef: WeakReference<Any> = WeakReference(_view)

    val view: Any?
        get() = viewRef.get()

    public enum class Type { Clickable }
}

/**
 * An element that declared handlers for [actions], with the identity to report for it.
 */
internal class GestureOwner(
    val actions: Set<String>,
    val tag: String?,
    val accessibilityLabel: String?,
    val interactionAction: InteractionAction = InteractionAction.Touch,
    /**
     * A horizontal drag toggles this element, the way a switch thumb follows the finger.
     * A vertical drag does not: that is a scroll that happened to start on the control.
     */
    val dragReportsValueChange: Boolean = false,
)

/**
 * The target to report for [action]: the innermost owner that handles it, mirroring the iOS SDK
 * reporting the view a recognizer is attached to. Null when no owner handles [action].
 */
internal fun ViewTarget.resolvedFor(action: String): ViewTarget? {
    val owner = gestureOwners.lastOrNull { action in it.actions } ?: return null
    return resolvedAs(owner)
}

internal fun ViewTarget.resolvedAs(owner: GestureOwner): ViewTarget {
    if (owner === gestureOwners.last()) return this
    return copy(tag = owner.tag, accessibilityLabel = owner.accessibilityLabel).apply {
        gestureOwners = listOf(owner)
        interactionAction = owner.interactionAction
    }
}

/**
 * The tap-like action declared by the innermost owner, if any.
 * [GestureActions.VALUE_CHANGE] wins over [GestureActions.TOUCH] on that owner.
 */
internal fun ViewTarget.tapAction(): String? {
    val owner =
        gestureOwners.lastOrNull {
            GestureActions.VALUE_CHANGE in it.actions || GestureActions.TOUCH in it.actions
        } ?: return null
    return if (GestureActions.VALUE_CHANGE in owner.actions) GestureActions.VALUE_CHANGE else GestureActions.TOUCH
}

/**
 * Builds the base properties for ELEMENT_INTERACTED events.
 * This is the foundation used by both standard element tracking and frustration analytics.
 */
@Deprecated("Not intended for public use. Will be internal in a future release.")
public fun buildElementInteractedProperties(
    target: ViewTarget,
    activityName: String,
): Map<String, Any?> = buildElementInteractedProperties(target, activityName, target.interactionAction.eventValue)

internal fun buildElementInteractedProperties(
    target: ViewTarget,
    activityName: String,
    action: String,
): Map<String, Any?> =
    mapOf(
        ACTION to action,
        TARGET_CLASS to target.className,
        TARGET_RESOURCE to target.resourceName,
        TARGET_TAG to target.tag,
        TARGET_TEXT to target.text,
        TARGET_ACCESSIBILITY_LABEL to target.accessibilityLabel,
        TARGET_SOURCE to
            target.source
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
        HIERARCHY to target.hierarchy,
        SCREEN_NAME to activityName,
    )

internal enum class InteractionAction(
    val eventValue: String,
) {
    Touch(GestureActions.TOUCH),
    ValueChange(GestureActions.VALUE_CHANGE),
}
