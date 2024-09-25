package com.amplitude.android.storage

import android.content.Context
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.migration.AndroidStorageMigration
import com.amplitude.android.migration.IdentityStorageMigration
import com.amplitude.android.utilities.AndroidStorage
import com.amplitude.core.utilities.FileStorage
import com.amplitude.id.FileIdentityStorage
import com.amplitude.id.IdentityConfiguration

/**
 * Data is stored in storage in the following format
 * /app_amplitude-kotlin-{api_key}
 *   /amplitude-identify-{api_key}.properties (this stores the user id, device id and api key)
 * /app_amplitude-disk-queue (this stores the events)
 *   /{instance_name}-0
 *   /{instance_name}-1.tmp
 * /app_amplitude-identify-intercept-disk-queue
 *   /{instance_name}-0
 *   /{instance_name}-1.tmp
 * /shared_prefs
 *   /amplitude-android-{api_key}.xml
 */
internal class AndroidStorageContextV1(
    private val amplitude: Amplitude,
    configuration: Configuration
) {
    /**
     * Stores all event data in storage
     */
    private val eventsStorage: AndroidStorage

    /**
     * Stores all identity data in storage (user id, device id etc)
     */
    private val identityStorage: FileIdentityStorage

    /**
     * Stores identifies intercepted by the SDK to reduce data sent over to the server
     */
    private val identifyInterceptStorage: AndroidStorage

    init {
        eventsStorage = AndroidStorage(
            configuration.context,
            configuration.apiKey,
            configuration.loggerProvider.getLogger(amplitude),
            null,
            amplitude.diagnostics
        )

        identityStorage = FileIdentityStorage(
            generateIdentityConfiguration(amplitude, configuration)
        )

        identifyInterceptStorage = AndroidStorage(
            configuration.context,
            configuration.instanceName,
            configuration.loggerProvider.getLogger(amplitude),
            "amplitude-identify-intercept",
            amplitude.diagnostics
        )
    }

    private fun generateIdentityConfiguration(
        amplitude: Amplitude,
        configuration: Configuration
    ): IdentityConfiguration {
        val storageDirectory = configuration.context.getDir(
            "${FileStorage.STORAGE_PREFIX}-${configuration.apiKey}",
            Context.MODE_PRIVATE
        )

        return IdentityConfiguration(
            instanceName = configuration.instanceName,
            apiKey = configuration.apiKey,
            identityStorageProvider = configuration.identityStorageProvider,
            storageDirectory = storageDirectory,
            logger = configuration.loggerProvider.getLogger(amplitude),
            fileName = "amplitude-identify-${configuration.apiKey}"
        )
    }

    suspend fun migrateToLatestVersion() {
        val identityMigration =
            IdentityStorageMigration(identityStorage, amplitude.identityStorage, amplitude.logger)
        identityMigration.execute()

        (amplitude.storage as? AndroidStorageV2)?.let {
            val migrator = AndroidStorageMigration(eventsStorage.storageV2, it, amplitude.logger)
            migrator.execute()
        }

        (amplitude.identifyInterceptStorage as? AndroidStorageV2)?.let {
            val migrator =
                AndroidStorageMigration(identifyInterceptStorage.storageV2, it, amplitude.logger)
            migrator.execute()
        }
    }
}