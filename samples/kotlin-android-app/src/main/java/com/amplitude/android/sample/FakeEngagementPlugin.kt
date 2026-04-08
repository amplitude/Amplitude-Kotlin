package com.amplitude.android.sample

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.JSONUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Mimics the exact behavior of AmplitudeEngagementPlugin at line 136:
 *   CoroutineScope(Dispatchers.Main).launch { amplitudeEngagement.forwardEvent(event) }
 *
 * forwardEvent() calls JSONUtil.eventToJsonObject(event), which iterates the event's maps.
 * Meanwhile, the pipeline continues to the next plugin on its own thread.
 */
class FakeEngagementPlugin : Plugin {
    override val type = Plugin.Type.Enrichment
    override lateinit var amplitude: Amplitude

    override fun execute(event: BaseEvent): BaseEvent {
        // This is what the real engagement plugin does — fire-and-forget serialization on Main.
        // The event object is shared with the rest of the pipeline.
        CoroutineScope(Dispatchers.Main).launch {
            JSONUtil.eventToJsonObject(event)
        }
        return event
    }
}
