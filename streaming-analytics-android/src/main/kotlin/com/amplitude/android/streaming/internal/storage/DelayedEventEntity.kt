package com.amplitude.android.streaming.internal.storage

import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.core.utilities.JSONUtil
import com.amplitude.core.utilities.toBaseEvent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject

@Serializable(with = DelayedEventEntitySerializer::class)
internal data class DelayedEventEntity(
    val ingestJson: JsonObject,
)

internal fun DelayedEvent.toEntity(): DelayedEventEntity =
    DelayedEventEntity(Json.parseToJsonElement(JSONUtil.eventToString(this)).jsonObject)

internal fun DelayedEventEntity.toDelayedEvent(kind: DelayedEvent.Kind): DelayedEvent =
    DelayedEvent(JSONObject(ingestJson.toString()).toBaseEvent(), kind)

private object DelayedEventEntitySerializer : KSerializer<DelayedEventEntity> {
    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: DelayedEventEntity,
    ) {
        encoder.encodeSerializableValue(JsonObject.serializer(), value.ingestJson)
    }

    override fun deserialize(decoder: Decoder): DelayedEventEntity =
        DelayedEventEntity(decoder.decodeSerializableValue(JsonObject.serializer()))
}
