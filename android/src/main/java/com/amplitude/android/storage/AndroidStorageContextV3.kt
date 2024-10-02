package com.amplitude.android.storage

import com.amplitude.android.Configuration
import com.amplitude.core.StorageProvider
import com.amplitude.id.FileIdentityStorageProvider
import com.amplitude.id.IdentityStorageProvider
import java.io.File

/**
 * Data is stored in storage in the following format
 * /amplitude
 *   /package_name
 *     /instance_name
 *       /analytics
 *         /events (stores the events)
 *           /{instance_name}-0
 *           /{instance_name}-1.tmp
 *         /identify-intercept (stores the intercepted identifies)
 *           /{instance_name}-0
 *           /{instance_name}-1.tmp
 *         /identity.properties (this stores the user id, device id and api key)
 * /shared_prefs
 *   /amplitude-android-{instance_name}.xml
 */
internal object AndroidStorageContextV3 {
    /**
     * Stores all event data in storage
     */
    val eventsStorageProvider: StorageProvider = AndroidEventsStorageProviderV2()

    /**
     * Stores all identity data in storage (user id, device id etc)
     */
    val identityStorageProvider: IdentityStorageProvider = FileIdentityStorageProvider()

    /**
     * Stores identifies intercepted by the SDK to reduce data sent over to the server
     */
    val identifyInterceptStorageProvider: StorageProvider = AndroidIdentifyInterceptStorageProviderV2()

    fun getEventsStorageDirectory(configuration: Configuration): File {
        return File(configuration.getStorageDirectory(), "events")
    }

    fun getIdentifyInterceptStorageDirectory(configuration: Configuration): File {
        return File(configuration.getStorageDirectory(), "identify-intercept")
    }

    fun getIdentityStorageDirectory(configuration: Configuration): File {
        return configuration.getStorageDirectory()
    }

    fun getIdentityStorageFileName(): String {
        return "identity"
    }
}
