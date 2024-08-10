package com.amplitude.android.internal.fragments

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import com.amplitude.core.Amplitude

object FragmentActivityHandler {
    fun Activity.attachFragmentLifecycleCallbacks(amplitude: Amplitude) {
        (this as? FragmentActivity)?.supportFragmentManager?.registerFragmentLifecycleCallbacks(
            AutocaptureFragmentLifecycleCallbacks(amplitude::track, amplitude.logger),
            false
        )
    }
}
