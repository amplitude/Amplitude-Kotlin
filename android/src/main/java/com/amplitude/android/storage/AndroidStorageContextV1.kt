package com.amplitude.android.storage

import android.content.Context
import android.content.SharedPreferences
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.migration.AndroidStorageMigration
import com.amplitude.android.migration.IdentityStorageMigration
import com.amplitude.core.utilities.FileStorage
import com.amplitude.id.FileIdentityStorage
import com.amplitude.id.IdentityConfiguration
import java.io.File

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
    private val eventsStorage: AndroidStorageV2

    /**
     * Stores all identity data in storage (user id, device id etc)
     */
    private val identityStorage: FileIdentityStorage

    /**
     * Stores identifies intercepted by the SDK to reduce data sent over to the server
     */
    private val identifyInterceptStorage: AndroidStorageV2

    private val storageDirectories = mutableListOf<File>()

    init {
        eventsStorage = createAndroidStorage(
            configuration,
            "amplitude-disk-queue",
            "amplitude-android-${configuration.apiKey}"
        )

        identifyInterceptStorage = createAndroidStorage(
            configuration,
            "amplitude-identify-intercept-disk-queue",
            "amplitude-identify-intercept-${configuration.apiKey}"
        )

        val identityConfig = generateIdentityConfiguration(amplitude, configuration)
        storageDirectories.add(identityConfig.storageDirectory)
        identityStorage = FileIdentityStorage(
            identityConfig
        )
    }

    private fun createAndroidStorage(
        configuration: Configuration,
        storageDirName: String,
        sharedPreferencesName: String
    ): AndroidStorageV2 {
        val storageDirectory = configuration.context.getDir(storageDirName, Context.MODE_PRIVATE)
        storageDirectories.add(storageDirectory)

        val sharedPreferences =
            configuration.context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        return AndroidStorageV2(
            configuration.apiKey,
            configuration.loggerProvider.getLogger(amplitude),
            sharedPreferences,
            storageDirectory,
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
            val migrator = AndroidStorageMigration(eventsStorage, it, amplitude.logger)
            migrator.execute()
        }

        (amplitude.identifyInterceptStorage as? AndroidStorageV2)?.let {
            val migrator =
                AndroidStorageMigration(identifyInterceptStorage, it, amplitude.logger)
            migrator.execute()
        }

        for (dir in storageDirectories) {
            if (dir.list()?.isEmpty() == true) {
                dir.delete()
            }
        }
    }
}