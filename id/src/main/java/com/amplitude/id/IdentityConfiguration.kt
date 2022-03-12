package com.amplitude.id

data class IdentityConfiguration(
    val instanceName: String,
    val apiKey: String? = null,
    val experimentApiKey: String? = null,
    val identityStorageProvider: IdentityStorageProvider
)
