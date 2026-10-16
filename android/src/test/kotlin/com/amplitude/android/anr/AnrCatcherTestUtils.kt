package com.amplitude.android.anr

internal suspend fun AnrCatcher.consumePreviousAnrs(): List<String> {
    val reports = mutableListOf<String>()
    consumePreviousAnrs { claim -> reports.addAll(claim()) }
    return reports
}
