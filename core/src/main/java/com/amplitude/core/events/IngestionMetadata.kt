package com.amplitude.core.events

import com.amplitude.common.jvm.ConsoleLogger
import org.json.JSONException
import org.json.JSONObject

open class IngestionMetadata @JvmOverloads constructor(
    val sourceName: String? = null,
    val sourceVersion: String? = null,
) {

    /**
     * Get JSONObject of current ingestion metadata
     * @return JSONObject including ingestion metadata information
     */
    internal fun toJSONObject(): JSONObject {
        val jsonObject = JSONObject()
        try {
            if (!sourceName.isNullOrEmpty()) {
                jsonObject.put(INGESTION_METADATA_SOURCE_NAME, sourceName)
            }
            if (!sourceVersion.isNullOrEmpty()) {
                jsonObject.put(INGESTION_METADATA_SOURCE_VERSION, sourceVersion)
            }
        } catch (e: JSONException) {
            ConsoleLogger.logger.error("JSON Serialization of ingestion metadata object failed")
        }
        return jsonObject
    }

    companion object {
        const val INGESTION_METADATA_SOURCE_NAME = "source_name"
        const val INGESTION_METADATA_SOURCE_VERSION = "source_version"

        internal fun fromJSONObject(jsonObject: JSONObject): IngestionMetadata {
            val branch = jsonObject.optString(INGESTION_METADATA_SOURCE_NAME, null)
            val source = jsonObject.optString(INGESTION_METADATA_SOURCE_VERSION, null)
            return IngestionMetadata(branch, source)
        }
    }
}
