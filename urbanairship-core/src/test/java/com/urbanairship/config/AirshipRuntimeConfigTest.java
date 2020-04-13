package com.urbanairship.config;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.UAirship;

import org.junit.Before;
import org.junit.Test;

import androidx.annotation.NonNull;

import static org.junit.Assert.assertEquals;

public class AirshipRuntimeConfigTest extends BaseTestCase {

    private AirshipUrlConfig urlConfig;
    private AirshipConfigOptions configOptions;
    private AirshipUrlConfigProvider configProvider;

    @Before
    public void setup() {
        urlConfig = AirshipUrlConfig.newBuilder().build();
        configProvider = new AirshipUrlConfigProvider() {
            @NonNull
            @Override
            public AirshipUrlConfig getConfig() {
                return urlConfig;
            }
        };

        configOptions = AirshipConfigOptions.newBuilder().build();
    }

    @Test
    public void testAndroidPlatform() {
        AirshipRuntimeConfig runtimeConfig = new AirshipRuntimeConfig(configProvider, configOptions, UAirship.ANDROID_PLATFORM);
        assertEquals(UAirship.ANDROID_PLATFORM, runtimeConfig.getPlatform());
    }

    @Test
    public void testAmazonPlatform() {
        AirshipRuntimeConfig runtimeConfig = new AirshipRuntimeConfig(configProvider, configOptions, UAirship.AMAZON_PLATFORM);
        assertEquals(UAirship.AMAZON_PLATFORM, runtimeConfig.getPlatform());
    }

    @Test
    public void testGetUrlConfig() {
        AirshipRuntimeConfig runtimeConfig = new AirshipRuntimeConfig(configProvider, configOptions, UAirship.AMAZON_PLATFORM);
        assertEquals(urlConfig, runtimeConfig.getUrlConfig());
    }

    @Test
    public void testGetConfigOptions() {
        AirshipRuntimeConfig runtimeConfig = new AirshipRuntimeConfig(configProvider, configOptions, UAirship.AMAZON_PLATFORM);
        assertEquals(configOptions, runtimeConfig.getConfigOptions());
    }

}
