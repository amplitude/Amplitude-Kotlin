package com.amplitude.android.streaming.internal.player

import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.android.streaming.internal.util.Time
import com.amplitude.android.streaming.internal.util.time
import kotlinx.coroutines.CoroutineScope

internal val StreamingDiGraph.heartbeatFactory: HeartbeatFactory by weak {
    HeartbeatFactory(
        time = time,
    )
}

internal class HeartbeatFactory(
    private val time: Time,
) {
    fun create(
        scope: CoroutineScope,
        stoppedEvent: suspend (timestamp: Long) -> Unit,
    ): Heartbeat =
        Heartbeat(
            scope = scope,
            stoppedEvent = stoppedEvent,
            time = time,
        )
}
