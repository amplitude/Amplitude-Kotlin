package com.amplitude.android.streaming.internal

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.JSONUtil
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable(with = DelayedEventSerializer::class)
internal class DelayedEvent(
    eventType: String,
    val kind: Kind,
    timestamp: Long? = null,
    deviceId: String? = null,
    userId: String? = null,
    sessionId: Long? = null,
    eventProperties: MutableMap<String, Any?> = mutableMapOf(),
) : BaseEvent() {
    enum class Kind {
        INSTANT,
        DELAYED,
    }

    init {
        this.eventType = eventType
        this.timestamp = timestamp
        this.deviceId = deviceId
        this.userId = userId
        this.sessionId = sessionId
        this.eventProperties = eventProperties
    }

    constructor(wrapping: BaseEvent, kind: Kind) : this(
        eventType = wrapping.eventType,
        kind = kind,
    ) {
        mergeEventOptions(wrapping)
        copyPropertyMapsFrom(wrapping)
    }
}

internal object DelayedEventSerializer : KSerializer<DelayedEvent> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("DelayedEvent")

    override fun serialize(
        encoder: Encoder,
        value: DelayedEvent,
    ) {
        val jsonElement = Json.parseToJsonElement(JSONUtil.eventToString(value))
        encoder.encodeSerializableValue(JsonElement.serializer(), jsonElement)
    }

    override fun deserialize(decoder: Decoder): DelayedEvent {
        throw UnsupportedOperationException("DelayedEvent deserialization is not supported")
    }
}
