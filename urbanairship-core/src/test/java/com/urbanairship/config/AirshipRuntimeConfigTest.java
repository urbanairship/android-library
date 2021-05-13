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
    private int platform;
    private AirshipRuntimeConfig runtimeConfig;

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
        runtimeConfig = new  AirshipRuntimeConfig(new PlatformProvider() {
            @Override
            public int getPlatform() {
                return platform;
            }
        }, configOptions, configProvider);
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
    public void testGetUrlConfig() {
        assertEquals(urlConfig, runtimeConfig.getUrlConfig());
    }

    @Test
    public void testGetConfigOptions() {
        assertEquals(configOptions, runtimeConfig.getConfigOptions());
    }

}
