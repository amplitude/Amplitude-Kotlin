package com.amplitude.android.plugins

import com.amplitude.Amplitude
import com.amplitude.platform.Plugin

class AndroidLifecyclePlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var amplitude: Amplitude
}
