package com.amplitude.android

enum class AutocaptureOption {
    SESSIONS,
    APP_LIFECYCLES,
    DEEP_LINKS,
    SCREEN_VIEWS,
    ELEMENT_INTERACTIONS
}

class AutocaptureOptionsBuilder {
    private val options = mutableSetOf<AutocaptureOption>()

    operator fun AutocaptureOption.unaryPlus() {
        options.add(this)
    }

    val sessions get() = AutocaptureOption.SESSIONS
    val appLifecycles get() = AutocaptureOption.APP_LIFECYCLES
    val deepLinks get() = AutocaptureOption.DEEP_LINKS
    val screenViews get() = AutocaptureOption.SCREEN_VIEWS
    val elementInteractions get() = AutocaptureOption.ELEMENT_INTERACTIONS

    fun build(): MutableSet<AutocaptureOption> = options.toMutableSet()
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
fun autocaptureOptions(init: AutocaptureOptionsBuilder.() -> Unit): MutableSet<AutocaptureOption> {
    return AutocaptureOptionsBuilder().apply(init).build()
}
