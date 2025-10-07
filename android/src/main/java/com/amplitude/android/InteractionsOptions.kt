package com.amplitude.android

/**
 * Configuration options for granular control over [AutocaptureOption.FRUSTRATION_INTERACTIONS] tracking.
 *
 * Example usage:
 * ```
 * val config = Configuration(
 *     apiKey = "YOUR_API_KEY",
 *     context = application,
 *     autocapture = setOf(AutocaptureOption.FRUSTRATION_INTERACTIONS),
 *     interactionsOptions = InteractionsOptions(
 *         rageClick = RageClickOptions(enabled = true),
 *         deadClick = DeadClickOptions(enabled = false)
 *     )
 * )
 * ```
 */
data class InteractionsOptions(
    val rageClick: RageClickOptions = RageClickOptions(),
    val deadClick: DeadClickOptions = DeadClickOptions()
)

/**
 * Configuration options for rage click detection.
 *
 * Rage clicks occur when a user rapidly clicks the same element multiple times
 * within a short time window, indicating frustration.
 */
data class RageClickOptions(
    val enabled: Boolean = true
)

/**
 * Configuration options for dead click detection.
 *
 * Dead clicks occur when a user clicks an element but no UI change is detected,
 * suggesting the click had no effect or the UI is unresponsive.
 */
data class DeadClickOptions(
    val enabled: Boolean = true
)
