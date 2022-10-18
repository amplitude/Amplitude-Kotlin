package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.HttpClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.lang.Exception
import java.util.concurrent.atomic.AtomicInteger

class EventPipeline(
    private val amplitude: Amplitude
) {

    private val writeChannel: Channel<WriteQueueMessage>

    private val uploadChannel: Channel<String>

    private val eventCount: AtomicInteger = AtomicInteger(0)

    private val httpClient: HttpClient = HttpClient(amplitude.configuration)

    private val storage get() = amplitude.storage

    private val scope get() = amplitude.amplitudeScope

    var running: Boolean
        private set

    var scheduled: Boolean
        private set

    var flushSizeDivider: AtomicInteger = AtomicInteger(1)

    companion object {
        internal const val UPLOAD_SIG = "#!upload"
    }

    init {
        running = false
        scheduled = false

        writeChannel = Channel(UNLIMITED)
        uploadChannel = Channel(UNLIMITED)

        registerShutdownHook()
    }

    fun put(event: BaseEvent) {
        event.attempts += 1
        writeChannel.trySend(WriteQueueMessage(WriteQueueMessageType.EVENT, event))
    }

    fun flush() {
        writeChannel.trySend(WriteQueueMessage(WriteQueueMessageType.FLUSH, null))
    }

    fun start() {
        running = true
        write()
        upload()
    }

    fun stop() {
        uploadChannel.cancel()
        writeChannel.cancel()
        running = false
    }

    private fun write() = scope.launch(amplitude.storageIODispatcher) {
        for (message in writeChannel) {
            // write to storage
            val triggerFlush = (message.type == WriteQueueMessageType.FLUSH)
            if (!triggerFlush && message.event != null) try {
                storage.writeEvent(message.event)
            } catch (e: Exception) {
                e.message?.let {
                    amplitude.logger.error("Error when write event: $it")
                }
            }

            // if flush condition met, generate paths
            if (eventCount.incrementAndGet() >= getFlushCount() || triggerFlush) {
                eventCount.set(0)
                uploadChannel.trySend(UPLOAD_SIG)
            } else {
                schedule()
            }
        }
    }

    private fun upload() = scope.launch(amplitude.networkIODispatcher) {
        uploadChannel.consumeEach {

            withContext(amplitude.storageIODispatcher) {
                try {
                    storage.rollover()
                } catch (e: FileNotFoundException) {
                    e.message?.let {
                        amplitude.logger.warn("Event storage file not found: $it")
                    }
                }
            }

            val eventsData = storage.readEventsContent()
            for (events in eventsData) {
                try {
                    val eventsString = storage.getEventsString(events)
                    if (eventsString.isEmpty()) continue
                    val connection = httpClient.upload()
                    connection.outputStream?.let {
                        connection.setEvents(eventsString)
                        // Upload the payloads.
                        connection.close()
                    }
                    val responseHandler = storage.getResponseHandler(
                        this@EventPipeline,
                        amplitude.configuration,
                        scope,
                        amplitude.retryDispatcher,
                        events,
                        eventsString
                    )
                    responseHandler.handle(connection.response)
                } catch (e: FileNotFoundException) {
                    e.message?.let {
                        amplitude.logger.warn("Event storage file not found: $it")
                    }
                } catch (e: Exception) {
                    e.message?.let {
                        amplitude.logger.error("Error when upload event: $it")
                    }
                }
            }
        }
    }

    private fun getFlushCount(): Int {
        val count = amplitude.configuration.flushQueueSize / flushSizeDivider.get()
        return count.takeUnless { it == 0 } ?: 1
    }

    private fun getFlushIntervalInMillis(): Long {
        return amplitude.configuration.flushIntervalMillis.toLong()
    }

    private fun schedule() = scope.launch(amplitude.storageIODispatcher) {
        while (isActive && running && !scheduled) {
            scheduled = true
            delay(getFlushIntervalInMillis())
            flush()
            scheduled = false
        }
    }

    private fun registerShutdownHook() {
        // close the stream if the app shuts down
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                this@EventPipeline.stop()
            }
        })
    }
}

enum class WriteQueueMessageType {
    EVENT, FLUSH
}

data class WriteQueueMessage(
    val type: WriteQueueMessageType,
    val event: BaseEvent?
)
