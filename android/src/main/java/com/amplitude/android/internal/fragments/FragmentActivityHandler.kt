package com.amplitude.android.internal.fragments

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import com.amplitude.android.internal.TrackFunction
import com.amplitude.common.Logger
import java.util.WeakHashMap

internal object FragmentActivityHandler {
    private val callbacksMap =
        WeakHashMap<FragmentActivity, MutableList<AutocaptureFragmentLifecycleCallbacks>>()

    fun Activity.registerFragmentLifecycleCallbacks(
        track: TrackFunction,
        logger: Logger,
    ) {
        (this as? FragmentActivity)?.apply {
            val callback = AutocaptureFragmentLifecycleCallbacks(track, logger)
            supportFragmentManager.registerFragmentLifecycleCallbacks(callback, true)
            callbacksMap.getOrPut(this) { mutableListOf() }.add(callback)
        } ?: logger.debug("Activity is not a FragmentActivity")
    }

    fun Activity.unregisterFragmentLifecycleCallbacks(logger: Logger) {
        (this as? FragmentActivity)?.apply {
            callbacksMap.remove(this)?.let { callbacks ->
                for (callback in callbacks) {
                    supportFragmentManager.unregisterFragmentLifecycleCallbacks(callback)
                }
            }
        } ?: logger.debug("Activity is not a FragmentActivity")
    }
}
