package com.amplitude.android.streaming.internal

import java.util.UUID

/** Local grouping metadata, excluded from event storage and cloud serialization. */
internal data class BatchInfo(
    val id: UUID,
    val batchSize: Int,
) {
    init {
        require(batchSize > 0) { "A delayed event batch must not be empty" }
    }
}
