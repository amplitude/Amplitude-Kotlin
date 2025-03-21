package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.http.HttpClient
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.core.utilities.http.ResponseHandler
import com.amplitude.core.utilities.logWithStackTrace
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicInteger

class EventPipeline(
    private val amplitude: Amplitude,
) {
    private val writeChannel: Channel<WriteQueueMessage>

    private val uploadChannel: Channel<String>

    private val eventCount: AtomicInteger = AtomicInteger(0)

    private val httpClient: HttpClientInterface = amplitude.configuration.httpClient
        ?: HttpClient(amplitude.configuration)

    private val storage get() = amplitude.storage

    private val scope get() = amplitude.amplitudeScope

    var flushInterval = amplitude.configuration.flushIntervalMillis.toLong()
    var flushQueueSize = amplitude.configuration.flushQueueSize

    private var running: Boolean

    private var scheduled: Boolean

    var flushSizeDivider: AtomicInteger = AtomicInteger(1)

    var exceededRetries = false

    companion object {
        internal const val UPLOAD_SIG = "#!upload"
    }

    private val responseHandler: ResponseHandler

    init {
        running = false
        scheduled = false

        writeChannel = Channel(UNLIMITED)
        uploadChannel = Channel(UNLIMITED)

        registerShutdownHook()

        responseHandler =
            storage.getResponseHandler(
                this@EventPipeline,
                amplitude.configuration,
                scope,
                amplitude.retryDispatcher,
            )
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

    private fun write() =
        scope.launch(amplitude.storageIODispatcher) {
            for (message in writeChannel) {
                // write to storage
                val triggerFlush = (message.type == WriteQueueMessageType.FLUSH)
                if (!triggerFlush && message.event != null) {
                    try {
                        storage.writeEvent(message.event)
                    } catch (e: Exception) {
                        e.logWithStackTrace(
                            amplitude.logger,
                            "Error when writing event to pipeline"
                        )
                    }
                }

                // Skip flush when offline
                if (amplitude.configuration.offline == true) {
                    continue
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

    private fun upload() =
        scope.launch(amplitude.networkIODispatcher) {
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

                        val diagnostics = amplitude.diagnostics.extractDiagnostics()
                        val response = httpClient.upload(eventsString, diagnostics)
                        responseHandler.handle(response, events, eventsString)
                    } catch (e: FileNotFoundException) {
                        e.message?.let {
                            amplitude.logger.warn("Event storage file not found: $it")
                        }
                    } catch (e: Exception) {
                        e.logWithStackTrace(amplitude.logger, "Error when uploading event")
                    }
                }
            }
        }

    private fun getFlushCount(): Int {
        val count = flushQueueSize / flushSizeDivider.get()
        return count.takeUnless { it == 0 } ?: 1
    }

    private fun schedule() =
        scope.launch(amplitude.storageIODispatcher) {
            if (isActive && running && !scheduled && !exceededRetries) {
                scheduled = true
                delay(flushInterval)
                flush()
                scheduled = false
            }
        }

    private fun registerShutdownHook() {
        // close the stream if the app shuts down
        try {
            Runtime.getRuntime().addShutdownHook(
                object : Thread() {
                    override fun run() {
                        this@EventPipeline.stop()
                    }
                },
            )
        } catch (e: IllegalStateException) {
            // Once the shutdown sequence has begun it is impossible to register a shutdown hook,
            // so we just ignore the IllegalStateException that's thrown.
            // https://developer.android.com/reference/java/lang/Runtime#addShutdownHook(java.lang.Thread)
        }
    }
}

enum class WriteQueueMessageType {
    EVENT,
    FLUSH,
}

data class WriteQueueMessage(
    val type: WriteQueueMessageType,
    val event: BaseEvent?,
)
