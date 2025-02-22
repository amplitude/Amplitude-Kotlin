package com.amplitude.core.events

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        assertEquals(sourceName, result.getString(IngestionMetadata.AMP_INGESTION_METADATA_SOURCE_NAME))
        assertEquals(sourceVersion, result.getString(IngestionMetadata.AMP_INGESTION_METADATA_SOURCE_VERSION))
    }

    @Test
    fun `test ingestionMetadata clone new object`() {
        val sourceName = "ampli"
        val sourceVersion = "2.0.0"
        val ingestionMetadata = IngestionMetadata(sourceName, sourceVersion)
        val clone = ingestionMetadata.clone()
        assertEquals(sourceName, clone.sourceName)
        assertEquals(sourceVersion, clone.sourceVersion)
        assertFalse(ingestionMetadata === clone)
    }

    @Test
    fun `test ingestionMetadata from json object`() {
        val jsonObject = JSONObject()
        val sourceName = "ampli"
        val sourceVersion = "2.0.0"
        jsonObject.put(IngestionMetadata.AMP_INGESTION_METADATA_SOURCE_NAME, sourceName)
            .put(IngestionMetadata.AMP_INGESTION_METADATA_SOURCE_VERSION, sourceVersion)
        val ingestionMetadata = IngestionMetadata.fromJSONObject(jsonObject)
        assertEquals(sourceName, ingestionMetadata.sourceName)
        assertEquals(sourceVersion, ingestionMetadata.sourceVersion)
    }
}
