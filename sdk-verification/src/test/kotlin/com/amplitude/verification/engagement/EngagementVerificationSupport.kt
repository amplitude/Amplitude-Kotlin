@file:OptIn(com.amplitude.android.GuardedAmplitudeFeature::class)

package com.amplitude.verification.engagement

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.engagement.AmplitudeEngagementPlugin
import com.amplitude.android.engagement.AmplitudeInitOptions
import com.amplitude.android.engagement.engagement
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.core.platform.PluginHost
import com.amplitude.id.IMIdentityStorageProvider
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.fail
import org.robolectric.Shadows.shadowOf
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

internal fun createEngagementVerificationAmplitude(
    application: Application = ApplicationProvider.getApplicationContext(),
    name: String,
    optOut: Boolean = false,
): Amplitude =
    Amplitude(
        Configuration(
            apiKey = "engagement-verification-api-key",
            context = application,
            instanceName = "sdk-verification-engagement-$name-${System.nanoTime()}",
            optOut = optOut,
            storageProvider = InMemoryStorageProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
            deviceId = "$name-device",
            offline = true,
            enableAutocaptureRemoteConfig = false,
            enableDiagnostics = false,
        ),
    ).also { it.setUserId("$name-user") }

internal fun MockWebServer.engagementOptions(): AmplitudeInitOptions {
    val baseUrl = url("/").toString().removeSuffix("/")
    return AmplitudeInitOptions(
        serverUrl = baseUrl,
        cdnUrl = baseUrl,
        mediaUrl = baseUrl,
    )
}

internal fun MockWebServer.respondToEngagementRequests(): EngagementRequestRecorder {
    val recorder = EngagementRequestRecorder()
    dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                recorder.record(request)
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{}")
            }
        }
    return recorder
}

internal class EngagementRequestRecorder {
    private val requests = CopyOnWriteArrayList<EngagementRequest>()

    val size: Int
        get() = requests.size

    fun decideCount(): Int = requests.count { it.path == DECIDE_PATH }

    fun awaitDecideUser(
        afterIndex: Int = 0,
        predicate: (JSONObject) -> Boolean = { true },
    ): IndexedValue<JSONObject> {
        repeat(200) {
            requests.forEachIndexed { index, request ->
                if (index >= afterIndex && request.path == DECIDE_PATH) {
                    request.encodedUser?.decodeUser()?.let { user ->
                        if (predicate(user)) return IndexedValue(index, user)
                    }
                }
            }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        val observed =
            requests
                .filter { it.path == DECIDE_PATH }
                .map { request -> request.encodedUser?.decodeUser()?.toString() ?: "<missing user>" }
        fail("Engagement did not issue the expected /decide request within 5 seconds. Observed: $observed")
        error("unreachable")
    }

    fun awaitNoDecide() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        if (decideCount() != 0) {
            fail("Engagement issued a /decide request while the host was opted out")
        }
    }

    internal fun record(request: RecordedRequest) {
        requests += EngagementRequest(request.path?.substringBefore('?'), request.getHeader(USER_HEADER))
    }

    private fun String.decodeUser(): JSONObject =
        JSONObject(String(Base64.getDecoder().decode(this), StandardCharsets.UTF_8))

    private data class EngagementRequest(
        val path: String?,
        val encodedUser: String?,
    )

    private companion object {
        const val DECIDE_PATH = "/sdk/v1/decide"
        const val USER_HEADER = "X-Amp-User"
    }
}

internal fun awaitEngagement(host: PluginHost): AmplitudeEngagementPlugin {
    repeat(200) {
        shadowOf(Looper.getMainLooper()).idle()
        host.engagement?.let { return it }
        Thread.sleep(25)
    }
    fail("Engagement plugin did not initialize within 5 seconds")
    error("unreachable")
}

internal fun awaitNoEngagement(host: PluginHost) {
    repeat(200) {
        shadowOf(Looper.getMainLooper()).idle()
        if (host.engagement == null) return
        Thread.sleep(25)
    }
    fail("Engagement plugin remained attached after 5 seconds")
}
