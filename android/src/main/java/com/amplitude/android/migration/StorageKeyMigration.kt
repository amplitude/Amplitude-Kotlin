package com.amplitude.android.migration

import com.amplitude.android.utilities.AndroidStorage
import com.amplitude.common.Logger
import com.amplitude.core.Storage
import java.io.File

class StorageKeyMigration(
    private val source: AndroidStorage,
    private val destination: AndroidStorage,
    private val logger: Logger
) {
    suspend fun execute() {
        if (source.storageKey == destination.storageKey) {
            return
        }
        moveSourceEventFilesToDestination()
        moveSimpleValues()
    }

    private suspend fun moveSourceEventFilesToDestination() {
        try {
            source.rollover()
            val sourceEventFiles = source.readEventsContent() as List<String>
            if (sourceEventFiles.isEmpty()) {
                return
            }

            for (sourceEventFilePath in sourceEventFiles) {
                val sourceEventFile = File(sourceEventFilePath)
                val destinationFilePath = sourceEventFilePath.replace(
                    "/${source.storageKey}/",
                    "/${destination.storageKey}/"
                ).replace(
                    source.eventsFile.id,
                    destination.eventsFile.id,
                )
                val destinationEventFile = File(destinationFilePath)
                if (destinationEventFile.exists()) {
                    logger.error("destination ${destinationEventFile.absoluteFile} already exists")
                } else {
                    try {
                        sourceEventFile.renameTo(destinationEventFile)
                    } catch (e: Exception) {
                        logger.error("can't rename $sourceEventFile to $destinationEventFile: ${e.message}")
                    }
                }
            }
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

        moveFileIndex()
    }

    private suspend fun moveSimpleValue(key: Storage.Constants) {
        try {
            val sourceValue = source.read(key) ?: return

            val destinationValue = destination.read(key)
            if (destinationValue == null) {
                try {
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

    private fun moveFileIndex() {
        try {
            val sourceFileIndexKey = "amplitude.events.file.index.${source.storageKey}"
            val destinationFileIndexKey = "amplitude.events.file.index.${destination.storageKey}"
            if (source.sharedPreferences.contains(sourceFileIndexKey)) {
                val fileIndex = source.sharedPreferences.getLong(sourceFileIndexKey, -1)
                try {
                    destination.sharedPreferences.edit().putLong(destinationFileIndexKey, fileIndex).commit()
                } catch (e: Exception) {
                    logger.error("can't write file index: ${e.message}")
                    return
                }
                source.sharedPreferences.edit().remove(sourceFileIndexKey).commit()
            }
        } catch (e: Exception) {
            logger.error("can't move file index: ${e.message}")
        }
    }
}
