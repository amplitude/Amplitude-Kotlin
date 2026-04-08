package com.amplitude.android.sample

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.JSONUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The FIXED version of the engagement plugin pattern.
 * Serializes the event on the pipeline thread (safe), then dispatches the
 * pre-serialized JSONObject to Main. No shared mutable state crosses the thread boundary.
 */
class FakeEngagementPluginFixed : Plugin {
    override val type = Plugin.Type.Enrichment
    override lateinit var amplitude: Amplitude

    override fun execute(event: BaseEvent): BaseEvent {
        // Serialize on the pipeline thread — this is the fix.
        val eventJson = JSONUtil.eventToJsonObject(event)
        // Only the independent JSONObject crosses the thread boundary.
        CoroutineScope(Dispatchers.Main).launch {
            // In the real plugin, this would be:
            // contextManager.evaluateAsync("window.engagement.forwardEvent($eventJson)")
            eventJson.toString()
        }
        return event
    }
}
