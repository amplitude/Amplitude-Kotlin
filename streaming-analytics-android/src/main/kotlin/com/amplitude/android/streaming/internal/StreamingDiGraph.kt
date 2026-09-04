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

/**
 * Dependency graph for streaming analytics.
 *
 * Dependencies are declared as extension properties on this class in the files that own
 * their types.
 */
internal class StreamingDiGraph(
    val amplitude: Amplitude,
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
