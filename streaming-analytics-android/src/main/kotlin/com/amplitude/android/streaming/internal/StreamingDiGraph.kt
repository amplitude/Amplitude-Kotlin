package com.amplitude.android.streaming.internal

import android.content.Context
import com.amplitude.android.Configuration
import com.amplitude.android.streaming.internal.util.DiGraph
import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
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
internal open class StreamingDiGraph private constructor(
    val amplitude: Amplitude,
    val maxStorageBytes: Long,
    parent: StreamingDiGraph?,
) : DiGraph(parent) {
    constructor(
        amplitude: Amplitude,
        maxStorageBytes: Long = MAX_STORAGE_BYTES,
    ) : this(amplitude, maxStorageBytes, null)

    /** Creates a child graph sharing the parent's inputs and streaming bindings. */
    protected constructor(parent: StreamingDiGraph) : this(parent.amplitude, parent.maxStorageBytes, parent)

    val configuration: Configuration by singleton { amplitude.configuration as Configuration }
    val context: Context by singleton { configuration.context.applicationContext }
    val ioDispatcher: CoroutineDispatcher by singleton { Dispatchers.IO }
    val logger: Logger by singleton { amplitude.logger }
    val scope: CoroutineScope by singleton {
        CoroutineScope(
            SupervisorJob(amplitude.amplitudeScope.coroutineContext[Job]) + ioDispatcher,
        )
    }
}
