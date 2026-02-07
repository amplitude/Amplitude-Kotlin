package com.amplitude.core.diagnostics

import com.amplitude.core.RestrictedAmplitudeFeature

/**
 * Provider interface for lazy access to [DiagnosticsClient].
 *
 * This interface is used to break circular dependencies during SDK initialization.
 * The dependency chain `storage -> diagnosticsClient -> remoteConfigClient -> storage`
 * would cause a StackOverflowError if diagnosticsClient were accessed directly during
 * storage initialization. By using a provider, the access is deferred until actually needed.
 */
@RestrictedAmplitudeFeature
fun interface DiagnosticsClientProvider {
    /**
     * Returns the [DiagnosticsClient] instance.
     * This method should only be called after the SDK is fully initialized.
     */
    fun get(): DiagnosticsClient
}
