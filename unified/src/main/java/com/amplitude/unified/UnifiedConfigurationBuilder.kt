package com.amplitude.unified

import android.content.Context
import com.amplitude.android.Configuration
import com.amplitude.android.ConfigurationBuilder
import com.amplitude.android.sessionreplay.config.PrivacyConfig
import com.amplitude.android.sessionreplay.config.RecordLogOptions
import com.amplitude.experiment.ExperimentConfig

/**
 * Configures [AmplitudeUnified] without coupling its constructor to future options.
 *
 * Kotlin usage:
 * ```
 * AmplitudeUnified("api-key", applicationContext) {
 *     analytics { flushQueueSize = 20 }
 *     sessionReplay { sampleRate = 1.0 }
 *     experiment { config = ExperimentConfig.builder().build() }
 * }
 * ```
 */
public open class UnifiedConfigurationBuilder(
    apiKey: String,
    applicationContext: Context,
) {
    private val applicationContext: Context = applicationContext.applicationContext ?: applicationContext

    /** Existing Analytics Android configuration builder. */
    public val analytics: ConfigurationBuilder = ConfigurationBuilder(apiKey, this.applicationContext)

    /** Session Replay-specific configuration. */
    public val sessionReplay: SessionReplayConfigurationBuilder = SessionReplayConfigurationBuilder()

    /** Experiment-specific configuration. */
    public val experiment: ExperimentConfigurationBuilder = ExperimentConfigurationBuilder()

    /** Applies [configure] to the existing Analytics Android configuration builder. */
    public fun analytics(configure: ConfigurationBuilder.() -> Unit): UnifiedConfigurationBuilder =
        apply { analytics.configure() }

    /** Applies [configure] to Session Replay-specific options. */
    public fun sessionReplay(configure: SessionReplayConfigurationBuilder.() -> Unit): UnifiedConfigurationBuilder =
        apply { sessionReplay.configure() }

    /** Applies [configure] to Experiment-specific options. */
    public fun experiment(configure: ExperimentConfigurationBuilder.() -> Unit): UnifiedConfigurationBuilder =
        apply { experiment.configure() }

    /** Builds an [AmplitudeUnified] instance from the current configuration. */
    public fun build(): AmplitudeUnified = AmplitudeUnified(this)

    internal fun buildSnapshot(): UnifiedConfiguration =
        UnifiedConfiguration(
            applicationContext = applicationContext,
            analytics = analytics.build(),
            sessionReplay = sessionReplay.buildSnapshot(),
            experiment = experiment.buildSnapshot(),
        )
}

/** Session Replay options owned by the unified wrapper. */
public open class SessionReplayConfigurationBuilder {
    /** Whether Session Replay is installed. */
    public var enabled: Boolean = true

    /** Percentage of sessions to record, from `0.0` to `1.0`. */
    public var sampleRate: Number = 0.0

    /** Whether Session Replay remote configuration is enabled. */
    public var enableRemoteConfig: Boolean = true

    /** Optional Session Replay proxy URL. */
    public var serverUrl: String? = null

    /** Optional daily metered-network bandwidth limit. */
    public var bandwidthLimitBytes: Int? = null

    /** Optional Session Replay storage limit in megabytes. */
    public var storageLimitMB: Int? = null

    /** Session Replay privacy configuration. */
    public var privacyConfig: PrivacyConfig = PrivacyConfig()

    /** Whether capture starts automatically after plugin setup. */
    public var autoStart: Boolean = true

    /** Session Replay log recording options. */
    public var recordLogOptions: RecordLogOptions = RecordLogOptions()

    /** Whether eligible WebViews are captured. */
    public var captureWebViews: Boolean = false

    internal fun buildSnapshot(): SessionReplayConfiguration =
        SessionReplayConfiguration(
            enabled = enabled,
            sampleRate = sampleRate,
            enableRemoteConfig = enableRemoteConfig,
            serverUrl = serverUrl,
            bandwidthLimitBytes = bandwidthLimitBytes,
            storageLimitMB = storageLimitMB,
            privacyConfig = privacyConfig.copy(),
            autoStart = autoStart,
            recordLogOptions = recordLogOptions.copy(),
            captureWebViews = captureWebViews,
        )
}

/** Experiment options owned by the unified wrapper. */
public open class ExperimentConfigurationBuilder {
    /** Whether Experiment is installed. */
    public var enabled: Boolean = true

    /** Optional Experiment deployment key. Defaults to the Analytics API key. */
    public var deploymentKey: String? = null

    /** Product-specific Experiment configuration. Shared host values override matching fields. */
    public var config: ExperimentConfig = ExperimentConfig()

    internal fun buildSnapshot(): ExperimentConfiguration =
        ExperimentConfiguration(
            enabled = enabled,
            deploymentKey = deploymentKey,
            config = config,
        )
}

internal data class UnifiedConfiguration(
    val applicationContext: Context,
    val analytics: Configuration,
    val sessionReplay: SessionReplayConfiguration,
    val experiment: ExperimentConfiguration,
)

internal data class SessionReplayConfiguration(
    val enabled: Boolean,
    val sampleRate: Number,
    val enableRemoteConfig: Boolean,
    val serverUrl: String?,
    val bandwidthLimitBytes: Int?,
    val storageLimitMB: Int?,
    val privacyConfig: PrivacyConfig,
    val autoStart: Boolean,
    val recordLogOptions: RecordLogOptions,
    val captureWebViews: Boolean,
)

internal data class ExperimentConfiguration(
    val enabled: Boolean,
    val deploymentKey: String?,
    val config: ExperimentConfig,
)
