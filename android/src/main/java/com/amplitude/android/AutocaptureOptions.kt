package com.amplitude.android

/**
 * Autocapture options to enable or disable specific types of autocapture events.
 */
enum class AutocaptureOption {
    /**
     * Enable session tracking.
     */
    SESSIONS,

    /**
     * Enable app lifecycle tracking.
     */
    APP_LIFECYCLES,

    /**
     * Enable deep link tracking.
     */
    DEEP_LINKS,

    /**
     * Enable screen view tracking.
     */
    SCREEN_VIEWS,

    /**
     * Enable element interaction tracking.
     */
    ELEMENT_INTERACTIONS,
}

class AutocaptureOptionsBuilder {
    private val options = mutableSetOf<AutocaptureOption>()

    operator fun AutocaptureOption.unaryPlus() {
        options.add(this)
    }

    val sessions = AutocaptureOption.SESSIONS
    val appLifecycles = AutocaptureOption.APP_LIFECYCLES
    val deepLinks = AutocaptureOption.DEEP_LINKS
    val screenViews = AutocaptureOption.SCREEN_VIEWS
    val elementInteractions = AutocaptureOption.ELEMENT_INTERACTIONS

    fun build(): Set<AutocaptureOption> = options.toSet()
}

/**
 * Helper function to create a set of autocapture options.
 *
 * Example usage:
 * ```
 * val options = autocaptureOptions {
 *    +sessions
 *    +appLifecycles
 *    +deepLinks
 *    +screenViews
 *    +elementInteractions
 * }
 * ```
 *
 * @param init Function to build the set of autocapture options.
 * @return Set of autocapture options.
 */
fun autocaptureOptions(init: AutocaptureOptionsBuilder.() -> Unit): Set<AutocaptureOption> {
    return AutocaptureOptionsBuilder().apply(init).build()
}
