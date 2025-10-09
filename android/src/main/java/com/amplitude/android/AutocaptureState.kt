package com.amplitude.android

import com.amplitude.android.AutocaptureOption.APP_LIFECYCLES
import com.amplitude.android.AutocaptureOption.DEEP_LINKS
import com.amplitude.android.AutocaptureOption.ELEMENT_INTERACTIONS
import com.amplitude.android.AutocaptureOption.FRUSTRATION_INTERACTIONS
import com.amplitude.android.AutocaptureOption.SCREEN_VIEWS
import com.amplitude.android.AutocaptureOption.SESSIONS

/**
 * State representing which autocapture features are enabled.
 * Built once from [AutocaptureOption] set and [InteractionsOptions] to provide
 * a single source of truth for feature checks throughout the codebase.
 *
 * @suppress This is an internal implementation detail and should not be used directly.
 */
data class AutocaptureState(
    val sessions: Boolean = false,
    val appLifecycles: Boolean = false,
    val screenViews: Boolean = false,
    val deepLinks: Boolean = false,
    val interactions: List<InteractionType> = emptyList(),
) {
    companion object {
        /**
         * Builds [AutocaptureState] from autocapture options and interactions configuration.
         */
        fun from(
            autocapture: Set<AutocaptureOption>,
            interactionsOptions: InteractionsOptions,
        ): AutocaptureState {
            val interactions =
                buildList {
                    if (ELEMENT_INTERACTIONS in autocapture) {
                        add(InteractionType.ElementInteraction)
                    }
                    if (FRUSTRATION_INTERACTIONS in autocapture) {
                        if (interactionsOptions.rageClick.enabled) {
                            add(InteractionType.RageClick)
                        }
                        if (interactionsOptions.deadClick.enabled) {
                            add(InteractionType.DeadClick)
                        }
                    }
                }

            return AutocaptureState(
                sessions = SESSIONS in autocapture,
                appLifecycles = APP_LIFECYCLES in autocapture,
                screenViews = SCREEN_VIEWS in autocapture,
                deepLinks = DEEP_LINKS in autocapture,
                interactions = interactions,
            )
        }
    }
}

/**
 * Types of user interaction tracking.
 */
sealed class InteractionType {
    /**
     * Standard element interaction events (ELEMENT_INTERACTED).
     */
    data object ElementInteraction : InteractionType()

    /**
     * Rage click [FRUSTRATION_INTERACTIONS] events (RAGE_CLICK).
     */
    data object RageClick : InteractionType()

    /**
     * Dead click [FRUSTRATION_INTERACTIONS] events (DEAD_CLICK).
     */
    data object DeadClick : InteractionType()
}
