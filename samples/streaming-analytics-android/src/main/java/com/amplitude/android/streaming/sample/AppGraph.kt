package com.amplitude.android.streaming.sample

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureOption
import com.amplitude.common.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory

@DependencyGraph(AppScope::class)
internal interface AppGraph {
    val xmlPlayerViewModel: Provider<XmlPlayerViewModel>
    val composePlayerViewModel: Provider<ComposePlayerViewModel>

    @Provides
    @SingleIn(AppScope::class)
    fun provideAmplitude(application: Application): Amplitude =
        Amplitude(BuildConfig.AMPLITUDE_API_KEY, application) {
            autocapture = setOf(AutocaptureOption.SESSIONS)
        }.also { amplitude ->
            amplitude.logger.logMode = Logger.LogMode.DEBUG
            amplitude.setUserId("streaming-analytics-sample-user")
        }

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
        ): AppGraph
    }
}

internal fun createAppGraph(application: Application): AppGraph =
    createGraphFactory<AppGraph.Factory>().create(application)

internal fun ComponentActivity.appGraph(): AppGraph = (application as MainApplication).appGraph

internal inline fun <reified VM : ViewModel> ComponentActivity.injectedViewModel(
    crossinline accessor: AppGraph.() -> Provider<VM>,
): Lazy<VM> =
    viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return appGraph().accessor().invoke() as T
            }
        }
    }
