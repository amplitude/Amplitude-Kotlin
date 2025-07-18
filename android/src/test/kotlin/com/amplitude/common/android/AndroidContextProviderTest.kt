package com.amplitude.common.android

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.provider.Settings.Secure
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdClient
import com.google.android.gms.appset.AppSetIdInfo
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before // Added import
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.Q])
class AndroidContextProviderTest {
    companion object {
        private const val TEST_BRAND = "brand"
        private const val TEST_MANUFACTURER = "manufacturer"
        private const val TEST_MODEL = "model"
        private const val TEST_CARRIER = "carrier"
        private val TEST_LOCALE: Locale = Locale.FRANCE
        private const val TEST_COUNTRY = "FR"
        private const val TEST_LANGUAGE = "fr"
        private const val TEST_NETWORK_COUNTRY = "GB"
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var androidContextProvider: AndroidContextProvider // Changed to lateinit var

    @Before // Added annotation
    fun setUp() { // Renamed init to setUp and made it a function
        ReflectionHelpers.setStaticField(Build::class.java, "BRAND", TEST_BRAND)
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", TEST_MANUFACTURER)
        ReflectionHelpers.setStaticField(Build::class.java, "MODEL", TEST_MODEL)

        Resources.getSystem().getConfiguration()
            .setLocales(LocaleList.forLanguageTags(TEST_LOCALE.toLanguageTag()))

        val manager =
            Shadows.shadowOf(
                context
                    .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager,
            )
        manager.setNetworkOperatorName(TEST_CARRIER)
        androidContextProvider =
            AndroidContextProvider(
                context = context,
                locationListening = true,
                shouldTrackAdid = false,
                shouldTrackAppSetId = true,
            )
    }

    @Test
    fun testGetBrand() {
        assertEquals(TEST_BRAND, androidContextProvider.brand)
    }

    @Test
    fun testGetManufacturer() {
        assertEquals(TEST_MANUFACTURER, androidContextProvider.manufacturer)
    }

    @Test
    fun testGetModel() {
        assertEquals(TEST_MODEL, androidContextProvider.model)
    }

    @Test
    fun testGetCarrier() {
        assertEquals(TEST_CARRIER, androidContextProvider.carrier)
    }

    @Test
    fun testGetCountry() {
        assertEquals(TEST_COUNTRY, androidContextProvider.country)
    }

    @Test
    fun testGetCountryFromNetwork() {
        val manager =
            Shadows.shadowOf(
                context
                    .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager,
            )
        manager.setNetworkCountryIso(TEST_NETWORK_COUNTRY)
        val deviceInfo =
            AndroidContextProvider(
                context = context,
                locationListening = true,
                shouldTrackAdid = false,
                shouldTrackAppSetId = true,
            )
        assertEquals(TEST_NETWORK_COUNTRY, deviceInfo.country)
    }

    @Test
    fun testGetLanguage() {
        assertEquals(TEST_LANGUAGE, androidContextProvider.language)
    }

    @Test
    fun testGetAdvertisingIdFromGoogleDevice() {
        mockkStatic(AdvertisingIdClient::class)
        val advertisingId = "advertisingId"
        val info =
            AdvertisingIdClient.Info(
                advertisingId,
                false,
            )
        try {
            every { AdvertisingIdClient.getAdvertisingIdInfo(context) } returns info
        } catch (e: Exception) {
            fail(e.toString())
        }
        val deviceInfo =
            AndroidContextProvider(
                context = context,
                locationListening = true,
                shouldTrackAdid = true,
                shouldTrackAppSetId = true,
            )

        // still get advertisingId even if limit ad tracking disabled
        assertEquals(advertisingId, deviceInfo.advertisingId)
        assertFalse(deviceInfo.isLimitAdTrackingEnabled())
        verify(exactly = 1) { AdvertisingIdClient.getAdvertisingIdInfo(context) }
    }

    @Test
    fun testGetAdvertisingIdFromGoogleDeviceDisabledTrackAdid() {
        mockkStatic(AdvertisingIdClient::class)
        val advertisingId = "advertisingId"
        val info =
            AdvertisingIdClient.Info(
                advertisingId,
                false,
            )
        try {
            every { AdvertisingIdClient.getAdvertisingIdInfo(context) } returns info
        } catch (e: Exception) {
            fail(e.toString())
        }
        val deviceInfo =
            AndroidContextProvider(
                context = context,
                locationListening = true,
                shouldTrackAdid = false,
                shouldTrackAppSetId = true,
            )

        assertNull(deviceInfo.advertisingId)
        assertTrue(deviceInfo.isLimitAdTrackingEnabled())
        verify(exactly = 0) { AdvertisingIdClient.getAdvertisingIdInfo(context) }
    }

    @Test
    fun testGetAdvertisingIdFromAmazonDevice() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Amazon")
        val advertisingId = "advertisingId"
        val cr = context.contentResolver
        Secure.putInt(cr, "limit_ad_tracking", 1)
        Secure.putString(cr, "advertising_id", advertisingId)
        val contextProvider =
            AndroidContextProvider(
                context = context,
                locationListening = true,
                shouldTrackAdid = true,
                shouldTrackAppSetId = true,
            )

        // still get advertisingID even if limit ad tracking enabled
        assertEquals(advertisingId, contextProvider.advertisingId)
        assertTrue(contextProvider.isLimitAdTrackingEnabled())
    }

