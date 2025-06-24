package com.amplitude.core.context

import com.amplitude.common.Logger
import com.amplitude.core.ServerZone

data class AmplitudeContext(
    val apiKey: String,
    val instanceName: String,
    val serverZone: ServerZone,
    val logger: Logger
)