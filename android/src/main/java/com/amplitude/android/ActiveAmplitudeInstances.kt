package com.amplitude.android

import android.app.Application
import java.lang.ref.WeakReference

/**
 * Coordinates the single active Android [Amplitude] for each application and logical instance
 * name.
 *
 * Amplitude's storage, identity, and connector state are already keyed by `instanceName`.
 * Reusing that name therefore replaces the previous logical instance instead of allowing two
 * partially shared instances to remain active.
 */
internal object ActiveAmplitudeInstances {
    private class Slot {
        var activeInstance: WeakReference<Amplitude>? = null
    }

    private class ApplicationKey(application: Application) {
        private val application = WeakReference(application)
        private val identityHashCode = System.identityHashCode(application)

        fun isCleared(): Boolean = application.get() == null

        override fun hashCode(): Int = identityHashCode

        override fun equals(other: Any?): Boolean {
            if (other !is ApplicationKey) {
                return false
            }
            val thisApplication = application.get() ?: return false
            return thisApplication === other.application.get()
        }
    }

    private val slotsLock = Any()
    private val slots = mutableMapOf<ApplicationKey, MutableMap<String, Slot>>()

    fun install(amplitude: Amplitude) {
        val slot =
            synchronized(slotsLock) {
                slots.keys.removeAll { it.isCleared() }
                slots
                    .getOrPut(ApplicationKey(amplitude.replacementApplication)) { mutableMapOf() }
                    .getOrPut(amplitude.replacementInstanceName) { Slot() }
            }
        var previous: Amplitude? = null

        try {
            synchronized(slot) {
                previous = slot.activeInstance?.get()?.takeUnless { it === amplitude }
                previous?.deactivateForReplacement()
                amplitude.activateForReplacement()
                slot.activeInstance = WeakReference(amplitude)
            }
        } finally {
            val previousInstance = previous
            if (previousInstance == null) {
                amplitude.startAfterReplacementCleanup()
            } else {
                previousInstance.finishReplacementCleanup {
                    amplitude.startAfterReplacementCleanup()
                }
            }
        }
    }

    internal fun activeInstance(
        application: Application,
        instanceName: String,
    ): Amplitude? {
        return synchronized(slotsLock) {
            slots[ApplicationKey(application)]?.get(instanceName)?.let { slot ->
                synchronized(slot) {
                    slot.activeInstance?.get()
                }
            }
        }
    }
}
