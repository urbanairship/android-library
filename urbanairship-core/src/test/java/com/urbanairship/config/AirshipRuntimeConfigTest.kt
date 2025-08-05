package com.urbanairship.config

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestRequestSession
import com.urbanairship.UAirship
import com.urbanairship.config.AirshipRuntimeConfig.ConfigChangeListener
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AirshipRuntimeConfigTest {

    private val dataStore: PreferenceDataStore = PreferenceDataStore.inMemoryStore(
        ApplicationProvider.getApplicationContext()
    )

    private var configOptions = AirshipConfigOptions.newBuilder().build()
    private var platform = UAirship.Platform.UNKNOWN
    private var runtimeConfig = AirshipRuntimeConfig(
        configOptionsProvider = { configOptions },
        requestSession = TestRequestSession(),
        dataStore = dataStore,
        platformProvider = { platform }
    )

    @Test
    public fun testAndroidPlatform() {
        platform = UAirship.Platform.ANDROID
        Assert.assertEquals(UAirship.Platform.ANDROID, runtimeConfig.platform)
    }

    @Test
    public fun testAmazonPlatform() {
        platform = UAirship.Platform.AMAZON
        Assert.assertEquals(UAirship.Platform.AMAZON, runtimeConfig.platform)
    }

    @Test
    public fun testDefaultUrlsRequireInitialConfig() {
        configOptions = AirshipConfigOptions.newBuilder()
            .setRequireInitialRemoteConfigEnabled(true)
            .build()

        runtimeConfig = AirshipRuntimeConfig({ configOptions }, TestRequestSession(), dataStore, { platform })

        Assert.assertNull(runtimeConfig.analyticsUrl.baseUrl)
        Assert.assertNull(runtimeConfig.deviceUrl.baseUrl)
        Assert.assertNull(runtimeConfig.walletUrl.baseUrl)
        Assert.assertNull(runtimeConfig.meteredUsageUrl.baseUrl)
        Assert.assertEquals(runtimeConfig.remoteDataUrl.baseUrl, configOptions.remoteDataUrl)
    }

    @Test
    public fun testDefaultUrlsInitialConfigUrl() {
        configOptions =
            AirshipConfigOptions.newBuilder().setInitialConfigUrl("https://neat.com").build()

        runtimeConfig = AirshipRuntimeConfig(
            { configOptions },
            TestRequestSession(),
            PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext()),
            { platform })

        Assert.assertNull(runtimeConfig.analyticsUrl.baseUrl)
        Assert.assertNull(runtimeConfig.deviceUrl.baseUrl)
        Assert.assertNull(runtimeConfig.walletUrl.baseUrl)
        Assert.assertNull(runtimeConfig.meteredUsageUrl.baseUrl)
        Assert.assertEquals(runtimeConfig.remoteDataUrl.baseUrl, configOptions.initialConfigUrl)
    }

    @Test
    public fun testDefaultUrlsFallback() {
        configOptions =
            AirshipConfigOptions.newBuilder().setRequireInitialRemoteConfigEnabled(false).build()

        runtimeConfig = AirshipRuntimeConfig(
            { configOptions },
            TestRequestSession(),
            PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext()),
            { platform })

        Assert.assertEquals(runtimeConfig.analyticsUrl.baseUrl, configOptions.analyticsUrl)
        Assert.assertEquals(runtimeConfig.deviceUrl.baseUrl, configOptions.deviceUrl)
        Assert.assertEquals(runtimeConfig.walletUrl.baseUrl, configOptions.walletUrl)
        Assert.assertEquals(runtimeConfig.remoteDataUrl.baseUrl, configOptions.remoteDataUrl)
        Assert.assertNull(runtimeConfig.meteredUsageUrl.baseUrl)
    }

    @Test
    public fun testUrlsRemoteConfigOverride() {
        val remoteAirshipConfig = RemoteAirshipConfig(
            "https://remote-data",
            "https://device",
            "https://wallet",
            "https://analytics",
            "https://metered-usage"
        )
        runtimeConfig.updateRemoteConfig(RemoteConfig(remoteAirshipConfig))

        Assert.assertEquals(runtimeConfig.analyticsUrl.baseUrl, remoteAirshipConfig.analyticsUrl)
        Assert.assertEquals(runtimeConfig.deviceUrl.baseUrl, remoteAirshipConfig.deviceApiUrl)
        Assert.assertEquals(runtimeConfig.walletUrl.baseUrl, remoteAirshipConfig.walletUrl)
        Assert.assertEquals(runtimeConfig.remoteDataUrl.baseUrl, remoteAirshipConfig.remoteDataUrl)
        Assert.assertEquals(
            runtimeConfig.meteredUsageUrl.baseUrl, remoteAirshipConfig.meteredUsageUrl
        )
    }

    @Test
    public fun testGetConfigOptions() {
        Assert.assertEquals(configOptions, runtimeConfig.configOptions)
    }

    @Test
    public fun testUpdateRemoteConfig() {
        val listener: ConfigChangeListener = mockk(relaxed = true)
        runtimeConfig.addConfigListener(listener)

        val remoteAirshipConfig = RemoteAirshipConfig(
            "https://remote-data",
            "https://device",
            "https://wallet",
            "https://analytics",
            "https://metered-usage"
        )

        val remoteConfig = RemoteConfig(remoteAirshipConfig)
        runtimeConfig.updateRemoteConfig(remoteConfig)

        Assert.assertEquals(remoteConfig, runtimeConfig.remoteConfig)
        verify { listener.onConfigUpdated() }
    }

    @Test
    public fun testDefaultRemoteConfig() {
        Assert.assertEquals(RemoteConfig(), runtimeConfig.remoteConfig)
    }

    @Test
    public fun testRemoteConfigPersists() {
        val remoteAirshipConfig = RemoteAirshipConfig(
            "https://remote-data",
            "https://device",
            "https://wallet",
            "https://analytics",
            "https://metered-usage"
        )

        val remoteConfig = RemoteConfig(remoteAirshipConfig)

        runtimeConfig.updateRemoteConfig(remoteConfig)
        Assert.assertEquals(remoteConfig, runtimeConfig.remoteConfig)

        runtimeConfig =
            AirshipRuntimeConfig({ configOptions }, TestRequestSession(), dataStore, { platform })

        Assert.assertEquals(remoteConfig, runtimeConfig.remoteConfig)
    }

    @Test
    public fun testEmptyURLs() {
        configOptions =
            AirshipConfigOptions.newBuilder().setRequireInitialRemoteConfigEnabled(false).build()

        runtimeConfig =
            AirshipRuntimeConfig({ configOptions }, TestRequestSession(), dataStore, { platform })

        val remoteAirshipConfig = RemoteAirshipConfig(
            "", "", "", "", ""
        )

        val remoteConfig = RemoteConfig(remoteAirshipConfig)
        runtimeConfig.updateRemoteConfig(remoteConfig)

        Assert.assertEquals(runtimeConfig.analyticsUrl.baseUrl, configOptions.analyticsUrl)
        Assert.assertEquals(runtimeConfig.deviceUrl.baseUrl, configOptions.deviceUrl)
        Assert.assertEquals(runtimeConfig.walletUrl.baseUrl, configOptions.walletUrl)
        Assert.assertEquals(runtimeConfig.remoteDataUrl.baseUrl, configOptions.remoteDataUrl)
        Assert.assertNull(runtimeConfig.meteredUsageUrl.baseUrl)
    }
}
