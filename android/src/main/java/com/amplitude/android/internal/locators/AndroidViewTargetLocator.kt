package com.amplitude.android.internal.locators

import android.view.View
import android.widget.Button
import androidx.core.view.isVisible
import com.amplitude.android.internal.ViewResourceUtils.resourceIdWithFallback
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.ViewTarget.Type

internal class AndroidViewTargetLocator : ViewTargetLocator {
    companion object {
        private const val HIERARCHY_DELIMITER = " → "

        private const val SOURCE = "android_view"
    }

    override fun Any.locate(
        targetPosition: Pair<Float, Float>,
        targetType: Type,
    ): ViewTarget? =
        (this as? View)
            ?.takeIf { touchWithinBounds(targetPosition) && targetType === Type.Clickable && isViewTappable() }
            ?.let { createViewTarget() }

    private fun View.createViewTarget(): ViewTarget {
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
        )
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

    private fun View.isViewTappable(): Boolean = isClickable && isVisible

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
