package com.amplitude.android.migration

import android.content.Context
import android.content.SharedPreferences
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.storage.AndroidStorageContextV1
import com.amplitude.android.storage.AndroidStorageContextV2
import com.amplitude.android.storage.LegacySdkStorageContext
import com.amplitude.android.storage.StorageVersion
import com.amplitude.common.Logger

internal class MigrationManager(private val amplitude: Amplitude) {
    private val sharedPreferences: SharedPreferences
    private val config: Configuration = amplitude.configuration as Configuration
    private val logger: Logger = amplitude.logger
    private val currentStorageVersion: Int

    init {
        sharedPreferences = config.context.getSharedPreferences(
            "amplitude-android-${config.instanceName}",
            Context.MODE_PRIVATE
        )
        currentStorageVersion = sharedPreferences.getInt("storage_version", 0)
    }

    suspend fun migrateOldStorage() {
        if (currentStorageVersion < StorageVersion.V3.rawValue) {
            logger.debug("Migrating storage to version ${StorageVersion.V3.rawValue}")
            safePerformMigration()
        } else {
            amplitude.logger.debug("Storage already at version ${StorageVersion.V3.rawValue}")
        }
    }

    internal suspend fun safePerformMigration() {
        try {
            val config = amplitude.configuration as Configuration
            if (config.migrateLegacyData) {
                val legacySdkStorageContext = LegacySdkStorageContext(amplitude)
                legacySdkStorageContext.migrateToLatestVersion()
            }

            val storageContextV1 = AndroidStorageContextV1(amplitude, config)
            storageContextV1.migrateToLatestVersion()

            val storageContextV2 = AndroidStorageContextV2(amplitude, config)
            storageContextV2.migrateToLatestVersion()

            sharedPreferences.edit().putInt("storage_version", StorageVersion.V3.rawValue).apply()
        } catch (ex: Throwable) {
            logger.error("Failed to migrate storage: ${ex.message}")
        }
    }
}