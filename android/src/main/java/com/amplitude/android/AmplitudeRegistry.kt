package com.amplitude.android

import java.lang.ref.WeakReference

/**
 * Tracks the single active [Amplitude] per instance name. Storage, identity, and the analytics
 * connector are all keyed by instance name, so two instances sharing one cannot both own them.
 */
internal object AmplitudeRegistry {
    private val activeInstances = mutableMapOf<String, WeakReference<Amplitude>>()

    /**
     * Makes [instance] the active owner of its instance name and retires the instance it replaces.
     * The previous instance is only retired once [instance] has started.
     */
    fun activate(instance: Amplitude) {
        val instanceName = instance.configuration.instanceName
        val replaced =
            synchronized(this) {
                activeInstances.values.removeAll { it.get() == null }
                val previous = activeInstances[instanceName]?.get()
                try {
                    instance.startAsActiveInstance()
                } catch (error: Throwable) {
                    // Nothing was claimed and the build does not exist yet, so retiring the
                    // half-built instance is enough; the previous instance keeps the name.
                    instance.markRetired()
                    throw error
                }
                activeInstances[instanceName] = WeakReference(instance)
                // Only instances sharing an Application compete: a different one still holds the
                // previous instance's lifecycle callbacks, so tearing it down owns nothing.
                previous
                    ?.takeIf { it !== instance && it.application === instance.application }
                    ?.takeIf { it.markRetired() }
            }

        replaced?.let {
            it.logger.warn(
                "Amplitude instance '$instanceName' was replaced by a newer instance with the " +
                    "same name and is now inactive.",
            )
            it.retire()
        }
    }

    /**
     * Runs [block] only while [instance] owns its instance name. Atomic against [activate], so a
     * build that finishes after its instance was replaced cannot claim shared state.
     */
    fun runIfActive(
        instance: Amplitude,
        block: () -> Unit,
    ) {
        synchronized(this) {
            if (activeInstances[instance.configuration.instanceName]?.get() === instance) block()
        }
    }
}
