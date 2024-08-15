package com.amplitude.android.internal.fragments

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import com.amplitude.common.Logger
import java.util.WeakHashMap

internal typealias TrackEventCallback = (String, Map<String, Any?>) -> Unit

internal object FragmentActivityHandler {
    private val callbacksMap =
        WeakHashMap<FragmentActivity, MutableList<AutocaptureFragmentLifecycleCallbacks>>()

    fun Activity.registerFragmentLifecycleCallbacks(track: TrackEventCallback, logger: Logger) {
        (this as? FragmentActivity)?.apply {
            val callback = AutocaptureFragmentLifecycleCallbacks(track, logger)
            supportFragmentManager.registerFragmentLifecycleCallbacks(callback, false)
            callbacksMap.getOrPut(this) { mutableListOf() }.add(callback)
        }
    }

    fun Activity.unregisterFragmentLifecycleCallbacks() {
        (this as? FragmentActivity)?.let { fragmentActivity ->
            callbacksMap.remove(fragmentActivity)?.forEach {
                fragmentActivity.supportFragmentManager.unregisterFragmentLifecycleCallbacks(it)
            }
        }
    }
}
