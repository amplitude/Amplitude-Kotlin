package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.*
import com.amplitude.core.utilities.FileResponseHandler
import com.amplitude.core.utilities.HttpClient
import com.amplitude.core.utilities.ResponseHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import java.lang.Exception
import java.util.concurrent.atomic.AtomicInteger

internal class EventPipeline(
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
                e.message?.let{
                    amplitude.logger.error(it)
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
                storage.rollover()
            }

            val eventsData = storage.readEventsContent()
            for (events in eventsData) {
                val eventsString = storage.getEventsString(events)
                if (eventsString.isEmpty()) continue

                try {
                    val connection = httpClient.upload()
                    connection.outputStream?.let {
                        connection.setEvents(eventsString)
                        // Upload the payloads.
                        connection.close()
                    }
                    val responseHandler = createResponseHandler(events, eventsString)
                    responseHandler?.handle(connection.response)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun createResponseHandler(events: Any, eventsString: String): ResponseHandler? {
        var responseHandler: ResponseHandler? = null
        when (storage) {
            is FileStorage -> {
                responseHandler = FileResponseHandler(
                    storage as FileStorage,
                    this,
                    amplitude.configuration,
                    scope,
                    amplitude.storageIODispatcher,
                    events as String,
                    eventsString
                )
            }
            is InMemoryStorage -> {
                responseHandler = InMemoryResponseHandler(
                    this,
                    amplitude.configuration,
                    scope,
                    amplitude.retryDispatcher,
                    events as List<BaseEvent>
                )
            }
        }
        return responseHandler
    }

    private fun getFlushCount(): Int {
        return amplitude.configuration.flushQueueSize / flushSizeDivider.get()
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
