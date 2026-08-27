package com.amplitude.unified

import android.content.Context
import com.amplitude.android.Amplitude
import com.amplitude.android.engagement.AmplitudeEngagementPlugin
import com.amplitude.android.engagement.AmplitudeEngagementPluginFactory
import com.amplitude.android.engagement.engagement
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.android.plugins.sessionReplay
import com.amplitude.android.sessionreplay.SessionReplay
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.PluginHost
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.experiment.AmplitudeExperimentPlugin
import com.amplitude.experiment.ExperimentClient
import java.util.concurrent.CancellationException

/**
 * Unified Android entry point that preserves the complete Analytics API and installs owned blades.
 *
 * Blades are installed in Session Replay, Experiment, then Guides and Surveys order. A failed blade
 * is logged and does not prevent later blades or Analytics from operating.
 */
public open class AmplitudeUnified internal constructor(
    private val unifiedConfiguration: UnifiedConfiguration,
    private val pluginFactory: UnifiedPluginFactory,
) : Amplitude(unifiedConfiguration.analytics) {
    /**
     * Creates a unified client and applies [configure] before installing blades.
     */
    @JvmOverloads
    public constructor(
        apiKey: String,
        applicationContext: Context,
        configure: UnifiedConfigurationBuilder.() -> Unit = {},
    ) : this(
        UnifiedConfigurationBuilder(apiKey, applicationContext).apply(configure).buildSnapshot(),
        DefaultUnifiedPluginFactory(applicationContext.applicationContext ?: applicationContext),
    )

    /** Creates a unified client from [configurationBuilder]. */
    public constructor(configurationBuilder: UnifiedConfigurationBuilder) : this(
        configurationBuilder.buildSnapshot().let { configuration ->
            configuration to DefaultUnifiedPluginFactory(configuration.applicationContext)
        },
    )

    private constructor(configurationAndFactory: Pair<UnifiedConfiguration, UnifiedPluginFactory>) : this(
        configurationAndFactory.first,
        configurationAndFactory.second,
    )

    /** The Analytics client. This is the same unified instance. */
    public val analytics: AmplitudeUnified
        get() = this

    /** The installed Session Replay client, or `null` when unavailable. */
    public val sessionReplay: SessionReplay?
        get() = (this as PluginHost).sessionReplay

    /** The installed Experiment client, or `null` when unavailable. */
    public val experiment: ExperimentClient?
        get() =
            (plugin(AmplitudeExperimentPlugin.PLUGIN_NAME) as? AmplitudeExperimentPlugin)
                ?.experimentClient

    /** The installed Guides and Surveys facade, or `null` when unavailable. */
    public val engagement: AmplitudeEngagementPlugin?
        get() = (this as PluginHost).engagement

    init {
        add(UnifiedLibraryPlugin())
        installBlades()
    }

    private fun installBlades() {
        if (unifiedConfiguration.sessionReplay.enabled) {
            install(SessionReplayPlugin.PLUGIN_NAME) {
                pluginFactory.sessionReplay(unifiedConfiguration.sessionReplay)
            }
        }
        if (unifiedConfiguration.experiment.enabled) {
            install(AmplitudeExperimentPlugin.PLUGIN_NAME) {
                pluginFactory.experiment(unifiedConfiguration.experiment)
            }
        }
        if (unifiedConfiguration.engagement.enabled) {
            install(AmplitudeEngagementPluginFactory.NAME) {
                pluginFactory.engagement(unifiedConfiguration.engagement)
            }
        }
    }

    private fun install(
        name: String,
        createPlugin: () -> UniversalPlugin,
    ) {
        try {
            add(createPlugin())
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            logger.error("Failed to install plugin \"$name\": $exception")
        }
    }
}

internal interface UnifiedPluginFactory {
    fun sessionReplay(configuration: SessionReplayConfiguration): UniversalPlugin

    fun experiment(configuration: ExperimentConfiguration): UniversalPlugin

    fun engagement(configuration: EngagementConfiguration): UniversalPlugin
}

private class DefaultUnifiedPluginFactory(
    private val applicationContext: Context,
) : UnifiedPluginFactory {
    override fun sessionReplay(configuration: SessionReplayConfiguration): UniversalPlugin =
        SessionReplayPlugin(
            sampleRate = configuration.sampleRate,
            enableRemoteConfig = configuration.enableRemoteConfig,
            serverUrl = configuration.serverUrl,
            bandwidthLimitBytes = configuration.bandwidthLimitBytes,
            storageLimitMB = configuration.storageLimitMB,
            privacyConfig = configuration.privacyConfig,
            autoStart = configuration.autoStart,
            recordLogOptions = configuration.recordLogOptions,
            captureWebViews = configuration.captureWebViews,
            context = applicationContext,
        )

    override fun experiment(configuration: ExperimentConfiguration): UniversalPlugin =
        AmplitudeExperimentPlugin(
            context = applicationContext,
            config = configuration.config,
        )

    override fun engagement(configuration: EngagementConfiguration): UniversalPlugin =
        AmplitudeEngagementPluginFactory.make(
            context = applicationContext,
            options = configuration.options,
        )
}

private class UnifiedLibraryPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override val name: String = "com.amplitude.unified"
    override lateinit var amplitude: com.amplitude.core.Amplitude

    override fun execute(event: BaseEvent): BaseEvent {
        if (event.library == null) {
            event.library = "amplitude-unified-android/${BuildConfig.UNIFIED_VERSION}"
        }
        return event
    }
}
