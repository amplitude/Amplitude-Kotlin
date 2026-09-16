package com.amplitude.android.utilities

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import kotlinx.coroutines.channels.Channel
import java.lang.ref.WeakReference

@Deprecated("Not intended for public use. Will be internal in a future release.")
public class ActivityLifecycleObserver : ActivityLifecycleCallbacks {
    internal val eventChannel = Channel<ActivityCallbackEvent>(Channel.UNLIMITED)

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                ActivityCallbackType.Created,
            ),
        )
    }

    override fun onActivityStarted(activity: Activity) {
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                ActivityCallbackType.Started,
            ),
        )
    }

    override fun onActivityResumed(activity: Activity) {
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                ActivityCallbackType.Resumed,
            ),
        )
    }

    override fun onActivityPaused(activity: Activity) {
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                ActivityCallbackType.Paused,
            ),
        )
    }

    override fun onActivityStopped(activity: Activity) {
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                ActivityCallbackType.Stopped,
            ),
        )
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                ActivityCallbackType.Destroyed,
            ),
        )
    }
}

@Deprecated("Not intended for public use. Will be internal in a future release.")
public enum class ActivityCallbackType {
    Created,
    Started,
    Resumed,
    Paused,
    Stopped,
    Destroyed,
}

@Deprecated("Not intended for public use. Will be internal in a future release.")
public data class ActivityCallbackEvent(
    val activity: WeakReference<Activity>,
    val type: ActivityCallbackType,
)
