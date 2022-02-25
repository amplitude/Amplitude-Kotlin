package com.amplitude.android.plugins

import com.amplitude.core.Amplitude
import com.amplitude.core.platform.Plugin

class AndroidLifecyclePlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var amplitude: Amplitude
}
