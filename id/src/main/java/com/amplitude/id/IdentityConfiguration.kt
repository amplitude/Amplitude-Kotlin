package com.amplitude.id

import java.io.File

/**
 * Configuration used to initialize identity.
 */
data class IdentityConfiguration(
    val instanceName: String,
    val apiKey: String? = null,
    val experimentApiKey: String? = null,
    val identityStorageProvider: IdentityStorageProvider,
    val storageDirectory: File? = null
)
