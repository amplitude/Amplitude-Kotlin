@file:OptIn(com.amplitude.android.GuardedAmplitudeFeature::class)

package com.amplitude.verification.experiment

import android.app.Application
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.Configuration
import com.amplitude.core.ServerZone
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertTrue

internal const val VERIFICATION_API_KEY = "verification-api-key"

internal fun createVerificationAmplitude(
    application: Application,
    name: String,
    apiKey: String = VERIFICATION_API_KEY,
    optOut: Boolean = false,
    deviceId: String? = "$name-device",
    sessionId: Long? = 42L,
    serverZone: ServerZone = ServerZone.US,
): Amplitude =
    Amplitude(
        Configuration(
            apiKey = apiKey,
            context = application,
            instanceName = "sdk-verification-experiment-$name-${System.nanoTime()}",
            optOut = optOut,
            storageProvider = InMemoryStorageProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
            autocapture = setOf(AutocaptureOption.SESSIONS),
            deviceId = deviceId,
            sessionId = sessionId,
            offline = true,
            serverZone = serverZone,
            enableAutocaptureRemoteConfig = false,
            enableDiagnostics = false,
        ),
    )

internal fun createMockServer(responseBody: String): MockWebServer =
    MockWebServer().apply {
        dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setBody(responseBody)
            }
        start()
    }

internal suspend fun awaitCondition(condition: () -> Boolean) {
    withTimeout(5_000L) {
        while (!condition()) {
            delay(10L)
        }
    }
    assertTrue(condition())
}
