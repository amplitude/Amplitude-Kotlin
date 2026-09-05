package com.amplitude.android.streaming.internal.player

import androidx.media3.common.C
import com.amplitude.android.streaming.internal.MediaType

internal data class PlayerMediaSnapshot(
    val positionMillis: Long,
    val durationMillis: Long = C.TIME_UNSET,
    val isLive: Boolean = false,
    val mediaId: String? = null,
    val title: String? = null,
    val mediaType: MediaType,
)
