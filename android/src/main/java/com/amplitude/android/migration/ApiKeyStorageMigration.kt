package com.amplitude.android.migration

import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.utilities.AndroidStorage

class ApiKeyStorageMigration(
    private val amplitude: Amplitude
) {
    suspend fun execute() {
        val configuration = amplitude.configuration as Configuration
        val logger = amplitude.logger

        val storage = amplitude.storage as? AndroidStorage
        if (storage != null) {
            val apiKeyStorage = AndroidStorage(configuration.context, configuration.apiKey, logger, storage.prefix)
            StorageKeyMigration(apiKeyStorage, storage, logger).execute()
        }

        val identifyInterceptStorage = amplitude.identifyInterceptStorage as? AndroidStorage
        if (identifyInterceptStorage != null) {
            val apiKeyStorage = AndroidStorage(configuration.context, configuration.apiKey, logger, identifyInterceptStorage.prefix)
            StorageKeyMigration(apiKeyStorage, identifyInterceptStorage, logger).execute()
        }
    }
}
