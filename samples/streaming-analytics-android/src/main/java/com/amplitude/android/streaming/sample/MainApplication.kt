package com.amplitude.android.streaming.sample

import android.app.Application

class MainApplication : Application() {
    internal lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = createAppGraph(this)
    }
}
