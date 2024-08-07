package com.amplitude.android

@Suppress("DEPRECATION")
@Deprecated("Use AutocaptureOption instead")
open class DefaultTrackingOptions
@JvmOverloads
constructor(
    sessions: Boolean = true,
    appLifecycles: Boolean = false,
    deepLinks: Boolean = false,
    screenViews: Boolean = false,
) {
    // Prebuilt options for easier usage
    companion object {
        @JvmField
        @Deprecated("Use AutocaptureOption instead.")
        val ALL =
            DefaultTrackingOptions(
                sessions = true,
                appLifecycles = true,
                deepLinks = true,
                screenViews = true,
            )

        @JvmField
        @Deprecated("Use AutocaptureOption instead.")
        val NONE =
            DefaultTrackingOptions(
                sessions = false,
                appLifecycles = false,
                deepLinks = false,
                screenViews = false,
            )
    }

    var sessions: Boolean = sessions
        set(value) {
            field = value
            notifyChanged()
        }

    var appLifecycles: Boolean = appLifecycles
        set(value) {
            field = value
            notifyChanged()
        }

    var deepLinks: Boolean = deepLinks
        set(value) {
            field = value
            notifyChanged()
        }

    var screenViews: Boolean = screenViews
        set(value) {
            field = value
            notifyChanged()
        }

    private val propertyChangeListeners: MutableList<DefaultTrackingOptions.() -> Unit> = mutableListOf()

    internal val autocaptureOptions: MutableSet<AutocaptureOption>
        get() = mutableSetOf<AutocaptureOption>().apply {
            if (sessions) add(AutocaptureOption.SESSIONS)
            if (appLifecycles) add(AutocaptureOption.APP_LIFECYCLES)
            if (deepLinks) add(AutocaptureOption.DEEP_LINKS)
            if (screenViews) add(AutocaptureOption.SCREEN_VIEWS)
        }

    private fun notifyChanged() {
        for (listener in propertyChangeListeners) {
            this.listener()
        }
    }

    internal constructor(listener: (DefaultTrackingOptions.() -> Unit)) : this() {
        propertyChangeListeners.add(listener)
    }

    internal fun addPropertyChangeListener(listener: DefaultTrackingOptions.() -> Unit) {
        propertyChangeListeners.add(listener)
    }
}
