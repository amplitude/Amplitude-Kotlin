package com.amplitude.android.utilities

import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Publishes process visibility changes. */
internal class ProcessLifecycleObserver(
    private val lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
    private val time: Time = Time(),
) {
    data class Transition(val timestamp: Long, val foreground: Boolean)

    private val started = AtomicBoolean(false)
    private var foreground = false

    private val observer =
        object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = transition(true)

            override fun onStop(owner: LifecycleOwner) = transition(false)
        }

    // The plugin subscribes before start(), so replay is unnecessary. Buffer transitions
    // while its collector is busy without blocking lifecycle callbacks or dropping events.
    private val _events = MutableSharedFlow<Transition>(extraBufferCapacity = Int.MAX_VALUE)
    val events: SharedFlow<Transition> = _events.asSharedFlow()

    suspend fun start() =
        withContext(Dispatchers.Main) {
            if (!started.compareAndSet(false, true)) return@withContext

            foreground = false
            // addObserver synchronizes to the current state, including onStart when already
            // foregrounded. Do not synthesize another transition from currentState.
            lifecycle.addObserver(observer)
        }

    suspend fun stop() =
        withContext(NonCancellable + Dispatchers.Main) {
            if (!started.compareAndSet(true, false)) return@withContext

            lifecycle.removeObserver(observer)
        }

    @MainThread
    private fun transition(isForeground: Boolean) {
        if (foreground == isForeground) return
        foreground = isForeground
        _events.tryEmit(Transition(time.nowMillis(), isForeground))
    }
}
