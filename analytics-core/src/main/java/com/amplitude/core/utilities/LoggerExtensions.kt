package com.amplitude.core.utilities

import com.amplitude.common.Logger

internal fun Exception.logWithStackTrace(
    logger: Logger,
    message: String,
) {
    this.message?.let {
        logger.error("$message: $it")
    }
    this.stackTrace?.let {
        logger.error("Stack trace: ${this.stackTraceToString()}")
    }
}
