package com.amplitude.android.streaming.internal

import com.amplitude.android.streaming.internal.util.DiGraph.Companion.singleton
import com.amplitude.android.streaming.internal.util.maxSizeMap
import java.util.UUID

private const val MAX_BATCHES = 100

internal val StreamingDiGraph.delayedEventBatcher: DelayedEventBatcher by singleton {
    DelayedEventBatcher()
}

/** Collects enriched events until every member of a batch has arrived. */
internal class DelayedEventBatcher {
    private val batches = maxSizeMap<UUID, MutableList<DelayedEvent>>(MAX_BATCHES)

    @Synchronized
    fun collect(event: DelayedEvent): List<DelayedEvent>? {
        val info = event.batchInfo ?: return listOf(event)

        val batch = batches.getOrPut(info.id) { mutableListOf() }
        batch.add(DelayedEvent(event, event.kind))

        if (batch.size < info.batchSize) return null

        batches.remove(info.id)
        return batch
    }
}
