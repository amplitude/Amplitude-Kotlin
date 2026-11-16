@file:OptIn(com.amplitude.android.GuardedAmplitudeFeature::class)

package com.amplitude.verification.sessionreplay

import android.app.Application
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.Configuration
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider

internal fun createSessionReplayVerificationAmplitude(
    application: Application,
    name: String,
    optOut: Boolean = false,
    deviceId: String? = "$name-device",
    sessionId: Long? = 42L,
): Amplitude =
    Amplitude(
        Configuration(
            apiKey = "session-replay-verification-api-key",
            context = application,
            instanceName = "sdk-verification-session-replay-$name-${System.nanoTime()}",
            optOut = optOut,
            storageProvider = InMemoryStorageProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
            autocapture = setOf(AutocaptureOption.SESSIONS),
            deviceId = deviceId,
            sessionId = sessionId,
            offline = true,
            enableAutocaptureRemoteConfig = false,
            enableDiagnostics = false,
        ),
    )
