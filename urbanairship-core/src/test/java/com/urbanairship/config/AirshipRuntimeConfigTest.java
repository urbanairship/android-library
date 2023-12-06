package com.urbanairship.config;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestRequestSession;
import com.urbanairship.UAirship;
import com.urbanairship.remoteconfig.RemoteAirshipConfig;
import com.urbanairship.remoteconfig.RemoteConfig;

import org.junit.Before;
import org.junit.Test;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class AirshipRuntimeConfigTest extends BaseTestCase {

    private PreferenceDataStore dataStore = PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext());

    private AirshipConfigOptions configOptions = AirshipConfigOptions.newBuilder().build();
    private int platform;
    private AirshipRuntimeConfig  runtimeConfig = new  AirshipRuntimeConfig(
            () -> configOptions,
            new TestRequestSession(),
            dataStore,
            () -> platform
    );

    @Before
    public void setup() {

    }

    @Test
    public void testAndroidPlatform() {
        platform = UAirship.ANDROID_PLATFORM;
        assertEquals(UAirship.ANDROID_PLATFORM, runtimeConfig.getPlatform());
    }

    @Test
    public void testAmazonPlatform() {
        platform = UAirship.AMAZON_PLATFORM;
        assertEquals(UAirship.AMAZON_PLATFORM, runtimeConfig.getPlatform());
    }

    @Test
    public void testDefaultUrlsRequireInitialConfig() {
        configOptions = AirshipConfigOptions.newBuilder()
                                            .setRequireInitialRemoteConfigEnabled(true)
                                            .build();

        runtimeConfig = new  AirshipRuntimeConfig(
                () -> configOptions,
                new TestRequestSession(),
                dataStore,
                () -> platform
        );

        assertNull(runtimeConfig.getAnalyticsUrl().baseUrl);
        assertNull(runtimeConfig.getDeviceUrl().baseUrl);
        assertNull(runtimeConfig.getWalletUrl().baseUrl);
        assertNull(runtimeConfig.getMeteredUsageUrl().baseUrl);
        assertEquals(runtimeConfig.getRemoteDataUrl().baseUrl, configOptions.remoteDataUrl);
    }

    @Test
    public void testDefaultUrlsInitialConfigUrl() {
        configOptions = AirshipConfigOptions.newBuilder()
                                            .setInitialConfigUrl("https://neat.com")
                                            .build();

        runtimeConfig = new  AirshipRuntimeConfig(
                () -> configOptions,
                new TestRequestSession(),
                PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext()),
                () -> platform
        );

        assertNull(runtimeConfig.getAnalyticsUrl().baseUrl);
        assertNull(runtimeConfig.getDeviceUrl().baseUrl);
        assertNull(runtimeConfig.getWalletUrl().baseUrl);
        assertNull(runtimeConfig.getMeteredUsageUrl().baseUrl);
        assertEquals(runtimeConfig.getRemoteDataUrl().baseUrl, configOptions.initialConfigUrl);
    }

    @Test
    public void testDefaultUrlsFallback() {
        configOptions = AirshipConfigOptions.newBuilder()
                                            .setRequireInitialRemoteConfigEnabled(false)
                                            .build();

        runtimeConfig = new  AirshipRuntimeConfig(
                () -> configOptions,
                new TestRequestSession(),
                PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext()),
                () -> platform
        );

        assertEquals(runtimeConfig.getAnalyticsUrl().baseUrl, configOptions.analyticsUrl);
        assertEquals(runtimeConfig.getDeviceUrl().baseUrl, configOptions.deviceUrl);
        assertEquals(runtimeConfig.getWalletUrl().baseUrl, configOptions.walletUrl);
        assertEquals(runtimeConfig.getRemoteDataUrl().baseUrl, configOptions.remoteDataUrl);
        assertNull(runtimeConfig.getMeteredUsageUrl().baseUrl);
    }

    @Test
    public void testUrlsRemoteConfigOverride() {
        RemoteAirshipConfig remoteAirshipConfig = new RemoteAirshipConfig(
                "https://remote-data",
                "https://device",
                "https://wallet",
                "https://analytics",
                "https://metered-usage"
        );
        runtimeConfig.updateRemoteConfig(new RemoteConfig(remoteAirshipConfig));

        assertEquals(runtimeConfig.getAnalyticsUrl().baseUrl, remoteAirshipConfig.getAnalyticsUrl());
        assertEquals(runtimeConfig.getDeviceUrl().baseUrl, remoteAirshipConfig.getDeviceApiUrl());
        assertEquals(runtimeConfig.getWalletUrl().baseUrl, remoteAirshipConfig.getWalletUrl());
        assertEquals(runtimeConfig.getRemoteDataUrl().baseUrl, remoteAirshipConfig.getRemoteDataUrl());
        assertEquals(runtimeConfig.getMeteredUsageUrl().baseUrl, remoteAirshipConfig.getMeteredUsageUrl());
    }

    @Test
    public void testGetConfigOptions() {
        assertEquals(configOptions, runtimeConfig.getConfigOptions());
    }

    @Test
    public void testUpdateRemoteConfig() {
        AirshipRuntimeConfig.ConfigChangeListener listener = mock(AirshipRuntimeConfig.ConfigChangeListener.class);
        runtimeConfig.addConfigListener(listener);

        RemoteAirshipConfig remoteAirshipConfig = new RemoteAirshipConfig(
                "https://remote-data",
                "https://device",
                "https://wallet",
                "https://analytics",
                "https://metered-usage"
        );

        RemoteConfig remoteConfig = new RemoteConfig(remoteAirshipConfig);
        runtimeConfig.updateRemoteConfig(remoteConfig);

        assertEquals(remoteConfig, runtimeConfig.getRemoteConfig());
        verify(listener).onConfigUpdated();
    }

    @Test
    public void testDefaultRemoteConfig() {
        assertEquals(new RemoteConfig(), runtimeConfig.getRemoteConfig());
    }

    @Test
    public void testRemoteConfigPersists() {
        RemoteAirshipConfig remoteAirshipConfig = new RemoteAirshipConfig(
                "https://remote-data",
                "https://device",
                "https://wallet",
                "https://analytics",
                "https://metered-usage"
        );

        RemoteConfig remoteConfig = new RemoteConfig(remoteAirshipConfig);

        runtimeConfig.updateRemoteConfig(remoteConfig);
        assertEquals(remoteConfig, runtimeConfig.getRemoteConfig());

        runtimeConfig = new  AirshipRuntimeConfig(
                () -> configOptions,
                new TestRequestSession(),
                dataStore,
                () -> platform
        );

        assertEquals(remoteConfig, runtimeConfig.getRemoteConfig());
    }

    @Test
    public void testEmptyURLs() {
        configOptions = AirshipConfigOptions.newBuilder()
                                            .setRequireInitialRemoteConfigEnabled(false)
                                            .build();

        runtimeConfig = new  AirshipRuntimeConfig(
                () -> configOptions,
                new TestRequestSession(),
                dataStore,
                () -> platform
        );

        RemoteAirshipConfig remoteAirshipConfig = new RemoteAirshipConfig(
                "",
                "",
                "",
                "",
                ""
        );

        RemoteConfig remoteConfig = new RemoteConfig(remoteAirshipConfig);
        runtimeConfig.updateRemoteConfig(remoteConfig);

        assertEquals(runtimeConfig.getAnalyticsUrl().baseUrl, configOptions.analyticsUrl);
        assertEquals(runtimeConfig.getDeviceUrl().baseUrl, configOptions.deviceUrl);
        assertEquals(runtimeConfig.getWalletUrl().baseUrl, configOptions.walletUrl);
        assertEquals(runtimeConfig.getRemoteDataUrl().baseUrl, configOptions.remoteDataUrl);
        assertNull(runtimeConfig.getMeteredUsageUrl().baseUrl);
    }
}
