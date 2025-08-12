package com.amplitude.android

import android.view.View
import androidx.compose.ui.Modifier
import com.amplitude.android.internal.compose.AmpFrustrationIgnoreElement

/**
 * Utility functions for configuring frustration analytics behavior.
 *
 * # Frustration Analytics Ignore Guide
 *
 * Exclude specific UI elements from frustration analytics detection (rage clicks and dead clicks).
 *
 * ## When to Use
 *
 * Use ignore functionality for:
 * - **Navigation elements**: Back buttons, close buttons, drawer toggles
 * - **Multi-click elements**: Increment/decrement buttons, like/favorite buttons
 * - **Loading indicators**: Progress bars, spinners, loading buttons
 * - **Decorative elements**: Non-functional UI components
 *
 * ## Usage Examples
 *
 * ### Android Views (Programmatic)
 * ```kotlin
 * // Ignore all frustration analytics
 * val backButton = findViewById<Button>(R.id.back_button)
 * FrustrationAnalyticsUtils.ignoreFrustrationAnalytics(backButton)
 *
 * // Ignore only rage clicks (allow dead click detection)
 * val incrementButton = findViewById<Button>(R.id.increment_button)
 * FrustrationAnalyticsUtils.ignoreFrustrationAnalytics(
 *     incrementButton,
 *     rageClick = true,
 *     deadClick = false
 * )
 * ```
 *
 * ### Jetpack Compose
 * ```kotlin
 * // Ignore all frustration analytics
 * Button(
 *     onClick = { finish() },
 *     modifier = Modifier.ignoreFrustrationAnalytics()
 * ) { Text("Back") }
 *
 * // Ignore only dead clicks
 * Button(
 *     onClick = { submitForm() },
 *     modifier = Modifier.ignoreFrustrationAnalytics(
 *         rageClick = false,
 *         deadClick = true
 *     )
 * ) { Text("Submit") }
 * ```
 *
 * ## Parameter Combinations
 *
 * | rageClick | deadClick | Behavior |
 * |-----------|-----------|----------|
 * | `true` (default) | `true` (default) | Ignore all frustration analytics |
 * | `true` | `false` | Ignore only rage click detection |
 * | `false` | `true` | Ignore only dead click detection |
 * | `false` | `false` | Track both (does not ignore anything) |
 *
 * @see [ignoreFrustrationAnalytics] for Android Views
 * @see [Modifier.ignoreFrustrationAnalytics] for Jetpack Compose
 *
 * **Note**: Regular interaction events are still tracked when frustration analytics are ignored.
 */
object FrustrationAnalyticsUtils {
    // Private keys for storing ignore flags using View.setTag(key, value)
    // Using hashcodes to avoid conflicts with other tag usage
    private val IGNORE_RAGE_CLICK_KEY = "amplitude_ignore_rage_click".hashCode()
    private val IGNORE_DEAD_CLICK_KEY = "amplitude_ignore_dead_click".hashCode()
    private val IGNORE_FRUSTRATION_KEY = "amplitude_ignore_frustration".hashCode()

    /**
     * Marks an Android View to be ignored for frustration analytics.
     *
     * @param view The view to mark as ignored
     * @param rageClick Whether to ignore rage click detection (default: true)
     * @param deadClick Whether to ignore dead click detection (default: true)
     * @return The same view for chaining
     */
    fun ignoreFrustrationAnalytics(
        view: View,
        rageClick: Boolean = true,
        deadClick: Boolean = true,
    ): View {
        view.setTag(IGNORE_RAGE_CLICK_KEY, rageClick)
        view.setTag(IGNORE_DEAD_CLICK_KEY, deadClick)
        view.setTag(IGNORE_FRUSTRATION_KEY, rageClick && deadClick)
        return view
    }

    /**
     * Checks if an Android View is marked to be ignored for frustration analytics.
     */
    fun isViewIgnored(view: View): Boolean {
        val ignoreAll = view.getTag(IGNORE_FRUSTRATION_KEY) as? Boolean ?: false
        return ignoreAll
    }

    /**
     * Checks if an Android View is marked to be ignored for rage click detection.
     */
    fun isRageClickIgnored(view: View): Boolean {
        val ignoreAll = view.getTag(IGNORE_FRUSTRATION_KEY) as? Boolean ?: false
        val ignoreRage = view.getTag(IGNORE_RAGE_CLICK_KEY) as? Boolean ?: false
        return ignoreAll || ignoreRage
    }

    /**
     * Checks if an Android View is marked to be ignored for dead click detection.
     */
    fun isDeadClickIgnored(view: View): Boolean {
        val ignoreAll = view.getTag(IGNORE_FRUSTRATION_KEY) as? Boolean ?: false
        val ignoreDead = view.getTag(IGNORE_DEAD_CLICK_KEY) as? Boolean ?: false
        return ignoreAll || ignoreDead
    }

    /**
     * Removes the ignore marker from an Android View.
     */
    fun unignoreView(view: View): View {
        view.setTag(IGNORE_RAGE_CLICK_KEY, null)
        view.setTag(IGNORE_DEAD_CLICK_KEY, null)
        view.setTag(IGNORE_FRUSTRATION_KEY, null)
        return view
    }
}

/**
 * Jetpack Compose modifier for ignoring frustration analytics.
 *
 * @param rageClick Whether to ignore rage click detection (default: true)
 * @param deadClick Whether to ignore dead click detection (default: true)
 */
fun Modifier.ignoreFrustrationAnalytics(
    rageClick: Boolean = true,
    deadClick: Boolean = true,
): Modifier {
    return if (!rageClick && !deadClick) {
        // Don't ignore anything, return unmodified
        this
    } else {
        this.then(AmpFrustrationIgnoreElement(rageClick, deadClick))
    }
}
