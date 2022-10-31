package com.urbanairship.config;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestApplication;
import com.urbanairship.remoteconfig.RemoteAirshipConfig;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class RemoteAirshipUrlConfigProviderTest extends BaseTestCase {
    private PreferenceDataStore dataStore;

    @Before
    public void setup() {
        this.dataStore = TestApplication.getApplication().preferenceDataStore;
    }

    @Test
    public void testDefaultUrls() {
        AirshipConfigOptions configOptions = AirshipConfigOptions.newBuilder()
                                                                 .setRequireInitialRemoteConfigEnabled(false)
                                                                 .build();

        RemoteAirshipUrlConfigProvider provider = new RemoteAirshipUrlConfigProvider(configOptions, dataStore);

        AirshipUrlConfig urlConfig = provider.getConfig();

        assertEquals(configOptions.deviceUrl, urlConfig.deviceUrl().build().toString());
        assertEquals(configOptions.remoteDataUrl, urlConfig.remoteDataUrl().build().toString());
        assertEquals(configOptions.analyticsUrl, urlConfig.analyticsUrl().build().toString());
        assertEquals(configOptions.walletUrl, urlConfig.walletUrl().build().toString());
        assertTrue(configOptions.chatUrl.isEmpty());
        assertTrue(configOptions.chatSocketUrl.isEmpty());
    }

    @Test
    public void tesDefaultUrlsDisableFallback() {
        AirshipConfigOptions configOptions = AirshipConfigOptions.newBuilder().build();
        RemoteAirshipUrlConfigProvider provider = new RemoteAirshipUrlConfigProvider(configOptions, dataStore);
        provider.disableFallbackUrls();

        AirshipUrlConfig urlConfig = provider.getConfig();

        assertNull(urlConfig.deviceUrl().build());
        assertNull(urlConfig.analyticsUrl().build());
        assertNull(urlConfig.walletUrl().build());

        // Remote-data should fallback so we can retrieve the other URLs
        assertEquals(configOptions.remoteDataUrl, urlConfig.remoteDataUrl().build().toString());
    }

    @Test
    public void testUpdatingUrls() {
        RemoteAirshipConfig remoteConfig = new RemoteAirshipConfig("http://remote",
                "http://device", "http://wallet", "http://analytics",
                "http://chat", "wss://chat");
        AirshipConfigOptions configOptions = AirshipConfigOptions.newBuilder().build();

        RemoteAirshipUrlConfigProvider provider = new RemoteAirshipUrlConfigProvider(configOptions, dataStore);

        AirshipUrlConfig.Listener listener = mock(AirshipUrlConfig.Listener.class);
        provider.addUrlConfigListener(listener);

        provider.onRemoteConfigUpdated(remoteConfig);

        verify(listener).onUrlConfigUpdated();

        AirshipUrlConfig urlConfig = provider.getConfig();

        assertEquals("http://device", urlConfig.deviceUrl().build().toString());
        assertEquals("http://remote", urlConfig.remoteDataUrl().build().toString());
        assertEquals("http://analytics", urlConfig.analyticsUrl().build().toString());
        assertEquals("http://wallet", urlConfig.walletUrl().build().toString());
        assertEquals("http://chat", urlConfig.chatUrl().build().toString());
        assertEquals("wss://chat", urlConfig.chatSocketUrl().build().toString());

    }

    @Test
    public void testUrlConfigListenerIgnoresUnchangedUpdates() {
        RemoteAirshipConfig remoteConfig = new RemoteAirshipConfig("http://remote",
                "http://device", "http://wallet", "http://analytics",
                "http://chat", "wss://chat");
        AirshipConfigOptions configOptions = AirshipConfigOptions.newBuilder().build();

        RemoteAirshipUrlConfigProvider provider = new RemoteAirshipUrlConfigProvider(configOptions, dataStore);
        // Update before attaching the listener.
        provider.onRemoteConfigUpdated(remoteConfig);

        AirshipUrlConfig.Listener listener = mock(AirshipUrlConfig.Listener.class);
        provider.addUrlConfigListener(listener);

        // Update with the same remote config now that the listener is attached.
        provider.onRemoteConfigUpdated(remoteConfig);

        verify(listener, never()).onUrlConfigUpdated();
    }

    @Test
    public void testEmptyRemoteConfigFallbacksToOptions() {
        AirshipConfigOptions configOptions = AirshipConfigOptions.newBuilder()
                                                                 .setRequireInitialRemoteConfigEnabled(false)
                                                                 .build();
        RemoteAirshipUrlConfigProvider provider = new RemoteAirshipUrlConfigProvider(configOptions, dataStore);

        provider.onRemoteConfigUpdated(new RemoteAirshipConfig(null, null, null, null, null, null));
        AirshipUrlConfig urlConfig = provider.getConfig();

        assertEquals(configOptions.deviceUrl, urlConfig.deviceUrl().build().toString());
        assertEquals(configOptions.remoteDataUrl, urlConfig.remoteDataUrl().build().toString());
        assertEquals(configOptions.analyticsUrl, urlConfig.analyticsUrl().build().toString());
        assertEquals(configOptions.walletUrl, urlConfig.walletUrl().build().toString());
    }

    @Test
    public void testRequireInitialRemoteConfig() {
        AirshipConfigOptions configOptions = AirshipConfigOptions.newBuilder()
                                                                 .setRequireInitialRemoteConfigEnabled(true)
                                                                 .build();

        RemoteAirshipUrlConfigProvider provider = new RemoteAirshipUrlConfigProvider(configOptions, dataStore);

        assertNull(provider.getConfig().deviceUrl().build());
        assertNull(provider.getConfig().analyticsUrl().build());
        assertNull(provider.getConfig().walletUrl().build());
        assertNull(provider.getConfig().chatUrl().build());
        assertNull(provider.getConfig().chatSocketUrl().build());
        assertEquals(configOptions.remoteDataUrl, provider.getConfig().remoteDataUrl().build().toString());

        RemoteAirshipConfig remoteConfig = new RemoteAirshipConfig("http://remote",
                "http://device", "http://wallet", "http://analytics",
                "http://chat", "wss://chat");
        provider.onRemoteConfigUpdated(remoteConfig);

        assertEquals("http://device", provider.getConfig().deviceUrl().build().toString());
        assertEquals("http://remote", provider.getConfig().remoteDataUrl().build().toString());
        assertEquals("http://analytics", provider.getConfig().analyticsUrl().build().toString());
        assertEquals("http://wallet", provider.getConfig().walletUrl().build().toString());
        assertEquals("http://chat", provider.getConfig().chatUrl().build().toString());
        assertEquals("wss://chat", provider.getConfig().chatSocketUrl().build().toString());
    }

    @Test
    public void testInitialConfigUrl() {
        AirshipConfigOptions configOptions = AirshipConfigOptions.newBuilder()
                                                                 .setInitialConfigUrl("custom://")
                                                                 .build();

        RemoteAirshipUrlConfigProvider provider = new RemoteAirshipUrlConfigProvider(configOptions, dataStore);
        assertEquals("custom://", provider.getConfig().remoteDataUrl().build().toString());
    }

    @Test
    public void testCacheRemoteAirshipConfig() {
        RemoteAirshipConfig remoteConfig = new RemoteAirshipConfig("http://remote",
                "http://device", "http://wallet", "http://analytics",
                "http://chat", "wss://chat");
        AirshipConfigOptions configOptions = AirshipConfigOptions.newBuilder().build();

        RemoteAirshipUrlConfigProvider provider = new RemoteAirshipUrlConfigProvider(configOptions, dataStore);
        provider.onRemoteConfigUpdated(remoteConfig);

        // Recreate it
        provider = new RemoteAirshipUrlConfigProvider(configOptions, dataStore);
        AirshipUrlConfig urlConfig = provider.getConfig();

        assertEquals("http://device", urlConfig.deviceUrl().build().toString());
        assertEquals("http://remote", urlConfig.remoteDataUrl().build().toString());
        assertEquals("http://analytics", urlConfig.analyticsUrl().build().toString());
        assertEquals("http://wallet", urlConfig.walletUrl().build().toString());
        assertEquals("http://chat", provider.getConfig().chatUrl().build().toString());
        assertEquals("wss://chat", provider.getConfig().chatSocketUrl().build().toString());
    }

}
