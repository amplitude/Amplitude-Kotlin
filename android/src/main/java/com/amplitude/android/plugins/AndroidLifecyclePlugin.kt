package com.amplitude.android.plugins

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.amplitude.android.Configuration
import com.amplitude.core.Amplitude
import com.amplitude.core.platform.Plugin

class AndroidLifecyclePlugin : Application.ActivityLifecycleCallbacks, Plugin {
    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var amplitude: Amplitude

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        ((amplitude.configuration as Configuration).context as Application).registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
        (amplitude as com.amplitude.android.Amplitude).onEnterForeground(getCurrentTimeMillis())
    }

    override fun onActivityPaused(activity: Activity) {
        (amplitude as com.amplitude.android.Amplitude).onExitForeground(getCurrentTimeMillis())
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    companion object {
        fun getCurrentTimeMillis(): Long {
            return System.currentTimeMillis()
        }
    }
}
