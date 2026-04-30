package com.amplitude.core

import com.amplitude.common.Logger
import com.amplitude.core.platform.Plugin

/**
 * Invoke [block] on [plugin], catching any [Throwable] so one misbehaving
 * plugin can't break the notification fan-out (or terminate the coroutine
 * draining the Android event-message channel).
 *
 * Shared by [Amplitude.notifyAllPlugins] / [Amplitude.notifyTimelinePlugins]
 * and [State]'s identity-callback iteration so all plugin-callback fan-outs
 * follow the same isolation contract: never propagate a plugin failure back
 * to the caller; log at warn level instead. [logger] is nullable so callers
 * without ready logger access (e.g. [State] before [Amplitude] wires one in)
 * still get the isolation guarantee.
 */
internal fun safelyNotify(
    plugin: Plugin,
    logger: Logger?,
    block: (Plugin) -> Unit,
) {
    try {
        block(plugin)
    } catch (throwable: Throwable) {
        val identifier = plugin.name ?: plugin::class.java.name
        logger?.warn("Plugin '$identifier' threw during notify callback: $throwable")
    }
}
