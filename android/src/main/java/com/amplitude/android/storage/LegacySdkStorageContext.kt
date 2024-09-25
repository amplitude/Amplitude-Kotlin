package com.amplitude.android.storage

import com.amplitude.android.Amplitude
import com.amplitude.android.migration.DatabaseStorage
import com.amplitude.android.migration.DatabaseStorageProvider
import com.amplitude.android.migration.RemnantDataMigration

internal class LegacySdkStorageContext(val amplitude: Amplitude) {
    private val databaseStorage: DatabaseStorage = DatabaseStorageProvider.getStorage(amplitude)

    suspend fun migrateToLatestVersion() {
        val remnantDataMigration = RemnantDataMigration(amplitude, databaseStorage)
        remnantDataMigration.execute()
    }
}