package com.amplitude.android.internal.locators

import android.view.View
import android.widget.Button
import androidx.core.view.isVisible
import com.amplitude.android.R
import com.amplitude.android.internal.ViewResourceUtils.resourceIdWithFallback
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.ViewTarget.Type

internal class AndroidViewTargetLocator : ViewTargetLocator {
    private val coordinates = IntArray(2)

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
        val className = javaClass.canonicalName ?: javaClass.simpleName ?: null
        val resourceName = resourceIdWithFallback
        val hierarchy = hierarchy
        val tag =
            tag
                ?.takeIf { it is String || it is Number || it is Boolean || it is Char }
                ?.toString()
        val text = (this as? Button)?.text?.toString()

        // Read custom frustration analytics attributes from XML
        val frustrationSettings = readFrustrationAttributes()

        return ViewTarget(
            this,
            className,
            resourceName,
            tag,
            text,
            SOURCE,
            hierarchy,
            ampIgnoreRageClick = frustrationSettings.ignoreRageClick,
            ampIgnoreDeadClick = frustrationSettings.ignoreDeadClick,
        )
    }

    /**
     * Data class to hold frustration analytics settings from XML attributes
     */
    private data class FrustrationSettings(
        val ignoreRageClick: Boolean = false,
        val ignoreDeadClick: Boolean = false,
    )

    /**
     * Reads frustration analytics settings from programmatic tags and XML attributes.
     * Checks programmatically set flags first, then XML attributes.
     * Note: Does NOT use android:tag for frustration analytics.
     */
    private fun View.readFrustrationAttributes(): FrustrationSettings {
        // Private keys for programmatic ignore flags (must match FrustrationAnalyticsUtils)
        val ignoreRageClickKey = "amplitude_ignore_rage_click".hashCode()
        val ignoreDeadClickKey = "amplitude_ignore_dead_click".hashCode()
        val ignoreFrustrationKey = "amplitude_ignore_frustration".hashCode()

        // Check for programmatically set flags first
        val programmaticIgnoreRage = getTag(ignoreRageClickKey) as? Boolean ?: false
        val programmaticIgnoreDead = getTag(ignoreDeadClickKey) as? Boolean ?: false
        val programmaticIgnoreAll = getTag(ignoreFrustrationKey) as? Boolean ?: false

        // If programmatic flags are set, use those (takes precedence)
        if (programmaticIgnoreRage || programmaticIgnoreDead || programmaticIgnoreAll) {
            return FrustrationSettings(
                ignoreRageClick = programmaticIgnoreRage || programmaticIgnoreAll,
                ignoreDeadClick = programmaticIgnoreDead || programmaticIgnoreAll,
            )
        }

        // Otherwise, try to read custom XML attributes if available
        try {
            val context = context

            // Get the styled attributes for this view using the declare-styleable
            val typedArray =
                context.obtainStyledAttributes(
                    null,
                    R.styleable.AmplitudeFrustrationAnalytics,
                    0,
                    0,
                )

            val ignoreRageFromXml =
                typedArray.getBoolean(
                    R.styleable.AmplitudeFrustrationAnalytics_amplitudeIgnoreRageClick,
                    false,
                )
            val ignoreDeadFromXml =
                typedArray.getBoolean(
                    R.styleable.AmplitudeFrustrationAnalytics_amplitudeIgnoreDeadClick,
                    false,
                )
            val ignoreAllFromXml =
                typedArray.getBoolean(
                    R.styleable.AmplitudeFrustrationAnalytics_amplitudeIgnoreFrustration,
                    false,
                )

            typedArray.recycle()

            // Return XML-based settings
            return FrustrationSettings(
                ignoreRageClick = ignoreRageFromXml || ignoreAllFromXml,
                ignoreDeadClick = ignoreDeadFromXml || ignoreAllFromXml,
            )
        } catch (e: Exception) {
            // Return default (no ignore flags)
            return FrustrationSettings()
        }
    }

    private fun View.touchWithinBounds(position: Pair<Float, Float>): Boolean {
        val (x, y) = position

        getLocationOnScreen(coordinates)
        val vx = coordinates[0]
        val vy = coordinates[1]

        val w = width
        val h = height

        return !(x < vx || x > vx + w || y < vy || y > vy + h)
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
