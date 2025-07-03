package com.amplitude.android.migration

import com.amplitude.android.storage.AndroidStorageV2
import com.amplitude.common.Logger
import com.amplitude.core.Storage
import com.amplitude.core.utilities.toEvents
import org.json.JSONArray

class AndroidStorageMigration(
    private val source: AndroidStorageV2,
    private val destination: AndroidStorageV2,
    private val logger: Logger,
) {
    suspend fun execute() {
        moveEventsToDestination()
        moveSimpleValues()
    }

    private suspend fun moveEventsToDestination() {
        try {
            source.rollover()
            val sourceEventFiles = source.readEventsContent() as List<String>
            if (sourceEventFiles.isEmpty()) {
                source.cleanupMetadata()
                return
            }

            for (sourceEventFilePath in sourceEventFiles) {
                val events = source.getEventsString(sourceEventFilePath)
                var count = 0
                val baseEvents = JSONArray(events).toEvents()
                for (event in baseEvents) {
                    try {
                        count++
                        destination.writeEvent(event)
                    } catch (e: Exception) {
                        logger.error("can't move event ($event) from file $sourceEventFilePath: ${e.message}")
                    }
                }
                logger.debug("Migrated $count/${baseEvents.size} events from $sourceEventFilePath")
                source.removeFile(sourceEventFilePath)
            }
            source.cleanupMetadata()
            destination.rollover()
        } catch (e: Exception) {
            logger.error("can't move event files: ${e.message}")
        }
    }

    private suspend fun moveSimpleValues() {
        moveSimpleValue(Storage.Constants.PREVIOUS_SESSION_ID)
        moveSimpleValue(Storage.Constants.LAST_EVENT_TIME)
        moveSimpleValue(Storage.Constants.LAST_EVENT_ID)

        moveSimpleValue(Storage.Constants.OPT_OUT)
        moveSimpleValue(Storage.Constants.Events)
        moveSimpleValue(Storage.Constants.APP_VERSION)
        moveSimpleValue(Storage.Constants.APP_BUILD)
    }

    private suspend fun moveSimpleValue(key: Storage.Constants) {
        try {
            val sourceValue = source.read(key) ?: return
            val destinationValue = destination.read(key)
            if (destinationValue == null) {
                try {
                    logger.debug("Migrating $key with value $sourceValue")
                    destination.write(key, sourceValue)
                } catch (e: Exception) {
                    logger.error("can't write destination $key: ${e.message}")
                    return
                }
            }
            source.remove(key)
        } catch (e: Exception) {
            logger.error("can't move $key: ${e.message}")
        }
    }
}
