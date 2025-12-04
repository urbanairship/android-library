/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.AirshipConfigOptions.ConfigException
import java.io.IOException
import java.io.InputStream
import java.util.Properties
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This class tests the parsing of each type of configuration
 * value from a Java-style properties file.
 */
@RunWith(AndroidJUnit4::class)
public class AirshipConfigOptionsTest {

    private val application: Context = ApplicationProvider.getApplicationContext()

    /**
     * This test verifies the applyProperties method can parse different types
     */
    @Test
    public fun testLoadFromProperties() {
        val production = AirshipConfigOptions.Builder()
            .applyProperties(application, getProperties(TEST_PROPERTIES_FILE))
            .build()

        val development = AirshipConfigOptions.Builder()
            .applyProperties(application, getProperties(TEST_PROPERTIES_FILE))
            .setInProduction(false)
            .build()

        Assert.assertEquals("devAppKey", development.appKey)
        Assert.assertEquals("devAppSecret", development.appSecret)

        Assert.assertEquals("prodAppKey", production.appKey)
        Assert.assertEquals("prodAppSecret", production.appSecret)
        Assert.assertEquals("https://test.host.url.com/", production.deviceUrl)
        Assert.assertEquals("https://test.analytics.url.com/", production.analyticsUrl)
        Assert.assertEquals(listOf("GCM_TRANSPORT"), production.allowedTransports)
        Assert.assertEquals(
            "https://first.urlAllowList.url.com/", production.urlAllowList[0]
        )
        Assert.assertEquals(
            "https://second.urlAllowList.url.com/", production.urlAllowList[1]
        )
        Assert.assertTrue(production.inProduction)
        Assert.assertFalse(production.analyticsEnabled)
        Assert.assertEquals(2700, production.backgroundReportingIntervalMS)
        Assert.assertFalse(production.autoLaunchApplication)
        Assert.assertTrue(production.channelCreationDelayEnabled)
        Assert.assertFalse(production.channelCaptureEnabled)
        Assert.assertEquals(AirshipConfigOptions.LogLevel.VERBOSE, production.logLevel)
        Assert.assertEquals(
            R.drawable.ua_default_ic_notification.toLong(), production.notificationIcon.toLong()
        )
        Assert.assertEquals(
            Color.parseColor("#ff0000").toLong(), production.notificationAccentColor.toLong()
        )
        Assert.assertEquals("https://test.wallet.url.com/", production.walletUrl)
        Assert.assertEquals("test_channel", production.notificationChannel)
        Assert.assertEquals(
            "https://play.google.com/store/apps/topic?id=editors_choice",
            production.appStoreUri.toString()
        )
        Assert.assertTrue(production.extendedBroadcastsEnabled)
        Assert.assertTrue(production.requireInitialRemoteConfigEnabled)
        Assert.assertEquals("config://", production.initialConfigUrl)
        Assert.assertEquals(PrivacyManager.Feature.NONE, production.enabledFeatures)
    }

    /**
     * This test loads invalid values and verify the property value is set to default
     */
    @Test
    public fun testInvalidOptions() {
        val development = AirshipConfigOptions.Builder()
            .applyProperties(application, getProperties(INVALID_PROPERTIES_FILE))
            .setInProduction(false)
            .build()

        Assert.assertEquals(AirshipConfigOptions.LogLevel.DEBUG, development.logLevel)

        val production = AirshipConfigOptions.Builder()
            .applyProperties(application, getProperties(INVALID_PROPERTIES_FILE))
            .setInProduction(true)
            .build()

        Assert.assertEquals(AirshipConfigOptions.LogLevel.ERROR, production.logLevel)
        Assert.assertEquals(0, production.notificationAccentColor.toLong())
        Assert.assertEquals(0, production.notificationIcon.toLong())
    }

    @Test(expected = ConfigException::class)
    public fun testThrowsInvalidConfigFile() {
        AirshipConfigOptions.Builder()
            .tryApplyProperties(application, UUID.randomUUID().toString())
            .build()
    }

    @Test
    public fun testDefaultConfig() {
        val defaultConfig = AirshipConfigOptions.Builder()
            .setDevelopmentAppKey("appKey")
            .setDevelopmentAppSecret("appSecret")
            .build()

        Assert.assertEquals("appKey", defaultConfig.appKey)
        Assert.assertEquals("appSecret", defaultConfig.appSecret)
        Assert.assertEquals("https://device-api.urbanairship.com/", defaultConfig.deviceUrl)
        Assert.assertEquals("https://combine.urbanairship.com/", defaultConfig.analyticsUrl)
        Assert.assertEquals("https://remote-data.urbanairship.com/", defaultConfig.remoteDataUrl)
        Assert.assertEquals("https://wallet-api.urbanairship.com", defaultConfig.walletUrl)

        Assert.assertNull(defaultConfig.notificationChannel)
        Assert.assertNull(defaultConfig.appStoreUri)

        Assert.assertNull(defaultConfig.customPushProvider)

        Assert.assertTrue(defaultConfig.urlAllowList.isEmpty())
        Assert.assertTrue(defaultConfig.urlAllowListScopeJavaScriptInterface.isEmpty())
        Assert.assertTrue(defaultConfig.urlAllowListScopeOpenUrl.isEmpty())

        Assert.assertEquals(24.hours.inWholeMilliseconds, defaultConfig.backgroundReportingIntervalMS)

        Assert.assertEquals(0, defaultConfig.notificationIcon.toLong())
        Assert.assertEquals(0, defaultConfig.notificationLargeIcon.toLong())
        Assert.assertEquals(
            NotificationCompat.COLOR_DEFAULT.toLong(),
            defaultConfig.notificationAccentColor.toLong()
        )

        Assert.assertTrue(defaultConfig.autoLaunchApplication)
        Assert.assertTrue(defaultConfig.channelCaptureEnabled)
        Assert.assertTrue(defaultConfig.analyticsEnabled)
        Assert.assertFalse(defaultConfig.inProduction)
        Assert.assertFalse(defaultConfig.channelCreationDelayEnabled)
        @Suppress("DEPRECATION")
        Assert.assertFalse(defaultConfig.dataCollectionOptInEnabled)
        Assert.assertFalse(defaultConfig.extendedBroadcastsEnabled)
        Assert.assertTrue(defaultConfig.requireInitialRemoteConfigEnabled)
        Assert.assertEquals(PrivacyManager.Feature.ALL, defaultConfig.enabledFeatures)
    }

