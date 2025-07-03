package com.amplitude.android.events

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.Identify
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.events.Revenue
import com.amplitude.core.events.RevenueEvent

open class BaseEvent : BaseEvent()

open class IdentifyEvent : IdentifyEvent()

open class GroupIdentifyEvent : GroupIdentifyEvent()

open class EventOptions : EventOptions()

open class Identify : Identify()

open class Revenue : Revenue()

open class RevenueEvent : RevenueEvent()

open class Plan
    @JvmOverloads
    constructor(
        branch: String? = null,
        source: String? = null,
        version: String? = null,
        versionId: String? = null,
    ) : Plan(branch, source, version, versionId)

open class IngestionMetadata
    @JvmOverloads
    constructor(
        sourceName: String? = null,
        sourceVersion: String? = null,
    ) : IngestionMetadata(sourceName, sourceVersion)
