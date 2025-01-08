package com.amplitude.android.utilities

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import kotlinx.coroutines.channels.Channel
import java.lang.ref.WeakReference

class ActivityLifecycleObserver : ActivityLifecycleCallbacks {

    val eventChannel = Channel<ActivityCallbackEvent>(Channel.UNLIMITED)

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                ActivityCallbackType.Created
            )
        )
    }

    override fun onActivityStarted(activity: Activity) {
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                ActivityCallbackType.Started
            )
        )
    }

    override fun onActivityResumed(activity: Activity) {
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                ActivityCallbackType.Resumed
            )
        )
    }

    override fun onActivityPaused(activity: Activity) {
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                ActivityCallbackType.Paused
            )
        )
    }

    override fun onActivityStopped(activity: Activity) {
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                ActivityCallbackType.Stopped
            )
        )
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

    }

    override fun onActivityDestroyed(activity: Activity) {
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                ActivityCallbackType.Destroyed
            )
        )
    }
}

enum class ActivityCallbackType {
    Created, Started, Resumed, Paused, Stopped, Destroyed
}

data class ActivityCallbackEvent(
    val activity: WeakReference<Activity>,
    val type: ActivityCallbackType
)