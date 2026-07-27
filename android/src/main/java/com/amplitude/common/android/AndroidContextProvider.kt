package com.amplitude.common.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings.Secure
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.amplitude.common.ContextProvider
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.util.Locale

public class AndroidContextProvider(
    private val context: Context,
    private val locationListening: Boolean,
    private val shouldTrackAdid: Boolean,
    private val shouldTrackAppSetId: Boolean,
) : ContextProvider {
    private val cachedInfo: CachedInfo by lazy { CachedInfo() }

    /**
     * Internal class serves as a cache
     */
    public inner class CachedInfo {
        public val advertisingId: String?
        public val country: String?
        public val versionName: String?
        public val osName: String
        public val osVersion: String
        public val brand: String
        public val manufacturer: String
        public val model: String
        public val carrier: String?
        public val language: String
        public var limitAdTrackingEnabled: Boolean = true
        public val gpsEnabled: Boolean
        public val appSetId: String?

        init {
            osName = OS_NAME
            osVersion = Build.VERSION.RELEASE
            brand = Build.BRAND
            manufacturer = Build.MANUFACTURER
            model = Build.MODEL
            language = locale.language

            // order is important here, some fields are checked before fetching the data
            advertisingId = if (shouldTrackAdid) fetchAdvertisingId() else null
            versionName = fetchVersionName()
            carrier = fetchCarrier()
            country = fetchCountry()
            gpsEnabled = checkGPSEnabled()
            appSetId = if (shouldTrackAppSetId) fetchAppSetId() else null
        }

        /**
         * Internal methods for getting raw information
         */
        private fun fetchVersionName(): String? {
            val packageInfo: PackageInfo
            try {
                packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                return packageInfo.versionName
            } catch (e: PackageManager.NameNotFoundException) {
                // do nothing
            } catch (e: Exception) {
                // do nothing
            }
            return null
        }

        private fun fetchCarrier(): String? {
            try {
                val manager =
                    context
                        .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                return manager.networkOperatorName
            } catch (e: Exception) {
                // Failed to get network operator name from network
            }
            return null
        }

        private fun fetchCountry(): String {
            // This should not be called on the main thread.

            // Prioritize reverse geocode, but until we have a result from that,
            // we try to grab the country from the network, and finally the locale
            var country = countryFromLocation
            if (!country.isNullOrEmpty()) {
                return country
            }
            country = countryFromNetwork
            return if (!country.isNullOrEmpty()) {
                country
            } else {
                countryFromLocale
            }
        } // Customized Android System without Google Play Service Installed// sometimes the location manager is unavailable// Bad lat / lon values can cause Geocoder to throw IllegalArgumentExceptions// failed to fetch geocoder// Failed to reverse geocode location

        // Failed to reverse geocode location
        private val countryFromLocation: String?
            get() {
                if (!locationListening) {
                    return null
                }
                val recent = mostRecentLocation
                if (recent != null) {
                    try {
                        if (Geocoder.isPresent()) {
                            val geocoder = geocoder
                            val addresses =
                                geocoder.getFromLocation(
                                    recent.latitude,
                                    recent.longitude,
                                    1,
                                )
                            if (addresses != null) {
                                for (address in addresses) {
                                    if (address != null) {
                                        return address.countryCode
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        // Failed to reverse geocode location
                    } catch (e: NullPointerException) {
                        // Failed to reverse geocode location
                    } catch (e: NoSuchMethodError) {
                        // failed to fetch geocoder
                    } catch (e: IllegalArgumentException) {
                        // Bad lat / lon values can cause Geocoder to throw IllegalArgumentExceptions
                    } catch (e: IllegalStateException) {
                        // sometimes the location manager is unavailable
                    } catch (e: SecurityException) {
                        // Customized Android System without Google Play Service Installed
                    }
                }
                return null
            }

        // Failed to get country from network
        private val countryFromNetwork: String?
            get() {
                try {
                    val manager =
                        context
                            .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    if (manager.phoneType != TelephonyManager.PHONE_TYPE_CDMA) {
                        val country = manager.networkCountryIso
                        if (country != null) {
                            return country.uppercase(Locale.US)
                        }
                    }
                } catch (e: Exception) {
                    // Failed to get country from network
                }
                return null
            }

        private val locale: Locale
            get() {
                val configuration = Resources.getSystem().configuration
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val localeList = configuration.locales
                    if (localeList.isEmpty) {
                        Locale.getDefault()
                    } else {
                        localeList.get(0)
                    }
                } else {
                    // for legacy versions, we're just going to use the deprecated field
                    @Suppress("DEPRECATION")
                    configuration.locale
                }
            }

        private val countryFromLocale: String
            get() = locale.country

        /**
         * This should not be called on the main thread.
         */
        private fun fetchAdvertisingId(): String? =
            if ("Amazon" == manufacturer) {
                fetchAndCacheAmazonAdvertisingId()
            } else {
                fetchAndCacheGoogleAdvertisingId()
            }

        private fun fetchAppSetId(): String? {
            try {
                val appSet = Class.forName("com.google.android.gms.appset.AppSet")
                val getClient = appSet.getMethod("getClient", Context::class.java)
                val appSetIdClient = getClient.invoke(null, context)
                val getAppSetIdInfo = appSetIdClient.javaClass.getMethod("getAppSetIdInfo")
                val taskWithAppSetInfo = getAppSetIdInfo.invoke(appSetIdClient)
                val tasks = Class.forName("com.google.android.gms.tasks.Tasks")
                val await = tasks.getMethod("await", Class.forName("com.google.android.gms.tasks.Task"))
                val appSetInfo = await.invoke(null, taskWithAppSetInfo)
                val getId = appSetInfo.javaClass.getMethod("getId")
                return getId.invoke(appSetInfo) as String
            } catch (e: ClassNotFoundException) {
                LogcatLogger.logger.warn("Google Play Services SDK not found for app set id!")
            } catch (e: InvocationTargetException) {
                LogcatLogger.logger.warn("Google Play Services not available for app set id")
            } catch (e: Exception) {
                LogcatLogger.logger.error(
                    "Encountered an error connecting to Google Play Services for app set id",
                )
            }

            return null
        }

        private fun fetchAndCacheAmazonAdvertisingId(): String? {
            val cr = context.contentResolver
            limitAdTrackingEnabled = Secure.getInt(cr, SETTING_LIMIT_AD_TRACKING, 0) == 1
            return Secure.getString(cr, SETTING_ADVERTISING_ID)
        }

        private fun fetchAndCacheGoogleAdvertisingId(): String? {
            try {
                val advertisingIdClient = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
                val getAdvertisingInfo =
                    advertisingIdClient.getMethod(
                        "getAdvertisingIdInfo",
                        Context::class.java,
                    )
                val advertisingInfo = getAdvertisingInfo.invoke(null, context)
                val isLimitAdTrackingEnabled =
                    advertisingInfo.javaClass.getMethod(
                        "isLimitAdTrackingEnabled",
                    )
                val limitAdTrackingEnabled =
                    isLimitAdTrackingEnabled
                        .invoke(advertisingInfo) as? Boolean
                this.limitAdTrackingEnabled =
                    limitAdTrackingEnabled != null && limitAdTrackingEnabled
                val getId = advertisingInfo.javaClass.getMethod("getId")
                return getId.invoke(advertisingInfo) as String
            } catch (e: ClassNotFoundException) {
                LogcatLogger.logger
                    .warn("Google Play Services SDK not found for advertising id!")
            } catch (e: InvocationTargetException) {
                LogcatLogger.logger
                    .warn("Google Play Services not available for advertising id")
            } catch (e: Exception) {
                LogcatLogger.logger.error(
                    "Encountered an error connecting to Google Play Services for advertising id",
                )
            }
            return null
        }

        private fun checkGPSEnabled(): Boolean {
            // This should not be called on the main thread.
            try {
                val gpsUtil = Class.forName("com.google.android.gms.common.GooglePlayServicesUtil")
                val getGPSAvailable =
                    gpsUtil.getMethod(
                        "isGooglePlayServicesAvailable",
                        Context::class.java,
                    )
                val status = getGPSAvailable.invoke(null, context) as? Int
                // status 0 corresponds to com.google.android.gms.common.ConnectionResult.SUCCESS;
                return status != null && status == 0
            } catch (e: NoClassDefFoundError) {
                LogcatLogger.logger.warn("Google Play Services Util not found!")
            } catch (e: ClassNotFoundException) {
                LogcatLogger.logger.warn("Google Play Services Util not found!")
            } catch (e: NoSuchMethodException) {
                LogcatLogger.logger.warn("Google Play Services not available")
            } catch (e: InvocationTargetException) {
                LogcatLogger.logger.warn("Google Play Services not available")
            } catch (e: IllegalAccessException) {
                LogcatLogger.logger.warn("Google Play Services not available")
            } catch (e: Exception) {
                LogcatLogger.logger.warn(
                    "Error when checking for Google Play Services: $e",
                )
            }
            return false
        }
    }

    public fun isGooglePlayServicesEnabled(): Boolean {
        return cachedInfo.gpsEnabled
    }

    public fun isLimitAdTrackingEnabled(): Boolean {
        return cachedInfo.limitAdTrackingEnabled
    }

    public val versionName: String?
        get() = cachedInfo.versionName
    public val osName: String
        get() = cachedInfo.osName
    public val osVersion: String
        get() = cachedInfo.osVersion
    public val brand: String
        get() = cachedInfo.brand
    public val manufacturer: String
        get() = cachedInfo.manufacturer
    public val model: String
        get() = cachedInfo.model
    public val carrier: String?
        get() = cachedInfo.carrier
    public val country: String?
        get() = cachedInfo.country
    public val language: String
        get() = cachedInfo.language
    public val advertisingId: String?
        get() = cachedInfo.advertisingId
    public val appSetId: String?
        get() = cachedInfo.appSetId // other causes// failed to get providers list
    // Don't crash if the device does not have location services.

    // It's possible that the location service is running out of process
    // and the remote getProviders call fails. Handle null provider lists.
    public val mostRecentLocation: Location?
        @SuppressLint("MissingPermission")
        get() {
            if (!locationListening) {
                return null
            }
            if (!(
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                )
            ) {
                return null
            }
            val locationManager =
                context
                    .getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    ?: return null

            // Don't crash if the device does not have location services.

            // It's possible that the location service is running out of process
            // and the remote getProviders call fails. Handle null provider lists.
            var providers: List<String?>? = null
            try {
                providers = locationManager.getProviders(true)
            } catch (e: SecurityException) {
                // failed to get providers list
            } catch (e: Exception) {
                // other causes
            }
            if (providers == null) {
                return null
            }
            val locations: MutableList<Location> = ArrayList()
            for (provider in providers) {
                var location: Location? = null
                try {
                    location = locationManager.getLastKnownLocation(provider!!)
                } catch (e: SecurityException) {
                    LogcatLogger.logger.warn("Failed to get most recent location")
                } catch (e: Exception) {
                    LogcatLogger.logger.warn("Failed to get most recent location")
                }
                if (location != null) {
                    locations.add(location)
                }
            }
            var maximumTimestamp: Long = -1
            var bestLocation: Location? = null
            for (location in locations) {
                if (location.time > maximumTimestamp) {
                    maximumTimestamp = location.time
                    bestLocation = location
                }
            }
            return bestLocation
        }

    private val geocoder: Geocoder
        get() = Geocoder(context, Locale.ENGLISH)

    public companion object {
        public const val OS_NAME: String = "android"
        public const val SETTING_LIMIT_AD_TRACKING: String = "limit_ad_tracking"
        public const val SETTING_ADVERTISING_ID: String = "advertising_id"
    }
}
