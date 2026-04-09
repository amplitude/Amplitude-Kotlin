package com.amplitude.android.sample

import android.app.Application
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureOption.Companion.ALL
import com.amplitude.android.DeadClickOptions
import com.amplitude.android.InteractionsOptions
import com.amplitude.android.RageClickOptions
import com.amplitude.android.network.NetworkTrackingOptions
import com.amplitude.android.network.NetworkTrackingOptions.CaptureBody
import com.amplitude.android.network.NetworkTrackingOptions.CaptureHeader
import com.amplitude.android.network.NetworkTrackingOptions.CaptureRule
import com.amplitude.android.network.NetworkTrackingOptions.URLPattern
import com.amplitude.android.network.NetworkTrackingPlugin
import com.amplitude.common.Logger
import com.amplitude.core.Constants
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.experiment.Experiment
import com.amplitude.experiment.ExperimentConfig
import okhttp3.OkHttpClient

class MainApplication : Application() {
    companion object {
        lateinit var amplitude: Amplitude
        lateinit var sharedOkHttpClient: OkHttpClient
        const val AMPLITUDE_API_KEY = BuildConfig.AMPLITUDE_API_KEY
        const val EXPERIMENT_API_KEY = BuildConfig.EXPERIMENT_API_KEY
    }

    override fun onCreate() {
        super.onCreate()

        // Multiple rules to showcase different capture configurations.
        // "Last matching rule wins" — more specific URL rules override the fallback.
        val networkTrackingPlugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(
                    captureRules =
                        listOf(
                            // Rule 1: Fallback — capture errors with no headers/body
                            CaptureRule(
                                hosts = listOf("*"),
                                statusCodeRange = (400..599).toList(),
                            ),
                            // Rule 2: Exact URL — capture response headers only
                            CaptureRule(
                                urls = listOf(URLPattern.Exact("https://httpbin.org/get")),
                                statusCodeRange = (0..599).toList(),
                                responseHeaders =
                                    CaptureHeader(
                                        allowlist = listOf("x-custom"),
                                        captureSafeHeaders = true,
                                    ),
                            ),
                            // Rule 3: Regex URL + POST method — capture body with allowlist/blocklist
                            CaptureRule(
                                urls = listOf(URLPattern.Regex(".*httpbin\\.org/post")),
                                methods = listOf("POST"),
                                statusCodeRange = (0..599).toList(),
                                requestBody = CaptureBody(allowlist = listOf("user/*"), excludelist = listOf("**/password")),
                                responseBody = CaptureBody(allowlist = listOf("**")),
                            ),
                            // Rule 4: Exact URL — capture headers (safe + custom, Authorization blocked)
                            CaptureRule(
                                urls = listOf(URLPattern.Exact("https://httpbin.org/headers")),
                                statusCodeRange = (0..599).toList(),
                                requestHeaders =
                                    CaptureHeader(
                                        allowlist = listOf("x-custom"),
                                        captureSafeHeaders = true,
                                    ),
                                responseHeaders = CaptureHeader(captureSafeHeaders = true),
                            ),
                            // Rule 5: Status endpoints — capture full headers + body
                            CaptureRule(
                                urls = listOf(URLPattern.Regex(".*httpbin\\.org/status/.*")),
                                statusCodeRange = (0..599).toList(),
                                requestHeaders = CaptureHeader(captureSafeHeaders = true),
                                responseHeaders = CaptureHeader(captureSafeHeaders = true),
                                requestBody = CaptureBody(allowlist = listOf("**")),
                                responseBody = CaptureBody(allowlist = listOf("**")),
                            ),
                        ),
                    ignoreAmplitudeRequests = true,
                ),
            )

        sharedOkHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(networkTrackingPlugin)
                .build()

        amplitude =
            Amplitude(AMPLITUDE_API_KEY, applicationContext) {
                autocapture = ALL
                httpClient =
                    CustomOkHttpClient(
                        apiKey = AMPLITUDE_API_KEY,
                        apiHost = Constants.DEFAULT_API_HOST,
                        okHttpClient = sharedOkHttpClient,
                    )
                interactionsOptions =
                    InteractionsOptions(
                        RageClickOptions(enabled = true),
                        // requires session replay to be enabled
                        DeadClickOptions(enabled = false),
                    )
            }

        amplitude.add(networkTrackingPlugin)

        // Sample for Experiment Integration
        val experimentConfig =
            ExperimentConfig.builder()
                .debug(true)
                .build()

        val experimentClient = Experiment.initializeWithAmplitudeAnalytics(this, EXPERIMENT_API_KEY, experimentConfig)

        try {
            experimentClient.fetch(null).get()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // set app to debug mode
        amplitude.logger.logMode = Logger.LogMode.DEBUG

        // add sample plugin
        amplitude.add(
            object : Plugin {
                override val type: Plugin.Type = Plugin.Type.Enrichment
                override lateinit var amplitude: com.amplitude.core.Amplitude

                override fun execute(event: BaseEvent): BaseEvent {
                    event.eventProperties = event.eventProperties ?: mutableMapOf()
                    event.eventProperties?.put("custom android event property", "test")
                    return event
                }
            },
        )

        // add the troubleshooting plugin for debugging
        amplitude.add(TroubleShootingPlugin())

        // identify a sample user
        amplitude.setUserId("android-kotlin-sample-user")
    }
}
