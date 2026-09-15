package com.amplitude.android.streaming.internal

import android.content.Context
import com.amplitude.android.Configuration
import com.amplitude.core.Amplitude
import com.amplitude.android.streaming.internal.util.DiGraph
import com.amplitude.common.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/** Safety valve so a stuck uploader cannot fill app storage. Not a working-set target. */
private const val MAX_STORAGE_BYTES = 25L * 1024 * 1024

/**
 * Dependency graph for streaming analytics.
 *
 * Dependencies are declared as extension properties on this class in the files that own
 * their types.
 */
internal class StreamingDiGraph(
    val amplitude: Amplitude,
    val maxStorageBytes: Long = MAX_STORAGE_BYTES,
) : DiGraph() {

    val configuration: Configuration by lazy { amplitude.configuration as Configuration }
    val context: Context by lazy { configuration.context.applicationContext }
    val ioDispatcher: CoroutineDispatcher by lazy { Dispatchers.IO }
    val logger: Logger by lazy { amplitude.logger }
    val scope: CoroutineScope by lazy {
        CoroutineScope(
            SupervisorJob(amplitude.amplitudeScope.coroutineContext[Job]) + ioDispatcher,
        )
    }
}
