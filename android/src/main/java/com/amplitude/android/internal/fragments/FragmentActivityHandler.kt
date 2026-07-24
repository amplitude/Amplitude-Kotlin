package com.amplitude.android.internal.fragments

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import com.amplitude.android.internal.TrackFunction
import com.amplitude.common.Logger
import java.util.WeakHashMap

internal object FragmentActivityHandler {
    private val callbacksMap =
        WeakHashMap<FragmentActivity, MutableMap<TrackFunction, AutocaptureFragmentLifecycleCallbacks>>()

    fun Activity.registerFragmentLifecycleCallbacks(
        track: TrackFunction,
        logger: Logger,
        screenViewsEnabled: () -> Boolean = { true },
    ) {
        (this as? FragmentActivity)?.apply {
            val callbacks = callbacksMap.getOrPut(this) { mutableMapOf() }
            if (track in callbacks) {
                return
            }
            val callback = AutocaptureFragmentLifecycleCallbacks(track, logger, screenViewsEnabled)
            supportFragmentManager.registerFragmentLifecycleCallbacks(callback, true)
            callbacks[track] = callback
        } ?: logger.debug("Activity is not a FragmentActivity")
    }

    fun Activity.unregisterFragmentLifecycleCallbacks(
        track: TrackFunction,
        logger: Logger,
    ) {
        (this as? FragmentActivity)?.apply {
            callbacksMap[this]?.let { callbacks ->
                callbacks.remove(track)?.let { callback ->
                    supportFragmentManager.unregisterFragmentLifecycleCallbacks(callback)
                }
                if (callbacks.isEmpty()) {
                    callbacksMap.remove(this)
                }
            }
        } ?: logger.debug("Activity is not a FragmentActivity")
    }
}
