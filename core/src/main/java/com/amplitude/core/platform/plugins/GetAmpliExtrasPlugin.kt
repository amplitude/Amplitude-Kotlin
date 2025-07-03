package com.amplitude.core.platform.plugins

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.platform.Plugin

class GetAmpliExtrasPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var amplitude: Amplitude

    companion object {
        const val AMP_AMPLI = "ampli"
    }

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
    }

    override fun execute(event: BaseEvent): BaseEvent {
        val ampliExtra = event.extra?.get(AMP_AMPLI) ?: return event
        try {
            val ingestionMetadataMap = (ampliExtra as Map<String, Any>).get("ingestionMetadata") as Map<String, String>
            val ingestionMetadata =
                IngestionMetadata(
                    ingestionMetadataMap["sourceName"],
                    ingestionMetadataMap["sourceVersion"],
                )
            event.ingestionMetadata = ingestionMetadata
        } finally {
            return event
        }
    }
}
