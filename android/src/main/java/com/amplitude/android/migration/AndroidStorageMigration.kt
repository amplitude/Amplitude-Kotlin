package com.amplitude.android.migration

import com.amplitude.android.storage.AndroidStorageV2
import com.amplitude.android.utilities.runCatchingCancellable
import com.amplitude.common.Logger
import com.amplitude.core.Storage
import com.amplitude.core.utilities.toEvents
import org.json.JSONArray

public class AndroidStorageMigration(
    private val source: AndroidStorageV2,
    private val destination: AndroidStorageV2,
    private val logger: Logger,
) {
    public suspend fun execute() {
        moveEventsToDestination()
        moveSimpleValues()
    }

    private suspend fun moveEventsToDestination() {
        runCatchingCancellable {
            source.rollover()
            val sourceEventFiles = source.readEventsContent() as List<String>
            if (sourceEventFiles.isEmpty()) {
                source.cleanupMetadata()
                return@runCatchingCancellable
            }

            for (sourceEventFilePath in sourceEventFiles) {
                val events = source.getEventsString(sourceEventFilePath)
                var count = 0
                val baseEvents = JSONArray(events).toEvents()
                for (event in baseEvents) {
                    runCatchingCancellable {
                        count++
                        destination.writeEvent(event)
                    }.onFailure { e ->
                        logger.error("can't move event ($event) from file $sourceEventFilePath: ${e.message}")
                    }
                }
                logger.debug("Migrated $count/${baseEvents.size} events from $sourceEventFilePath")
                source.removeFile(sourceEventFilePath)
            }
            source.cleanupMetadata()
            destination.rollover()
        }.onFailure { e ->
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
        runCatchingCancellable {
            val sourceValue = source.read(key) ?: return@runCatchingCancellable
            val destinationValue = destination.read(key)
            if (destinationValue == null) {
                runCatchingCancellable {
                    logger.debug("Migrating $key with value $sourceValue")
                    destination.write(key, sourceValue)
                }.getOrElse { e ->
                    logger.error("can't write destination $key: ${e.message}")
                    return@runCatchingCancellable
                }
            }
            source.remove(key)
        }.onFailure { e ->
            logger.error("can't move $key: ${e.message}")
        }
    }
}