    @Test
    fun testGetAdvertisingIdFromAmazonDeviceDisabledTrackAdid() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Amazon")
        val advertisingId = "advertisingId"
        val cr = context.contentResolver
        Secure.putInt(cr, "limit_ad_tracking", 1)
        Secure.putString(cr, "advertising_id", advertisingId)
        val contextProvider =
            AndroidContextProvider(
                context = context,
                locationListening = true,
                shouldTrackAdid = false,
                shouldTrackAppSetId = true,
            )

        assertNull(contextProvider.advertisingId)
        assertTrue(contextProvider.isLimitAdTrackingEnabled())
    }

    @Test
    fun testGetAppSetId() {
        mockkStatic(AppSet::class)
        mockkStatic("com.google.android.gms.tasks.Tasks")
        mockkStatic(Tasks::class)

        val appSetIdClient = mockk<AppSetIdClient>()

        val appSetIdInfo = mockk<AppSetIdInfo>()
        val task = mockTask()
        val appSetId = "appSetId"

        every { AppSet.getClient(context) } returns appSetIdClient
        every { appSetIdInfo.id } returns appSetId
        every { AppSet.getClient(context).appSetIdInfo } returns task
        every { task.result } returns appSetIdInfo
        every { Tasks.await(task) } returns appSetIdInfo

        val provider = AndroidContextProvider(context, true, true, true)
        val result = provider.appSetId

        assertEquals(appSetId, result)
    }

    private fun mockTask(exception: Exception? = null): Task<AppSetIdInfo> {
        val task: Task<AppSetIdInfo> = mockk(relaxed = true)
        every { task.isComplete } returns true
        every { task.exception } returns exception
        every { task.isCanceled } returns false
        val relaxedVoid: AppSetIdInfo = mockk(relaxed = true)
        every { task.result } returns relaxedVoid
        return task
    }

    @Test
    fun testGetAppSetIdDeviceDisabledTrackAdid() {
        mockkStatic(AppSet::class)
        mockkStatic("com.google.android.gms.tasks.Tasks")
        mockkStatic(Tasks::class)

        val appSetIdClient = mockk<AppSetIdClient>()

        val appSetIdInfo = mockk<AppSetIdInfo>()
        val task = mockTask()
        val appSetId = "appSetId"

        every { AppSet.getClient(context) } returns appSetIdClient
        every { appSetIdInfo.id } returns appSetId
        every { AppSet.getClient(context).appSetIdInfo } returns task
        every { task.result } returns appSetIdInfo
        every { Tasks.await(task) } returns appSetIdInfo

        val provider =
            AndroidContextProvider(
                context = context,
                locationListening = true,
                shouldTrackAdid = true,
                shouldTrackAppSetId = false,
            )
        val result = provider.appSetId

        assertEquals(null, result)
    }

    @Test
    fun testGPSDisabled() {
        // GPS not enabled
        val deviceInfo =
            AndroidContextProvider(
                context = context,
                locationListening = true,
                shouldTrackAdid = false,
                shouldTrackAppSetId = true,
            )
        assertFalse(deviceInfo.isGooglePlayServicesEnabled())

        // GPS bundled but not enabled, GooglePlayUtils.isAvailable returns non-0 value
        mockkStatic(GooglePlayServicesUtil::class)
        try {
            every { GooglePlayServicesUtil.isGooglePlayServicesAvailable(context) } returns 1
        } catch (e: Exception) {
            fail(e.toString())
        }
        assertFalse(deviceInfo.isGooglePlayServicesEnabled())
    }

    @Test
    fun testGPSEnabled() {
        mockkStatic(GooglePlayServicesUtil::class)
        try {
            every { GooglePlayServicesUtil.isGooglePlayServicesAvailable(context) } returns ConnectionResult.SUCCESS
        } catch (e: Exception) {
            fail(e.toString())
        }
        assertTrue(androidContextProvider.isGooglePlayServicesEnabled())
    }

    @Test
    fun testNoLocation() {
        val deviceInfo =
            AndroidContextProvider(
                context = context,
                locationListening = true,
                shouldTrackAdid = false,
                shouldTrackAppSetId = true,
            )
        val recent = deviceInfo.mostRecentLocation
        assertNull(recent)
    }
}
