package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.ExponentialBackoffRetryHandler
import com.amplitude.core.utilities.http.BadRequestResponse
import com.amplitude.core.utilities.http.HttpClient
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.core.utilities.http.ResponseHandler
import com.amplitude.core.utilities.http.TooManyRequestsResponse
import com.amplitude.core.utilities.logWithStackTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@OptIn(RestrictedAmplitudeFeature::class)
public class EventPipeline(
    private val amplitude: Amplitude,
    private val eventCount: AtomicInteger = AtomicInteger(0),
    private val httpClient: HttpClientInterface =
        amplitude.configuration.httpClient
            ?: HttpClient(amplitude.configuration, amplitude.logger),
    private val retryUploadHandler: ExponentialBackoffRetryHandler = ExponentialBackoffRetryHandler(),
    private val storage: Storage = amplitude.storage,
    private val scope: CoroutineScope = amplitude.amplitudeScope,
    private val writeChannel: Channel<WriteQueueMessage> = Channel(UNLIMITED),
    private var uploadChannel: Channel<String> = Channel(UNLIMITED),
    overrideResponseHandler: ResponseHandler? = null,
) {
    private var running: Boolean
    private var scheduled: Boolean
    public var flushSizeDivider: AtomicInteger = AtomicInteger(1)

    private val responseHandler by lazy {
        overrideResponseHandler ?: storage.getResponseHandler(
            this@EventPipeline,
            amplitude.configuration,
            scope,
            amplitude.storageIODispatcher,
        )
    }

    public companion object {
        private const val UPLOAD_SIG = "#!upload"

        /**
         * The server asked us to slow down, so we wait this long before retrying.
         */
        private const val TOO_MANY_REQUESTS_DELAY_IN_MS = 30_000L
    }

    init {
        running = false
        scheduled = false

        registerShutdownHook()
    }

    public fun put(event: BaseEvent) {
        event.attempts += 1
        writeChannel.trySend(WriteQueueMessage(WriteQueueMessageType.EVENT, event))
    }

    public fun flush() {
        writeChannel.trySend(WriteQueueMessage(WriteQueueMessageType.FLUSH, null))
    }

    public fun start() {
        running = true
        write()
        upload()
    }

    public fun stop() {
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
                            "Error when writing event to pipeline",
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
            uploadChannel.consumeEach { _ ->
                withContext(amplitude.storageIODispatcher) {
                    try {
                        storage.rollover()
                    } catch (e: FileNotFoundException) {
                        e.message?.let {
                            amplitude.logger.warn("Event storage file not found: $it")
                        }
                    }
                }

                val eventFiles = storage.readEventsContent()
                for (eventFile in eventFiles) {
                    try {
                        val eventsString = storage.getEventsString(eventFile)
                        if (eventsString.isEmpty()) continue

                        val diagnostics = amplitude.diagnostics.extractDiagnostics()
                        val response = httpClient.upload(eventsString, diagnostics)
                        val shouldRetryUploadOnFailure = responseHandler.handle(response, eventFile, eventsString)

                        // an invalid API key is terminal, retrying can never recover from it
                        if (response is BadRequestResponse && response.isInvalidApiKeyResponse()) {
                            amplitude.logger.error("Invalid API key. Don't retry.")
                            break
                        }

                        // if we encounter a retryable error, we retry with delay and
                        // restart the loop to get the newest event files
                        if (shouldRetryUploadOnFailure == true) {
                            if (response is TooManyRequestsResponse) {
                                delay(TOO_MANY_REQUESTS_DELAY_IN_MS.milliseconds)
                                uploadChannel.trySend(UPLOAD_SIG)
                                break
                            }

                            retryUploadHandler.attemptRetry { canRetry ->
                                if (canRetry) {
                                    uploadChannel.trySend(UPLOAD_SIG)
                                }
                            }
                            break
                        }
                        retryUploadHandler.reset() // always reset when we've successfully uploaded
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
        val count = amplitude.configuration.flushQueueSize / flushSizeDivider.get()
        return count.takeUnless { it == 0 } ?: 1
    }

    private fun schedule() =
        scope.launch(amplitude.storageIODispatcher) {
            if (isActive && running && !scheduled) {
                scheduled = true
                delay(amplitude.configuration.flushIntervalMillis.toLong())
                flush()
                scheduled = false
            }
        }

    private fun registerShutdownHook() {
        // close the stream if the app shuts down
        try {
            val weakRef = WeakReference(this)
            Runtime.getRuntime().addShutdownHook(
                object : Thread() {
                    override fun run() {
                        weakRef.get()?.stop()
                    }
                },
            )
        } catch (_: IllegalStateException) {
            // Once the shutdown sequence has begun it is impossible to register a shutdown hook,
            // so we just ignore the IllegalStateException that's thrown.
            // https://developer.android.com/reference/java/lang/Runtime#addShutdownHook(java.lang.Thread)
        }
    }
}

public enum class WriteQueueMessageType {
    EVENT,
    FLUSH,
}

public data class WriteQueueMessage(
    val type: WriteQueueMessageType,
    val event: BaseEvent?,
)
