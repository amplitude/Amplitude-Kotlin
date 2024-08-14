package com.amplitude.android.internal.fragments

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import com.amplitude.common.Logger
import java.util.WeakHashMap

internal typealias TrackEventCallback = (String, Map<String, Any?>) -> Unit

internal object FragmentActivityHandler {
    private val callbacksMap =
        WeakHashMap<FragmentActivity, AutocaptureFragmentLifecycleCallbacks>()

    fun Activity.registerFragmentLifecycleCallbacks(track: TrackEventCallback, logger: Logger) {
        (this as? FragmentActivity)?.let { fragmentActivity ->
            val callback = AutocaptureFragmentLifecycleCallbacks(track, logger)
            fragmentActivity.supportFragmentManager
                .registerFragmentLifecycleCallbacks(callback, false)
            callbacksMap[fragmentActivity] = callback
        }
    }

    fun Activity.unregisterFragmentLifecycleCallbacks() {
        (this as? FragmentActivity)?.let { fragmentActivity ->
            callbacksMap[fragmentActivity]?.let { callback ->
                fragmentActivity.supportFragmentManager
                    .unregisterFragmentLifecycleCallbacks(callback)
                callbacksMap.remove(fragmentActivity)
            }
        }
    }
}
