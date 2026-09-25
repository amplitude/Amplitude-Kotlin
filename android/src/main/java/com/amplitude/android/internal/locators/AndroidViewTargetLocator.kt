package com.amplitude.android.internal.locators

import android.view.View
import android.widget.AbsSeekBar
import android.widget.Button
import android.widget.Switch
import androidx.core.view.isVisible
import com.amplitude.android.internal.GestureOwner
import com.amplitude.android.internal.ViewResourceUtils.resourceIdWithFallback
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.ViewTarget.Type
import com.amplitude.android.internal.gestures.GestureActions

internal class AndroidViewTargetLocator : ViewTargetLocator {
    companion object {
        private const val HIERARCHY_DELIMITER = " → "

        private const val SOURCE = "android_view"

        /**
         * Framework and AndroidX views that move a thumb with the finger, so they handle
         * drag gestures without exposing a flag like [View.isClickable].
         */
        private val DRAGGABLE_VIEW_TYPES =
            setOf(
                "androidx.appcompat.widget.SwitchCompat",
                "com.google.android.material.slider.BaseSlider",
            )
    }

    override fun Any.locate(
        targetPosition: Pair<Float, Float>,
        targetType: Type,
    ): ViewTarget? =
        (this as? View)
            ?.takeIf { touchWithinBounds(targetPosition) && targetType === Type.Clickable && isVisible }
            ?.let { view ->
                val actions = view.declaredGestures()
                view.takeIf { actions.isNotEmpty() }?.createViewTarget(actions)
            }

    /**
     * Gestures this view declared. A view that declares any of them consumes the touch stream,
     * so its ancestors never receive the gesture.
     */
    private fun View.declaredGestures(): Set<String> =
        buildSet {
            if (isClickable) add(GestureActions.TOUCH)
            if (isLongClickable) add(GestureActions.LONG_PRESS)
            if (isDraggableViewType()) add(GestureActions.PAN)
        }

    private fun View.isDraggableViewType(): Boolean =
        this is AbsSeekBar ||
            this is Switch ||
            generateSequence(javaClass as Class<*>?) { it.superclass }
                .any { it.name in DRAGGABLE_VIEW_TYPES }

    private fun View.createViewTarget(actions: Set<String>): ViewTarget {
        val className = javaClass.canonicalName ?: javaClass.simpleName
        val resourceName = resourceIdWithFallback
        val hierarchy = hierarchy
        val tag =
            tag
                ?.takeIf { it is String || it is Number || it is Boolean || it is Char }
                ?.toString()
        val text = (this as? Button)?.text?.toString()
        val accessibilityLabel = contentDescription?.toString()

        // Read frustration analytics settings from programmatic tags (and Compose)
        val frustrationSettings = readFrustrationAttributes()

        return ViewTarget(
            this,
            className,
            resourceName,
            tag,
            text,
            accessibilityLabel,
            SOURCE,
            hierarchy,
            ampIgnoreRageClick = frustrationSettings.ignoreRageClick,
            ampIgnoreDeadClick = frustrationSettings.ignoreDeadClick,
        ).apply {
            gestureOwners = listOf(GestureOwner(actions, tag, accessibilityLabel))
        }
    }

    /**
     * Data class to hold frustration analytics settings
     */
    private data class FrustrationSettings(
        val ignoreRageClick: Boolean = false,
        val ignoreDeadClick: Boolean = false,
    )

    /**
     * Reads frustration analytics settings from programmatic tags.
     */
    private fun View.readFrustrationAttributes(): FrustrationSettings {
        // Private keys for programmatic ignore flags (must match FrustrationAnalyticsUtils)
        val ignoreRageClickKey = "amplitude_ignore_rage_click".hashCode()
        val ignoreDeadClickKey = "amplitude_ignore_dead_click".hashCode()
        val ignoreFrustrationKey = "amplitude_ignore_frustration".hashCode()

        // Check for programmatically set flags
        val programmaticIgnoreRage = getTag(ignoreRageClickKey) as? Boolean ?: false
        val programmaticIgnoreDead = getTag(ignoreDeadClickKey) as? Boolean ?: false
        val programmaticIgnoreAll = getTag(ignoreFrustrationKey) as? Boolean ?: false

        return FrustrationSettings(
            ignoreRageClick = programmaticIgnoreRage || programmaticIgnoreAll,
            ignoreDeadClick = programmaticIgnoreDead || programmaticIgnoreAll,
        )
    }

    private fun View.touchWithinBounds(position: Pair<Float, Float>): Boolean {
        val (x, y) = position // Window coordinates

        // Get window-relative position of the view
        val rootCoordinates = IntArray(2)
        val coordinates = IntArray(2)

        rootView.getLocationOnScreen(rootCoordinates)
        getLocationOnScreen(coordinates)

        val viewX = coordinates[0] - rootCoordinates[0]
        val viewY = coordinates[1] - rootCoordinates[1]

        return x >= viewX && x <= viewX + width && y >= viewY && y <= viewY + height
    }

    private val View.hierarchy: String
        get() {
            val hierarchy = mutableListOf<String>()
            var currentView: View? = this
            while (currentView != null) {
                hierarchy.add(currentView.javaClass.simpleName)
                currentView = currentView.parent as? View
            }
            return hierarchy.joinToString(separator = HIERARCHY_DELIMITER)
        }
}
