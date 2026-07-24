package com.amplitude.android.utilities

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import kotlinx.coroutines.channels.Channel
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

class ActivityLifecycleObserver : ActivityLifecycleCallbacks {
    internal val eventChannel = Channel<ActivityCallbackEvent>(Channel.UNLIMITED)
    private val active = AtomicBoolean(true)

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        send(activity, ActivityCallbackType.Created)
    }

    override fun onActivityStarted(activity: Activity) {
        send(activity, ActivityCallbackType.Started)
    }

    override fun onActivityResumed(activity: Activity) {
        send(activity, ActivityCallbackType.Resumed)
    }

    override fun onActivityPaused(activity: Activity) {
        send(activity, ActivityCallbackType.Paused)
    }

    override fun onActivityStopped(activity: Activity) {
        send(activity, ActivityCallbackType.Stopped)
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        send(activity, ActivityCallbackType.Destroyed)
    }

    internal fun stop() {
        if (active.compareAndSet(true, false)) {
            eventChannel.cancel()
        }
    }

    private fun send(
        activity: Activity,
        type: ActivityCallbackType,
    ) {
        if (!active.get()) {
            return
        }
        eventChannel.trySend(
            ActivityCallbackEvent(
                WeakReference(activity),
                type,
            ),
        )
    }
}

enum class ActivityCallbackType {
    Created,
    Started,
    Resumed,
    Paused,
    Stopped,
    Destroyed,
}

data class ActivityCallbackEvent(
    val activity: WeakReference<Activity>,
    val type: ActivityCallbackType,
)
