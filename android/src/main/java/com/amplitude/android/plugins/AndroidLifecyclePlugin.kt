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

    override fun onActivityCreated(p0: Activity, p1: Bundle?) {
    }

    override fun onActivityStarted(p0: Activity) {
    }

    override fun onActivityResumed(p0: Activity) {
        (amplitude as com.amplitude.android.Amplitude).onEnterForeground(getCurrentTimeMillis());
    }

    override fun onActivityPaused(p0: Activity) {
        (amplitude as com.amplitude.android.Amplitude).onExitForeground(getCurrentTimeMillis());
    }

    override fun onActivityStopped(p0: Activity) {
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
    }

    override fun onActivityDestroyed(p0: Activity) {
    }

    companion object {
        fun getCurrentTimeMillis() : Long {
            return System.currentTimeMillis()
        }
    }
}
