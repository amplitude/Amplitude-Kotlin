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
     * Enable screen view tracking for Activities and Fragments (including nested ones).
     */
    SCREEN_VIEWS,

    /**
     * Enable element interaction tracking.
     */
    ELEMENT_INTERACTIONS,

    /**
     * Enable frustration interactions tracking (rage click and dead click).
     */
    FRUSTRATION_INTERACTIONS,

    ;

    companion object {
        /**
         * Set containing all available autocapture options.
         *
         * Equivalent to enabling all autocapture features:
         * - SESSIONS
         * - APP_LIFECYCLES
         * - DEEP_LINKS
         * - SCREEN_VIEWS
         * - ELEMENT_INTERACTIONS
         * - FRUSTRATION_INTERACTIONS
         */
        val ALL =
            setOf(
                SESSIONS,
                APP_LIFECYCLES,
                DEEP_LINKS,
                SCREEN_VIEWS,
                ELEMENT_INTERACTIONS,
                FRUSTRATION_INTERACTIONS,
            )
    }
}

class AutocaptureOptionsBuilder {
    private val options = mutableSetOf<AutocaptureOption>()

    operator fun AutocaptureOption.unaryPlus() {
        options.add(this)
    }

    /**
     * Adds all autocapture options when invoked with unary plus operator.
     */
    fun addAll() {
        options.addAll(AutocaptureOption.ALL)
    }

    val sessions = AutocaptureOption.SESSIONS
    val appLifecycles = AutocaptureOption.APP_LIFECYCLES
    val deepLinks = AutocaptureOption.DEEP_LINKS
    val screenViews = AutocaptureOption.SCREEN_VIEWS
    val elementInteractions = AutocaptureOption.ELEMENT_INTERACTIONS
    val frustrationInteractions = AutocaptureOption.FRUSTRATION_INTERACTIONS

    fun build(): Set<AutocaptureOption> = options.toSet()
}

/**
 * Helper function to create a set of autocapture options.
 *
 * Example usage:
 * ```
 * // Enable all autocapture options
 * val allOptions = AutocaptureOption.ALL
 *
 * // Or use the builder for selective options
 * val options = autocaptureOptions {
 *    +sessions
 *    +appLifecycles
 *    +deepLinks
 *    +screenViews
 *    +elementInteractions
 *    +frustrationInteractions
 * }
 *
 * // Or enable all via builder
 * val allViaBuilder = autocaptureOptions {
 *    addAll()
 * }
 * ```
 *
 * @param init Function to build the set of autocapture options.
 * @return Set of autocapture options.
 */
fun autocaptureOptions(init: AutocaptureOptionsBuilder.() -> Unit): Set<AutocaptureOption> = AutocaptureOptionsBuilder().apply(init).build()
