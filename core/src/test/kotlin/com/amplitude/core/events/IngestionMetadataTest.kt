package com.amplitude.core.events

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IngestionMetadataTest {

    @Test
    fun `test ingestionMetadata to json object`() {
        val sourceName = "ampli"
        val sourceVersion = "2.0.0"
        val ingestionMetadata = IngestionMetadata(sourceName, sourceVersion)
        val result = ingestionMetadata.toJSONObject()
        assertEquals(sourceName, result.getString(IngestionMetadata.INGESTION_METADATA_SOURCE_NAME))
        assertEquals(sourceVersion, result.getString(IngestionMetadata.INGESTION_METADATA_SOURCE_VERSION))
    }

    @Test
    fun `test ingestionMetadata from json object`() {
        val jsonObject = JSONObject()
        val sourceName = "ampli"
        val sourceVersion = "2.0.0"
        jsonObject.put(IngestionMetadata.INGESTION_METADATA_SOURCE_NAME, sourceName)
            .put(IngestionMetadata.INGESTION_METADATA_SOURCE_VERSION, sourceVersion)
        val ingestionMetadata = IngestionMetadata.fromJSONObject(jsonObject)
        assertEquals(sourceName, ingestionMetadata.sourceName)
        assertEquals(sourceVersion, ingestionMetadata.sourceVersion)
    }
}
