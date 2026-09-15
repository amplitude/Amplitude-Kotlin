package com.amplitude.core.platform.intercept

import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.platform.intercept.IdentifyInterceptorUtil.filterNonNullValues
import com.amplitude.core.utilities.EventsFileStorage
import com.amplitude.core.utilities.runCatchingCancellable
import com.amplitude.core.utilities.toEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.FileNotFoundException

public class IdentifyInterceptFileStorageHandler(
    private val storage: EventsFileStorage,
    private val logger: Logger,
    private val amplitude: Amplitude,
) : IdentifyInterceptStorageHandler {
    // Keep emitted files claimed so cleanup failures cannot make a batch eligible again.
    private val consumedFilePaths = mutableSetOf<String>()

    override suspend fun getTransferIdentifyEvent(): BaseEvent? {
        runCatchingCancellable {
            storage.rollover()
        }.onFailure { e ->
            if (e is FileNotFoundException) {
                e.message?.let { logger.warn("Event storage file not found: $it") }
            } else {
                throw e
            }
        }
        cleanupConsumedFiles()
        val eventsData =
            storage.readEventsContent()
                .map { it as String }
                .filterNot(consumedFilePaths::contains)
        if (eventsData.isEmpty()) {
            return null
        }
        var event: BaseEvent? = null
        var identifyEventUserProperties: MutableMap<String, Any?>? = null
        val processedFilePaths = mutableListOf<String>()
        try {
            for (eventPath in eventsData) {
                val processed =
                    runCatchingCancellable {
                        val eventsString = storage.getEventsString(eventPath)
                        if (eventsString.isEmpty()) {
                            return@runCatchingCancellable
                        }
                        val eventsList = JSONArray(eventsString).toEvents()
                        if (eventsList.isEmpty()) {
                            return@runCatchingCancellable
                        }

                        if (event == null) {
                            val firstEvent = eventsList[0]
                            val firstEventUserProperties =
                                filterNonNullValues(
                                    firstEvent.userProperties?.get(IdentifyOperation.SET.operationType) as MutableMap<String, Any?>,
                                )
                            val mergedUserProperties =
                                IdentifyInterceptorUtil.mergeIdentifyList(eventsList.subList(1, eventsList.size))
                            firstEventUserProperties.putAll(mergedUserProperties)
                            event = firstEvent
                            identifyEventUserProperties = firstEventUserProperties
                        } else {
                            val mergedUserProperties = IdentifyInterceptorUtil.mergeIdentifyList(eventsList)
                            identifyEventUserProperties?.putAll(mergedUserProperties)
                        }
                    }
                processed.onSuccess {
                    processedFilePaths.add(eventPath)
                }.onFailure { e ->
                    logger.warn("Identify Merge error: ${e.message}")
                    // Discard the file even if delete fails so it cannot join a later batch.
                    processedFilePaths.add(eventPath)
                }
            }
        } catch (e: CancellationException) {
            eventsData.forEach(storage::releaseFile)
            throw e
        }
        consumedFilePaths.addAll(processedFilePaths)
        cleanupConsumedFiles()
        event?.userProperties?.put(
            IdentifyOperation.SET.operationType,
            identifyEventUserProperties,
        )
        return event
    }

    override suspend fun clearIdentifyIntercepts() {
        runCatchingCancellable {
            storage.rollover()
        }.onFailure { e ->
            if (e is FileNotFoundException) {
                e.message?.let { logger.warn("Event storage file not found: $it") }
            } else {
                throw e
            }
        }
        cleanupConsumedFiles()
        consumedFilePaths.addAll(storage.readEventsContent().map { it as String })
        cleanupConsumedFiles()
    }

    private suspend fun cleanupConsumedFiles() {
        consumedFilePaths.toList().forEach { file ->
            val removal =
                runCatchingCancellable {
                    withContext(amplitude.storageIODispatcher) {
                        storage.removeFile(file)
                    }
                }.onFailure { e ->
                    logger.warn("Unable to remove consumed identify file $file: ${e.message}")
                }
            if (removal.getOrDefault(false)) {
                consumedFilePaths.remove(file)
            } else if (removal.isSuccess) {
                logger.warn("Unable to remove consumed identify file $file")
            }
        }
    }
}
