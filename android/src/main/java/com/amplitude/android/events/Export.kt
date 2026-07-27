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

public open class BaseEvent : BaseEvent()

public open class IdentifyEvent : IdentifyEvent()

public open class GroupIdentifyEvent : GroupIdentifyEvent()

public open class EventOptions : EventOptions()

public open class Identify : Identify()

public open class Revenue : Revenue()

public open class RevenueEvent : RevenueEvent()

public open class Plan
    @JvmOverloads
    constructor(
        branch: String? = null,
        source: String? = null,
        version: String? = null,
        versionId: String? = null,
    ) : Plan(branch, source, version, versionId)

public open class IngestionMetadata
    @JvmOverloads
    constructor(
        sourceName: String? = null,
        sourceVersion: String? = null,
    ) : IngestionMetadata(sourceName, sourceVersion)
