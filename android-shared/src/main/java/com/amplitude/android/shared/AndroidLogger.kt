package com.amplitude.android.shared

import android.util.Log

class AndroidLogger private constructor() // prevent instantiation
{
    @Volatile
    private var enableLogging = true

    @Volatile
    private var logLevel: Int = Log.INFO // default log level

    fun setEnableLogging(enableLogging: Boolean): AndroidLogger {
        this.enableLogging = enableLogging
        return logger
    }

    fun setLogLevel(logLevel: Int): AndroidLogger {
        this.logLevel = logLevel
        return logger
    }

    fun d(tag: String?, msg: String): Int {
        return if (enableLogging && logLevel <= Log.DEBUG) Log.d(tag, msg) else 0
    }

    fun d(tag: String?, msg: String, tr: Throwable?): Int {
        return if (enableLogging && logLevel <= Log.DEBUG) Log.d(tag, msg, tr) else 0
    }

    fun e(tag: String?, msg: String): Int {
        if (enableLogging && logLevel <= Log.ERROR) {
            return Log.e(tag, msg)
        }
        return 0
    }

    fun e(tag: String?, msg: String, tr: Throwable?): Int {
        if (enableLogging && logLevel <= Log.ERROR) {
            return Log.e(tag, msg, tr)
        }
        return 0
    }

    fun getStackTraceString(tr: Throwable?): String {
        return Log.getStackTraceString(tr)
    }

    fun i(tag: String?, msg: String): Int {
        return if (enableLogging && logLevel <= Log.INFO) Log.i(tag, msg) else 0
    }

    fun i(tag: String?, msg: String, tr: Throwable?): Int {
        return if (enableLogging && logLevel <= Log.INFO) Log.i(tag, msg, tr) else 0
    }

    fun isLoggable(tag: String?, level: Int): Boolean {
        return Log.isLoggable(tag, level)
    }

    fun println(priority: Int, tag: String?, msg: String): Int {
        return Log.println(priority, tag, msg)
    }

    fun v(tag: String?, msg: String): Int {
        return if (enableLogging && logLevel <= Log.VERBOSE) Log.v(tag, msg) else 0
    }

    fun v(tag: String?, msg: String, tr: Throwable?): Int {
        return if (enableLogging && logLevel <= Log.VERBOSE) Log.v(tag, msg, tr) else 0
    }

    fun w(tag: String?, msg: String): Int {
        return if (enableLogging && logLevel <= Log.WARN) Log.w(tag, msg) else 0
    }

    fun w(tag: String?, tr: Throwable?): Int {
        return if (enableLogging && logLevel <= Log.WARN) Log.w(tag, tr) else 0
    }

    fun w(tag: String?, msg: String, tr: Throwable?): Int {
        return if (enableLogging && logLevel <= Log.WARN) Log.w(tag, msg, tr) else 0
    }

    companion object {
        var logger = AndroidLogger()
            protected set
    }
}