    @Test
    public fun testValidate() {
        val valid = AirshipConfigOptions.newBuilder()
            .setAppKey("-----1abc123_-90000000")
            .setAppSecret("0A00000000000000000000")
            .build()

        valid.validate()
    }

    @Test(expected = IllegalArgumentException::class)
    public fun testValidateChecksKey() {
        val valid = AirshipConfigOptions.newBuilder()
            .setAppKey("0A00000000000") // not enough characters
            .build()

        valid.validate()
    }

    @Test(expected = IllegalArgumentException::class)
    public fun testValidateChecksSecret() {
        val valid = AirshipConfigOptions.newBuilder()
            .setAppSecret("0A00000000000000000000EXTRA") // too many characters
            .build()

        valid.validate()
    }

    @Test
    public fun testEuCloudSite() {
        val configOptions = AirshipConfigOptions.newBuilder()
            .setSite(AirshipConfigOptions.Site.SITE_EU)
            .build()

        Assert.assertEquals(configOptions.analyticsUrl, "https://combine.asnapieu.com/")
        Assert.assertEquals(configOptions.deviceUrl, "https://device-api.asnapieu.com/")
        Assert.assertEquals(configOptions.remoteDataUrl, "https://remote-data.asnapieu.com/")
        Assert.assertEquals(configOptions.walletUrl, "https://wallet-api.asnapieu.com")
    }

    @Test
    public fun testUsCloudSite() {
        val configOptions = AirshipConfigOptions.newBuilder()
            .setSite(AirshipConfigOptions.Site.SITE_US)
            .build()

        Assert.assertEquals(configOptions.analyticsUrl, "https://combine.urbanairship.com/")
        Assert.assertEquals(configOptions.deviceUrl, "https://device-api.urbanairship.com/")
        Assert.assertEquals(configOptions.remoteDataUrl, "https://remote-data.urbanairship.com/")
        Assert.assertEquals(configOptions.walletUrl, "https://wallet-api.urbanairship.com")
    }

    @Test
    public fun testUrlOverrides() {
        val configOptions =AirshipConfigOptions.newBuilder()
            .setSite(AirshipConfigOptions.Site.SITE_EU)
            .setAnalyticsUrl("some-analytics-url")
            .setDeviceUrl("some-device-url")
            .setRemoteDataUrl("some-remote-data-url")
            .setWalletUrl("some-wallet-url")
            .build()

        Assert.assertEquals(configOptions.analyticsUrl, "some-analytics-url")
        Assert.assertEquals(configOptions.deviceUrl, "some-device-url")
        Assert.assertEquals(configOptions.remoteDataUrl, "some-remote-data-url")
        Assert.assertEquals(configOptions.walletUrl, "some-wallet-url")
    }

    @Test
    public fun testEnabledFeaturesMigration() {
        @Suppress("DEPRECATION")  // for setDataCollectionOptInEnabled
        var configOptions = AirshipConfigOptions.newBuilder()
            .setEnabledFeatures(PrivacyManager.Feature.PUSH)
            .setDataCollectionOptInEnabled(true)
            .build()

        Assert.assertEquals(PrivacyManager.Feature.PUSH, configOptions.enabledFeatures)

        @Suppress("DEPRECATION")  // for setDataCollectionOptInEnabled
        configOptions = AirshipConfigOptions.newBuilder()
            .setDataCollectionOptInEnabled(true)
            .build()

        Assert.assertEquals(PrivacyManager.Feature.NONE, configOptions.enabledFeatures)

        configOptions = AirshipConfigOptions.newBuilder().build()

        Assert.assertEquals(PrivacyManager.Feature.ALL, configOptions.enabledFeatures)
    }

    public fun getProperties(file: String): Properties {
        val classLoader = requireNotNull(javaClass.classLoader) { "ClassLoader is null!" }
        var stream: InputStream? = null
        try {
            stream = classLoader.getResourceAsStream(file)
            val properties = Properties()
            properties.load(stream)
            return properties
        } finally {
            stream?.close()
        }
    }

    private companion object {

        private const val TEST_PROPERTIES_FILE = "valid.properties"
        private const val INVALID_PROPERTIES_FILE = "invalid.properties"
    }
}
