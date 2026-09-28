package com.amplitude.android.crash

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME = "amplitude_crash_tracking"
private const val KEY_ENABLED = "enabled"

/**
 * The last known crash-tracking decision, kept across app launches so the next launch can act on
 * it before remote config arrives. Defaults to disabled.
 */
internal class CrashTrackingEnabledStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
    }
}
