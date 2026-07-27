package com.amplitude.common

public interface Logger {
    public enum class LogMode(i: Int) {
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4),
        OFF(5),
    }

    public var logMode: LogMode

    public fun debug(message: String)

    public fun error(message: String)

    public fun info(message: String)

    public fun warn(message: String)
}
